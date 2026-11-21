package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.j8.J8PidWorker;
import org.mpi_sws.jmc.mp.j8.J8WaitExitWorker;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/**
 * SPLIT tester round 01, gate round 03 P2 (accepted exception, asserted as a documented difference): a slot
 * started late re-reads the specification file. OnlineBank:2 at K 1 (N 3: items 2, 1, 0), W 4: the base starts
 * 4 workers at once (worker 3 idle, its specification loaded at start); S-a2 starts 3, slot 3 dormant.
 * Slot 2 (item 2; S-a1 hands out 0 .. N - 1) is a stand-in: its first JVM exits at once, its second holds 2.0.
 * The liveness probe, asked about that second JVM, waits until items 0 and 1 are published (so workers 0 and 1
 * loaded the file) and 3 s
 * more, then rewrites the specification file (one line dropped: another SHA-256) and lets the stand-in exit.
 * Derived: 2.0 is voided and requeued, slot 2 is lost; items 0 and 1 are not yet committed in that pass, so
 * live 2 &lt; min(4, 3) and S-a2 starts slot 3, which reads the rewritten file: startup fail marker of worker 3
 * (SHA-256 differs), abort. The base gives 2.1 to its warm worker 3: complete.
 */
class J8SWeffSpecTest {

    static final String JAVA = J8SApiTest.JAVA;
    static final String CP = J8SApiTest.CP;

    @Test
    void lateStartedSlot_rereadsChangedSpec_startupFailMarker_documentedDifference() throws Exception {
        Outcome o = run(true);
        assertEquals(3, o.r.n());
        assertNotNull(o.r.failure(), "S-a2: the late-started worker 3 refuses the rewritten specification");
        assertTrue(o.r.failure().startsWith("fail marker worker-3.fail.json") && o.r.failure().contains("SHA-256"),
                o.r.failure());
        assertEquals(new TreeMap<>(java.util.Map.of(0, 1, 1, 1, 2, 2, 3, 1)), o.starts);
    }

    /**
     * Gate round 05, gap 2: the same run with the specification unchanged. Derived: the activated worker 3
     * loads the same file and runs 2.1; complete, lost 1, starts {0=1, 1=1, 2=2, 3=1}; the semantic online
     * fields (all but {@code firstMissWallMs}) equal the base's, measured at 648e9c83 with the same
     * injection: miss, 1 uncovered graph, 1 distinct, first miss at iteration 3 (the dropped line), NoSig,
     * the witness of iteration 3, lookups 12 (11 found, 0 not found, 1 without signature).
     */
    @Test
    void lateStartedSlot_unchangedSpec_onlineAsBase() throws Exception {
        Outcome o = run(false);
        assertNull(o.r.failure());
        assertEquals("{0=6, 1=3, 2=2}", o.r.itemIterations().toString());
        assertEquals(1, o.r.workersLost());
        assertEquals(new TreeMap<>(java.util.Map.of(0, 1, 1, 1, 2, 2, 3, 1)), o.starts);
        assertEquals(12, o.r.ran());
        assertEquals(12, o.r.distinctComplete());
        assertEquals(0, o.r.distinctBlocked());
        assertEquals(BASE_ONLINE, semantic(o.r.online()));
        assertEquals(J8SSupport.bankLine(3), o.r.online().witness());
    }

    /** The base's semantic online fields for this run (648e9c83, same injection), filled from its CASE line. */
    static final String BASE_ONLINE = "miss=true uncoveredTotal=1 uncoveredDistinct=1 firstMissIteration=3 answer=NoSig lookups=12"
            + " found=11 notFound=0 noSig=1 witnessMd5=c3df021b69676ab872418efe93f90ab5";

    static String semantic(SplitCoordinator.Online on) {
        return "miss=" + on.miss() + " uncoveredTotal=" + on.uncoveredTotal() + " uncoveredDistinct="
                + on.uncoveredDistinct() + " firstMissIteration=" + on.firstMissIteration() + " answer=" + on.answer()
                + " lookups=" + on.lookups() + " found=" + on.found() + " notFound=" + on.notFound() + " noSig="
                + on.noSig() + " witnessMd5=" + md5(on.witness());
    }

