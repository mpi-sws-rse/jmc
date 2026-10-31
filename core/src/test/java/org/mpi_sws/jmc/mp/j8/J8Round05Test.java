package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.programs.j4.A3Lock;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * J8 tester round 05 (plan/jmc-must/log/dev/J8/round-05.prompt.md), item 2: a deterministic test of the
 * race the lead found after D-1 — a worker publishes item j and dies only after the coordinator has
 * assigned it item j'. No production seam delays a worker's exit, so the coordinator is driven through
 * its public API ({@link SplitCoordinator#run}) with a test-side worker command: {@link
 * J8DelayedExitWorker} wraps the real worker ({@code MpLauncher ... --worker}) and keeps the watched
 * process alive 1500 ms after an injected death ({@code die-after:4:0}).
 *
 * <p>Derived (round-01 segments of A3Lock:fifo,3,1, K = 1: R 1, N 5, items 4..0 = 2 4 3 10 10;
 * sequential export md5 {@code ddc264d8…}): with W = 1 the worker explores item 4 (attempt 0), publishes
 * it and dies; within the 1500 ms the coordinator commits item 4 and assigns item 3 (attempt 0) to the
 * same, still "alive", slot; the death is then observed with 3.0 outstanding and unconsumed. Expected:
 * 3.0 is voided ({@code consumed/3.0.json}), item 3 is retried once as 3.1 on the restarted worker, which
 * does not execute 3.0; the run completes and the export equals the sequential one.
 */
class J8Round05Test {

    static final String MD5 = "ddc264d82b01cdb97a8860dd701394b2";

    static SplitCoordinator.Result run(Path dir, int workers, String fault, long delayMillis) throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String cp = System.getProperty("java.class.path");
        String program = "org.mpi_sws.jmc.mp.programs.j4.A3Lock:fifo,3,1";
        A3Lock a3 = new A3Lock("fifo", "3", "1");
        return SplitCoordinator.run(new SplitCoordinator.Config(program + "|p2p", a3, a3.rolesInSpawnOrder(),
                a3.visibleRoles(), 1, workers, 300, dir, (d, id) -> {
                    List<String> cmd = new ArrayList<>(List.of(java, "-cp", cp, J8DelayedExitWorker.class.getName(),
                            Long.toString(delayMillis), java, "-Djmc.split.fault=" + fault, "-Xss4m", "-cp", cp,
                            "org.mpi_sws.jmc.mp.launch.MpLauncher", program, "must", "0", "1",
                            d.resolve("worker-report").toString(),
                            // an export path, as the launcher passes: the worker then summarises (roles)
                            d.resolve("worker-export.jsonl").toString(), "300", "--worker", d.toString(),
                            Integer.toString(id)));
                    return cmd;
                }));
    }

    static String exportMd5(SplitCoordinator.Result r) throws Exception {
        MpProgram mp = new A3Lock("fifo", "3", "1");
        StringBuilder b = new StringBuilder();
        b.append(SummaryExporter.header(mp.programName(), mp.size(), mp.visibleRoles(), mp.model(), "complete", 1L,
                r.ran())).append('\n');
        for (String l : r.summaryLines()) {
            b.append(l).append('\n');
        }
        return J8Launch.md5(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void race_publishThenDieAfterNextAssignment_nextItemRetriedOnce_runComplete() throws Exception {
        Path dir = J8Launch.DIR.resolve("race-" + System.currentTimeMillis());
        SplitCoordinator.Result r = run(dir, 1, "die-after:4:0", 1500);
        StringBuilder files = new StringBuilder();
        for (String sub : List.of("", "assigned", "consumed")) {
            Path p = sub.isEmpty() ? dir : dir.resolve(sub);
            if (Files.isDirectory(p)) {
                try (var s = Files.list(p)) {
                    files.append(sub.isEmpty() ? "." : sub).append(": ");
                    s.map(x -> x.getFileName().toString()).sorted().forEach(x -> files.append(x).append(' '));
                    files.append('\n');
                }
            }
        }
        System.out.println("J8R5 race: failure " + r.failure() + ", items " + r.itemIterations() + ", lost "
                + r.workersLost() + "\n" + files);
        assertNull(r.failure(), files.toString());
        assertEquals(30, r.distinctComplete());
        assertEquals(30, r.ran());
        assertEquals("{0=10, 1=10, 2=3, 3=4, 4=2}", r.itemIterations().toString());
        assertEquals(MD5, exportMd5(r), "export equal to sequential");
        // the race happened: 3.0 was assigned to the dying slot, voided, and retried as 3.1
        assertTrue(Files.exists(dir.resolve("assigned/3.0.json")), files.toString());
        assertTrue(Files.exists(dir.resolve("consumed/3.0.json")), files.toString());
        assertTrue(Files.exists(dir.resolve("assigned/3.1.json")), files.toString());
        String item3 = Files.readString(dir.resolve("item-3.json"));
        assertTrue(item3.replace(" ", "").contains("\"attempt\":1"), item3);
        // item 4 was not retried (died after its rename)
        assertFalse(Files.exists(dir.resolve("assigned/4.1.json")), files.toString());
        assertEquals(0, r.workersLost());
    }
}
