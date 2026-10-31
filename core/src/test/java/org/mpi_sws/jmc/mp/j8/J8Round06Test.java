package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * J8 tester round 06 (plan/jmc-must/log/dev/J8/round-06.prompt.md): regressions for Codex's step-1
 * round-01 findings M2, M3, m1, m2 (review/J8/round-03.verdict.md), driving {@link SplitCoordinator#run}
 * through its public API with test-side worker commands (no production change). M1 is reported as not
 * reachable deterministically from the test side (see the round-06 report).
 *
 * <p>Fixture: A3Lock:fifo,3,1 at K = 1 (round-01 derivation: R 1, N 5, items 4..0 = 2 4 3 10 10, 30
 * complete, sequential export md5 {@code ddc264d8…}).
 */
class J8Round06Test {

    static final String JAVA = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    static final String CP = System.getProperty("java.class.path");
    static final String A3 = "org.mpi_sws.jmc.mp.programs.j4.A3Lock:fifo,3,1";
    static final String MD5 = "ddc264d82b01cdb97a8860dd701394b2";

    /** The real worker's command line, as MpLauncher builds it, with a tag to find leftover processes. */
    static List<String> realWorker(Path d, int id, String program, String tag) {
        return new ArrayList<>(List.of(JAVA, "-Dj8.tag=" + tag, "-Xss4m", "-cp", CP,
                "org.mpi_sws.jmc.mp.launch.MpLauncher", program, "must", "0", "1", d.resolve("worker-report").toString(),
                d.resolve("worker-export.jsonl").toString(), "300", "--worker", d.toString(), Integer.toString(id)));
    }

    static SplitCoordinator.Result run(Path dir, String program, MpProgram mp, int workers, boolean reversed,
                                       BiFunction<Path, Integer, List<String>> cmd) throws Exception {
        return SplitCoordinator.run(new SplitCoordinator.Config(program + "|p2p", mp, mp.rolesInSpawnOrder(),
                mp.visibleRoles(), 1, workers, 300, dir, cmd, reversed));
    }

    static String exportMd5(MpProgram mp, SplitCoordinator.Result r) throws Exception {
        StringBuilder b = new StringBuilder();
        b.append(SummaryExporter.header(mp.programName(), mp.size(), mp.visibleRoles(), mp.model(), "complete", 1L,
                r.ran())).append('\n');
        for (String l : r.summaryLines()) {
            b.append(l).append('\n');
        }
        return J8Launch.md5(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Path dir(String name) {
        return J8Launch.DIR.resolve("r6-" + name + "-" + System.currentTimeMillis());
    }

    /** Processes still alive whose command line carries the tag. */
    static List<String> leftovers(String tag) {
        List<String> out = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(p -> p.info().commandLine().ifPresent(c -> {
            if (c.contains(tag) && p.isAlive()) {
                out.add(p.pid() + " " + c.substring(0, Math.min(120, c.length())));
            }
        }));
        return out;
    }

    static A3Lock a3() {
        return new A3Lock("fifo", "3", "1");
    }

    // ---------------------------------------------------------------- M2

    @Test
    void m2_partialStartupFailure_incomplete_startedWorkerTerminatedAndJoined() throws Exception {
        String tag = UUID.randomUUID().toString();
        Path d = dir("m2-startup");
        SplitCoordinator.Result r = run(d, A3, a3(), 2, false, (x, id) -> id == 0 ? realWorker(x, id, A3, tag)
                : List.of("/nonexistent/j8-no-such-binary-" + tag));
        System.out.println("J8R6 m2 startup: failure " + r.failure() + "; leftovers " + leftovers(tag));
        assertNotNull(r.failure());
        assertTrue(r.failure().startsWith("coordinator failure: java.io.IOException"), r.failure());
        assertTrue(Files.exists(d.resolve("worker-0.log")), "worker 0 was started");
        assertEquals(List.of(), leftovers(tag), "no worker left running");
    }

    @Test
    void m2_malformedResult_withAnotherWorkerAlive_incomplete_allTerminated() throws Exception {
        for (String mode : List.of("garbage", "emptyobject")) {
            String tag = UUID.randomUUID().toString();
            Path d = dir("m2-malformed-" + mode);
            SplitCoordinator.Result r = run(d, A3, a3(), 2, false, (x, id) -> id == 0
                    ? List.of(JAVA, "-Dj8.tag=" + tag, "-cp", CP, J8FakeWorker.class.getName(), x.toString(), "0", mode)
                    : realWorker(x, id, A3, tag));
            System.out.println("J8R6 m2 malformed " + mode + ": failure " + r.failure() + "; leftovers "
                    + leftovers(tag));
            assertNotNull(r.failure(), mode);
            assertTrue(r.failure().contains("item 4") || r.failure().contains("item-4"), mode + ": " + r.failure());
            assertEquals(List.of(), leftovers(tag), mode + ": no worker left running");
        }
    }

    // ---------------------------------------------------------------- M3

    @Test
    void m3_replayExhaustsBeforeTheBoundary_startupFailure_noRetry() throws Exception {
        // The worker replays A2Store:spec,1,2, whose first graph leaves the stack empty (round 01: S1 = 0),
        // so its replay ends below K = 1 without reaching the coordinator's boundary (R 1, N 5).
        String other = "org.mpi_sws.jmc.mp.programs.j4.A2Store:spec,1,2";
        String tag = UUID.randomUUID().toString();
        Path d = dir("m3");
        SplitCoordinator.Result r = run(d, A3, a3(), 1, false, (x, id) -> realWorker(x, id, other, tag));
        System.out.println("J8R6 m3: failure " + r.failure());
        assertNotNull(r.failure());
        assertTrue(r.failure().contains("worker-0.fail.json")
                && r.failure().contains("the replay ended before reaching the split boundary (R 1, N 5)"), r.failure());
        try (var s = Files.list(d.resolve("assigned"))) {
            assertTrue(s.noneMatch(p -> p.getFileName().toString().endsWith(".1.json")), "no retry");
        }
        assertEquals(List.of(), leftovers(tag));
    }

    // ---------------------------------------------------------------- m1

    @Test
    void m1_exportFailure_stopReasonError_withMergedCounts() throws Exception {
        Files.createDirectories(J8Launch.DIR);
        Path blocker = J8Launch.DIR.resolve("r6-not-a-directory-" + System.currentTimeMillis());
        Files.writeString(blocker, "a file, so the export's parent cannot be created");
        Path exportFile = blocker.resolve("x.jsonl");
        List<String> cmd = new ArrayList<>(List.of(JAVA, "-Xss4m", "-cp", CP, "org.mpi_sws.jmc.mp.launch.MpLauncher",
                A3, "must", "0", "1", J8Launch.DIR.resolve("r6-rep-" + System.currentTimeMillis()).toString(),
                exportFile.toString(), "300", "--split", "2", "--k", "1"));
        Path log = J8Launch.DIR.resolve("r6-exportfail-" + System.currentTimeMillis() + ".txt");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(p.waitFor(300, java.util.concurrent.TimeUnit.SECONDS));
        String text = Files.readString(log);
        Map<String, String> block = new TreeMap<>();
        boolean in = false;
        for (String l : text.split("\n")) {
            if (l.startsWith("--- measurements")) {
                in = true;
                continue;
            }
            int c = l.indexOf(':');
            if (in && c > 0 && l.substring(0, c).matches("[a-z_]+")) {
                block.put(l.substring(0, c), l.substring(c + 1).trim());
            }
        }
        System.out.println("J8R6 m1 export failure: exit " + p.exitValue() + " block " + block);
        assertEquals(1, p.exitValue(), text);
        assertTrue(text.contains("FAILED:") && text.contains("cannot write"), text);
        assertEquals("error", block.get("stop_reason"));
        assertEquals("30", block.get("graphs_complete"));
        assertEquals("0", block.get("graphs_blocked"));
        assertEquals("30", block.get("iterations"));
        assertEquals("12", block.get("summaries_distinct"));
        assertFalse(Files.exists(exportFile));
    }

    @Test
    void m1_exploreMs_excludesADelayedWorkerShutdown() throws Exception {
        long hold = 3000;
        Path d = dir("m1-hold");
        long t0 = System.nanoTime();
        SplitCoordinator.Result r = run(d, A3, a3(), 2, false, (x, id) -> {
            List<String> c = new ArrayList<>(List.of(JAVA, "-cp", CP, J8HoldWorker.class.getName(), Long.toString(hold)));
            c.addAll(realWorker(x, id, A3, "hold"));
            return c;
        });
        long wallMs = (System.nanoTime() - t0) / 1_000_000;
        long exploreMs = r.exploreNanos() / 1_000_000;
        System.out.println("J8R6 m1 hold: wall " + wallMs + " ms, explore " + exploreMs + " ms, failure " + r.failure());
        assertNull(r.failure());
        assertEquals(MD5, exportMd5(a3(), r));
        assertTrue(exploreMs > 0, "explore measured");
        // the run returns only after both workers have ended, i.e. at least `hold` after STOP
        assertTrue(wallMs >= exploreMs + hold - 500, "explore_ms ends at the last commit: " + exploreMs + " vs " + wallMs);
    }

    // ---------------------------------------------------------------- m2

    @Test
    void m2_reversedAssignment_exportIdenticalToSequential() throws Exception {
        String[][] rows = {
            {A3, "fifo", "3", "1", MD5},
            {"org.mpi_sws.jmc.mp.programs.j4.A3Lock:double-grant,3,1", "double-grant", "3", "1",
                "45dc7076677f571c6885284aff1697ac"},
            {"org.mpi_sws.jmc.mp.programs.j4.A3Lock:never-grant,3,1", "never-grant", "3", "1",
                "09c394ff3f21d16459ea20e9fc094d29"},
        };
        for (String[] row : rows) {
            A3Lock mp = new A3Lock(row[1], row[2], row[3]);
            for (int w : new int[] {1, 4}) {
                Path d = dir("m2-reversed-" + row[1] + "-W" + w);
                SplitCoordinator.Result r = run(d, row[0], mp, w, true, (x, id) -> realWorker(x, id, row[0], "rev"));
                assertNull(r.failure(), row[0]);
                assertEquals(row[4], exportMd5(mp, r), row[0] + " W=" + w + ": export identical to sequential");
                // assignment order: by creation time of assigned/<j>.0.json, ascending item index
                Map<Long, Integer> byTime = new TreeMap<>();
                try (var s = Files.list(d.resolve("assigned"))) {
                    for (Path p : (Iterable<Path>) s::iterator) {
                        String f = p.getFileName().toString();
                        FileTime t = Files.getLastModifiedTime(p);
                        byTime.put(t.to(java.util.concurrent.TimeUnit.MICROSECONDS) * 100 + byTime.size(),
                                Integer.parseInt(f.substring(0, f.indexOf('.'))));
                    }
                }
                List<Integer> order = new ArrayList<>(byTime.values());
                System.out.println("J8R6 reversed " + row[1] + " W=" + w + ": assignment order " + order
                        + ", items " + r.itemIterations() + ", md5 " + exportMd5(mp, r));
                if (w == 1) {
                    List<Integer> asc = new ArrayList<>();
                    for (int j = 0; j < r.n(); j++) {
                        asc.add(j);
                    }
                    assertEquals(asc, order, "W=1: items assigned 0..N-1");
                }
            }
        }
    }
}