    static String md5(String s) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
                    .digest(String.valueOf(s).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    record Outcome(SplitCoordinator.Result r, TreeMap<Integer, Integer> starts) {}

    static Outcome run(boolean change) throws Exception {
        Path spec = J8SSupport.DIR.resolve("weff-" + UUID.randomUUID() + ".jsonl");
        Files.copy(J8SSupport.bankSpec("weff-drop3", 3), spec);
        SpecificationFile.Loaded loaded = SpecificationFile.load(spec, new OnlineBank("2").expected());
        String tag = UUID.randomUUID().toString();
        Path d = J8SApiTest.dir("weff-spec");
        TreeMap<Integer, Integer> starts = new TreeMap<>();
        OnlineBank p = new OnlineBank("2");
        SplitCoordinator.Result r;
        try {
            r = SplitCoordinator.run(new SplitCoordinator.Config(J8SSupport.BANK + "|p2p", p, p.rolesInSpawnOrder(),
                    p.visibleRoles(), 1, 4, 300, d, (x, id) -> {
                        int i;
                        synchronized (starts) {
                            i = starts.merge(id, 1, Integer::sum) - 1;
                        }
                        if (id == 2) {
                            return List.of(JAVA, "-Dj8.tag=" + tag, "-cp", CP, J8WaitExitWorker.class.getName(),
                                    x.toString(), "2", i == 0 ? "now" : "wait");
                        }
                        List<String> c = new ArrayList<>(List.of(JAVA, "-Dj8.tag=" + tag, "-cp", CP,
                                J8PidWorker.class.getName(), x.toString(), Integer.toString(id)));
                        c.addAll(J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag));
                        return c;
                    }, false, proc -> liveness(d, spec, proc, change), loaded));
        } finally {
            ProcessHandle.allProcesses().forEach(h -> h.info().commandLine().ifPresent(c -> {
                if (c.contains(tag) && h.isAlive()) {
                    h.destroyForcibly();
                }
            }));
        }
        System.out.println("CASE weff-spec-" + (change ? "changed" : "unchanged") + " W 4: failure=" + r.failure()
                + " starts=" + starts + " lost=" + r.workersLost() + " n=" + r.n() + " items=" + r.itemIterations()
                + " ran=" + r.ran() + " dc=" + r.distinctComplete() + " db=" + r.distinctBlocked() + " bi="
                + r.blockedIterations() + " online: " + (r.online() == null ? "-" : semantic(r.online())));
        return new Outcome(r, starts);
    }

    static boolean liveness(Path d, Path spec, Process p, boolean change) {
        try {
            Path w = d.resolve("wexit").resolve(Long.toString(p.pid()));
            Path pid = d.resolve("pids").resolve(Long.toString(p.pid()));
            long deadline = System.currentTimeMillis() + 30_000;
            while (!Files.exists(w) && !Files.exists(pid) && p.isAlive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            if (Files.exists(pid)) {
                Files.createDirectories(d.resolve("gate"));
                Path g = d.resolve("gate").resolve(Long.toString(p.pid()));
                if (!Files.exists(g)) {
                    Files.writeString(g, "go");
                }
                return p.isAlive();
            }
            if (Files.exists(w)) {
                if (Files.readString(w).equals("now")) {
                    p.waitFor();
                    return false;
                }
                long until = System.currentTimeMillis() + 120_000;
                while (!(Files.exists(d.resolve("item-0.json")) && Files.exists(d.resolve("item-1.json")))) {
                    if (System.currentTimeMillis() > until) {
                        throw new IllegalStateException("items 0 and 1 not published in 120 s");
                    }
                    Thread.sleep(10);
                }
                Thread.sleep(3000);
                if (!change) {
                    Files.writeString(d.resolve("exit-2"), "go");
                    p.waitFor();
                    return false;
                }
                List<String> lines = new ArrayList<>(Files.readAllLines(spec, StandardCharsets.UTF_8));
                lines.remove(lines.size() - 1);
                Files.write(spec, lines, StandardCharsets.UTF_8);
                Files.writeString(d.resolve("exit-2"), "go");
                p.waitFor();
                return false;
            }
            return p.isAlive();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
