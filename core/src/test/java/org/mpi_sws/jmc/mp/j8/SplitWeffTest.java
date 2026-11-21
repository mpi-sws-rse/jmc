package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.h1.Litmus;
import org.mpi_sws.jmc.mp.fixtures.apps.A3Lock;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * SPLIT tester round 01: S-a2 (W slots; {@code min(W, N)} start at once, the others are dormant and start
 * when the live slots fall below {@code min(W, items not yet committed)}). Every worker start goes through
 * the {@code workerCommand} seam and is counted (id → starts); real workers run behind {@link J8PidWorker}
 * (gated until the liveness probe releases them), stand-ins are {@link J8WaitExitWorker}; process deaths are
 * made deterministic through the {@code liveness} seam: a stand-in in mode {@code now} is reported dead only
 * after it has exited. Each case prints one {@code CASE} line (failure, starts, lost, n, per-item iterations,
 * export md5) so that the same class run on the base (648e9c83) gives the base's behaviour for comparison.
 *
 * <p>Programs: A3Lock:fifo,3,1 at K = 1 (R 1, N 5, items 4..0 = 2 4 3 10 10, sequential export md5
 * {@link J8Round06Test#MD5}); Litmus:r_ss at K = 1 (N 1, count mode); Litmus:r_ss at K = 100 (no split).
 */
class SplitWeffTest {

    static final String RSS = "org.mpi_sws.jmc.mp.h1.Litmus:r_ss";
    static final String ITEMS = "{0=10, 1=10, 2=3, 3=4, 4=2}";

    /**
     * The merged results of every complete run ({@link #merged}), measured on the base 648e9c83 with the same
     * cases (every complete A3 or r_ss K 1 run of the base gave exactly these): every complete S-a2 run must
     * equal them (gate round 05, gap 1).
     */
    static final String BASE_A3 = "dc=30 db=0 ran=30 bi=0 summaries=05feb1587d2cd50a247113b6fda67c7d"
            + " fingerprint=556f1db1282457a5 itemsDigest=4c5906e5a880b59242418d2b9bd15762";
    static final String BASE_RSS = "dc=3 db=0 ran=3 bi=0 summaries=- fingerprint=ea3394eba0690e5d"
            + " itemsDigest=f10f902a04f31e5df039c5f272186c48";

    /** One run's set-up and observations. */
    static final class H {
        final String name;
        final String tag = UUID.randomUUID().toString();
        final Path d;
        final boolean a3;
        final Map<Integer, List<String>> plan = new HashMap<>();
        final TreeMap<Integer, Integer> starts = new TreeMap<>();
        final AtomicInteger nowDeaths = new AtomicInteger();
        BooleanSupplier release = () -> true;
        final Map<Integer, BooleanSupplier> releaseById = new HashMap<>();
        final Map<Integer, Long> releaseDelayMs = new HashMap<>();
        final Map<Integer, Long> commandDelayMs = new HashMap<>();
        long t0;
        BooleanSupplier exitTrigger = () -> false;
        int waitId = -1;
        Predicate<Process> realProbe;
        int k = 1;
        long timeout = 300;
        List<String> leftovers = List.of();

        H(String name, boolean a3) {
            this.name = name;
            this.a3 = a3;
            this.d = J8Round06Test.dir("weff-" + name);
        }

        H plan(int id, String... kinds) {
            plan.put(id, List.of(kinds));
            return this;
        }

        synchronized int count(int id) {
            return starts.merge(id, 1, Integer::sum) - 1;
        }

        synchronized TreeMap<Integer, Integer> starts() {
            return new TreeMap<>(starts);
        }

        String program() {
            return a3 ? J8Round06Test.A3 : RSS;
        }

        List<String> command(Path x, int id) {
            int i = count(id);
            Long delay = commandDelayMs.get(id);
            if (delay != null && i == 0) {
                try {
                    Thread.sleep(delay); // a slow workerCommand callback (gate round 03 P1)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            List<String> kinds = plan.getOrDefault(id, List.of("real"));
            String kind = kinds.get(Math.min(i, kinds.size() - 1));
            String j = J8Round06Test.JAVA;
            String cp = J8Round06Test.CP;
            switch (kind.split(":")[0]) {
                case "missing":
                    return List.of("/nonexistent/weff-no-such-binary-" + tag);
                case "now":
                case "wait":
                case "hang":
                    return List.of(j, "-Dj8.tag=" + tag, "-cp", cp, J8WaitExitWorker.class.getName(), x.toString(),
                            Integer.toString(id), kind);
                default:
                    List<String> real = new ArrayList<>(List.of(j, "-Dj8.tag=" + tag, "-Xss4m", "-cp", cp,
                            "org.mpi_sws.jmc.mp.cli.MpLauncher", program(), "must", "0", "1",
                            x.resolve("worker-report").toString(),
                            a3 ? x.resolve("worker-export.jsonl").toString() : "-", "300", "--worker", x.toString(),
                            Integer.toString(id)));
                    if (kind.startsWith("real:")) {
                        real.add(1, "-Djmc.split.fault=" + kind.substring(5));
                    }
                    List<String> out = new ArrayList<>(List.of(j, "-Dj8.tag=" + tag, "-cp", cp,
                            J8PidWorker.class.getName(), x.toString(), Integer.toString(id)));
                    out.addAll(real);
                    return out;
            }
        }

        boolean liveness(Process p) {
            Path w = d.resolve("wexit").resolve(Long.toString(p.pid()));
            Path r = d.resolve("pids").resolve(Long.toString(p.pid()));
            long deadline = System.currentTimeMillis() + 30_000;
            while (!Files.exists(w) && !Files.exists(r) && p.isAlive() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return p.isAlive();
                }
            }
            try {
                if (Files.exists(w)) {
                    String mode = Files.readString(w);
                    if (mode.equals("now")) {
                        p.waitFor();
                        nowDeaths.incrementAndGet();
                        return false;
                    }
                    if (mode.equals("wait") && exitTrigger.getAsBoolean()) {
                        Files.writeString(d.resolve("exit-" + waitId), "go");
                        p.waitFor();
                        return false;
                    }
                    return p.isAlive();
                }
                if (Files.exists(r)) {
                    if (realProbe != null) {
                        return realProbe.test(p);
                    }
                    String idText = Files.readString(r).trim();
                    if (idText.isEmpty()) {
                        return p.isAlive(); // the wrapper is still writing its id: asked again next pass
                    }
                    int id = Integer.parseInt(idText);
                    long age = System.currentTimeMillis() - Files.getLastModifiedTime(r).toMillis();
                    boolean ok = releaseById.getOrDefault(id, release).getAsBoolean()
                            && age >= releaseDelayMs.getOrDefault(id, 0L);
                    if (ok) {
                        J8Round07Test.release(d, p);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return p.isAlive();
        }

        /**
         * The S-a2 contract's crash bounds (owner's option A), checked after every run of either build: each
         * slot is started at most twice (one restart) and each item has at most attempts 0 and 1 (one retry).
         */
        void invariants() throws Exception {
            for (Map.Entry<Integer, Integer> e : starts().entrySet()) {
                assertTrue(e.getValue() <= 2, name + ": slot " + e.getKey() + " started " + e.getValue() + " times");
            }
            Path assigned = d.resolve("assigned");
            if (Files.isDirectory(assigned)) {
                try (var s = Files.list(assigned)) {
                    for (Path f : (Iterable<Path>) s::iterator) {
                        String n = f.getFileName().toString();
                        assertTrue(n.matches("\\d+\\.[01]\\.json"), name + ": assignment " + n);
                    }
                }
            }
        }

        SplitCoordinator.Result run(int w) throws Exception {
            SplitCoordinator.Result res;
            t0 = System.currentTimeMillis();
            try {
                if (a3) {
                    A3Lock m = J8Round06Test.a3();
                    res = SplitCoordinator.run(new SplitCoordinator.Config(J8Round06Test.A3 + "|p2p", m,
                            m.rolesInSpawnOrder(), m.visibleRoles(), k, w, timeout, d, this::command, false,
                            this::liveness));
                } else {
                    res = SplitCoordinator.run(new SplitCoordinator.Config(RSS, new Litmus("r_ss"), null, null, k, w,
                            timeout, d, this::command, false, this::liveness));
                }
            } finally {
                // an abort destroys the J8PidWorker wrappers forcibly, not their children: kill and record those
                List<String> left = new ArrayList<>();
                ProcessHandle.allProcesses().forEach(p -> p.info().commandLine().ifPresent(c -> {
                    if (c.contains(tag) && p.isAlive()) {
                        left.add(p.pid() + "");
                        p.destroyForcibly();
                    }
                }));
                leftovers = left;
            }
            invariants();
            String md5 = res.failure() == null && a3 ? J8Round06Test.exportMd5(J8Round06Test.a3(), res) : "-";
            System.out.println("CASE " + name + " W " + w + ": failure=" + res.failure() + " starts=" + starts()
                    + " lost=" + res.workersLost() + " split=" + res.split() + " n=" + res.n() + " items="
                    + res.itemIterations() + " ran=" + res.ran() + " md5=" + md5 + " leftovers=" + leftovers.size()
                    + " | " + merged(d, res));
            if (res.failure() == null && res.split()) {
                assertEquals(a3 ? BASE_A3 : BASE_RSS, merged(d, res), name + ": merged results as the base");
            }
            return res;
        }
    }

    /**
     * The merged results compared with the base (gate round 05, gap 1): distinct complete and blocked graphs,
     * ran, blocked iterations, the ordered summary lines (md5), run.json's fingerprint, and per item the
     * result file's fingerprint, iterations, blocked iterations, outcomes, summaries and names (md5 over the
     * items in index order; run id, worker, attempt, RSS and maxima left out).
     */
    static String merged(Path d, SplitCoordinator.Result r) throws Exception {
        StringBuilder b = new StringBuilder();
        b.append("dc=").append(r.distinctComplete()).append(" db=").append(r.distinctBlocked()).append(" ran=")
                .append(r.ran()).append(" bi=").append(r.blockedIterations());
        b.append(" summaries=").append(r.summaryLines() == null ? "-"
                : J8Launch.md5(String.join("\n", r.summaryLines()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path run = d.resolve("run.json");
        b.append(" fingerprint=").append(Files.exists(run)
                ? com.google.gson.JsonParser.parseString(Files.readString(run)).getAsJsonObject().get("fingerprint")
                        .getAsString().substring(0, 16) : "-");
        StringBuilder items = new StringBuilder();
        for (int j = 0; j < r.n(); j++) {
            Path f = d.resolve("item-" + j + ".json");
            if (!Files.exists(f)) {
                items.append(j).append(":absent;");
                continue;
            }
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (String k : List.of("item", "fingerprint", "iterations", "blockedIterations", "outcomes", "summaries",
                    "names")) {
                items.append(k).append('=').append(o.get(k)).append(';');
            }
        }
        b.append(" itemsDigest=").append(J8Launch.md5(items.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return b.toString();
    }

    static TreeMap<Integer, Integer> starts(int... idCount) {
        TreeMap<Integer, Integer> m = new TreeMap<>();
        for (int i = 0; i < idCount.length; i += 2) {
            m.put(idCount[i], idCount[i + 1]);
        }
        return m;
    }

    static TreeMap<Integer, Integer> once(int n) {
        TreeMap<Integer, Integer> m = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            m.put(i, 1);
        }
        return m;
    }

    static void completeA3(H h, SplitCoordinator.Result r) throws Exception {
        assertNull(r.failure(), h.name);
        assertEquals(5, r.n());
        assertEquals(ITEMS, r.itemIterations().toString(), h.name);
        assertEquals(J8Round06Test.MD5, J8Round06Test.exportMd5(J8Round06Test.a3(), r), h.name);
        assertEquals(List.of(), h.leftovers, h.name + ": leftovers");
    }

    // ---------------------------------------------------------------- item 1: the worker count

    @Test
    void a3_N5_startsMinWN_forW_1_2_4_5_6_16() throws Exception {
        for (int w : new int[] {1, 2, 4, 5, 6, 16}) {
            H h = new H("a3-w" + w, true);
            SplitCoordinator.Result r = h.run(w);
            completeA3(h, r);
            assertEquals(once(Math.min(w, 5)), h.starts(), "W " + w);
            assertEquals(0, r.workersLost());
        }
    }

    @Test
    void rss_N1_startsOneWorker_forW_1_2_16() throws Exception {
        for (int w : new int[] {1, 2, 16}) {
            H h = new H("rss-w" + w, false);
            SplitCoordinator.Result r = h.run(w);
            assertNull(r.failure(), "W " + w);
            assertTrue(r.split());
            assertEquals(1, r.n());
            assertEquals(1, r.itemIterations().size());
            assertEquals(once(1), h.starts(), "W " + w);
            assertEquals(List.of(), h.leftovers);
        }
    }

    @Test
    void rss_K100_noSplit_noWorkerStarted() throws Exception {
        H h = new H("rss-k100", false);
        h.k = 100;
        SplitCoordinator.Result r = h.run(16);
        assertNull(r.failure());
        assertFalse(r.split());
        assertEquals(0, r.n());
        assertEquals(new TreeMap<>(), h.starts());
    }

    // ---------------------------------------------------------------- item 2: failure paths

    @Test
    void a3_W16_deathOnItem0_retriedOnRestartedSlot() throws Exception {
        // S-a1 (round 02): items go out 0 .. N - 1, so worker 0 holds item 0 (was: item 4 with N - 1 .. 0)
        H h = new H("a3-death", true);
        for (int i = 0; i < 16; i++) {
            h.plan(i, "real:die-before:0:0");
        }
        SplitCoordinator.Result r = h.run(16);
        completeA3(h, r);
        assertEquals(starts(0, 2, 1, 1, 2, 1, 3, 1, 4, 1), h.starts(), "slot 0 (item 0) restarted, ids below N");
        assertTrue(Files.exists(h.d.resolve("consumed").resolve("0.0.json")));
        assertTrue(Files.exists(h.d.resolve("assigned").resolve("0.1.json")));
        assertEquals(0, r.workersLost());
    }

    @Test
    void rss_N1_W16_onlyWorkerDiesTwice_lostItsWorkerTwice() throws Exception {
        H h = new H("rss-twice", false);
        h.plan(0, "real:die-before:0:0,die-before:0:1");
        SplitCoordinator.Result r = h.run(16);
        assertEquals("item 0 lost its worker twice", r.failure());
        assertEquals(starts(0, 2), h.starts());
    }

    /** W 1: slot 0's first JVM exits before an assignment, its restart dies with 0.0: every slot lost. */
    @Test
    void rss_N1_W1_everyStartExitsAtOnce_everyWorkerWasLost() throws Exception {
        H h = new H("rss-alllost-w1", false);
        h.plan(0, "now");
        SplitCoordinator.Result r = h.run(1);
        assertEquals("every worker was lost", r.failure());
        assertEquals(starts(0, 2), h.starts());
        assertEquals(1, r.workersLost());
    }

    /**
     * A documented difference (an abort either way): W 2, every start exits at once. Base: both slots start,
     * slot 1 dies idle twice, slot 0's restart dies with 0.0: "every worker was lost", starts {0=2, 1=2},
     * lost 2. S-a2: slot 1 stays dormant until slot 0 is lost, then starts and takes 0.1, and dies with it:
     * "item 0 lost its worker twice", starts {0=2, 1=1}, lost 1.
     */
    @Test
    void rss_N1_W2_everyStartExitsAtOnce_differenceFromBase_lostItsWorkerTwice() throws Exception {
        H h = new H("rss-alllost-w2", false);
        h.plan(0, "now").plan(1, "now");
        SplitCoordinator.Result r = h.run(2);
        assertEquals("item 0 lost its worker twice", r.failure());
        assertEquals(starts(0, 2, 1, 1), h.starts());
        assertEquals(1, r.workersLost());
    }

    /**
     * Gate P1 (a): K 1, N 1, W 2, {@code die-before:0:0,die-after:0:1} on worker 0; the exit after 0.1's
     * publication is reported in the window between the result check and the death classification
     * (J8Round07Test's probe, target 0.1). Derived: 0.0 dies (void, restart), 0.1 is published, the recheck
     * commits it, slot 0 is lost; slot 1 is not lost, so no "every worker was lost": complete, lost 1.
     */
    @Test
    void gateP1a_N1_W2_resultThenDeathInCollection_complete() throws Exception {
        H h = new H("p1a", false);
        h.plan(0, "real:die-before:0:0,die-after:0:1");
        J8Round07Test.State st = new J8Round07Test.State();
        h.realProbe = J8Round07Test.probe(h.d, 0, 1, st);
        SplitCoordinator.Result r = h.run(2);
        assertNull(st.failure, st.failure);
        assertTrue(st.intercepted, "the probe answered false in the window");
        assertTrue(st.resultAbsentAtEntry);
        assertNull(r.failure());
        assertEquals(1, r.itemIterations().size());
        assertEquals(1, r.workersLost());
        assertEquals(starts(0, 2), h.starts(), "the dormant slot is never started");
        assertEquals(List.of(), h.leftovers);
    }

    /**
     * Gate P1 (b): K 1, N 1, W 2. Worker 0's first JVM exits before any assignment (restart used), its
     * replacement dies with 0.0 ({@code die-before:0:0}): slot 0 lost, no live slot, one item left: the dormant
     * slot 1 starts and runs 0.1. Derived: complete, lost 1, starts {0=2, 1=1}, 0.1 assigned to worker 1.
     */
    @Test
    void gateP1b_N1_W2_earlyExitThenDeath_attempt1OnDormantSlot() throws Exception {
        H h = new H("p1b", false);
        h.plan(0, "now", "real:die-before:0:0").plan(1, "real");
        SplitCoordinator.Result r = h.run(2);
        assertNull(r.failure());
        assertEquals(1, r.itemIterations().size());
        assertEquals(1, r.workersLost());
        assertEquals(starts(0, 2, 1, 1), h.starts());
        assertTrue(Files.readString(h.d.resolve("assigned").resolve("0.0.json")).replace(" ", "").contains("\"worker\":0"));
        assertTrue(Files.readString(h.d.resolve("assigned").resolve("0.1.json")).replace(" ", "").contains("\"worker\":1"));
        assertEquals(List.of(), h.leftovers);
    }

    /**
     * A dormant slot is started when losses leave fewer live slots than items to finish: A3, W 6 (5 start,
     * slot 5 dormant); worker 4 exits at once twice (lost before any commit; real workers are gated until
     * then). Derived: live 4 &lt; min(6, 5): slot 5 starts; complete as sequential, lost 1,
     * starts {0..3=1, 4=2, 5=1}.
     */
    @Test
    void a3_W6_slotLostBeforeAnyCommit_dormantSlotStarted() throws Exception {
        H h = new H("a3-dormant-start", true);
        h.plan(4, "now");
        h.release = () -> h.nowDeaths.get() >= 2;
        SplitCoordinator.Result r = h.run(6);
        completeA3(h, r);
        assertEquals(1, r.workersLost());
        assertEquals(starts(0, 1, 1, 1, 2, 1, 3, 1, 4, 2, 5, 1), h.starts());
    }

    /**
     * No dormant slot is started when the live slots cover every item not yet committed: A3, W 6; worker 4
     * (item 4; S-a1 hands out 0 .. N - 1) is a stand-in that exits once items 0..3 are published, its restart
     * exits at once. Derived: 4.1 goes to an idle live slot, slot 4 is lost with at most 1 item left and 4 live
     * slots: slot 5 never starts; complete as sequential, lost 1, starts {0..3=1, 4=2}.
     */
    @Test
    void a3_W6_slotLostWhenLiveSlotsCoverTheRest_noDormantStart() throws Exception {
        H h = new H("a3-no-dormant", true);
        h.plan(4, "wait");
        h.waitId = 4;
        h.exitTrigger = () -> {
            for (int j = 0; j <= 3; j++) {
                if (!Files.exists(h.d.resolve("item-" + j + ".json"))) {
                    return false;
                }
            }
            return true;
        };
        SplitCoordinator.Result r = h.run(6);
        completeA3(h, r);
        assertEquals(1, r.workersLost());
        assertEquals(starts(0, 1, 1, 1, 2, 1, 3, 1, 4, 2), h.starts());
    }

    // ---------------------------------------------------------------- gate P2: the exclusions (N 1, W 2)

    @Test
    void gateP2_N1_W2_worker1MissingExecutable_neverStarted_complete() throws Exception {
        H h = new H("p2-missing", false);
        h.plan(1, "missing");
        SplitCoordinator.Result r = h.run(2);
        assertNull(r.failure(), "the base aborts: coordinator failure: java.io.IOException");
        assertEquals(once(1), h.starts());
    }

    @Test
    void gateP2_N1_W2_worker1StartupFailMarker_neverStarted_complete() throws Exception {
        H h = new H("p2-failmarker", false);
        h.plan(1, "real:fingerprint:-1:-1");
        SplitCoordinator.Result r = h.run(2);
        assertNull(r.failure(), "the base aborts on worker 1's startup fail marker");
        assertEquals(once(1), h.starts());
        assertEquals(List.of(), h.leftovers);
    }

    @Test
    void gateP2_N1_W2_worker1HangsAtStop_neverStarted_complete() throws Exception {
        H h = new H("p2-hang", false);
        h.plan(1, "hang");
        h.timeout = 30;
        SplitCoordinator.Result r = h.run(2);
        assertNull(r.failure(), "the base aborts: worker 1 did not stop");
        assertEquals(once(1), h.starts());
        assertEquals(List.of(), h.leftovers);
    }

    @Test
    void gateP2_N1_W2_idleWorker1DiesTwice_neverStarted_lost0() throws Exception {
        H h = new H("p2-idle-lost", false);
        h.plan(1, "now");
        h.release = () -> true;
        SplitCoordinator.Result r = h.run(2);
        assertNull(r.failure());
        assertEquals(0, r.workersLost(), "the base counts worker 1 lost (1)");
        assertEquals(once(1), h.starts());
    }

    // ---------------------------------------------------------------- gate round 02

    /** The P1(b) set-up with slot 1's real worker released only 3 s after its wrapper starts. */
    static H deadlineCase(String name, long timeout, long deathAtMs) {
        H h = new H(name, false);
        h.timeout = timeout;
        h.plan(0, "now", "wait").plan(1, "real");
        h.waitId = 0;
        h.releaseDelayMs.put(1, 3000L);
        h.exitTrigger = () -> System.currentTimeMillis() - h.t0 >= deathAtMs;
        return h;
    }

    /**
     * Gate round 02 P1, the accepted exclusion, asserted: K 1, N 1, W 2, timeout 15 s. Slot 0's first JVM
     * exits before an assignment; its replacement holds 0.0 and dies at 13 s; worker 1 needs 3 s before it
     * starts its replay. Base: worker 1 started at once and is warm, runs 0.1 and the run completes. S-a2:
     * slot 1 starts only at 13 s, so the deadline comes first: "timed out with 0 of 1 items committed",
     * lost 1, starts {0=2, 1=1}, nothing left running.
     */
    @Test
    void gate2P1_deadline_retryOnDormantSlotTimesOut_documentedDifference() throws Exception {
        H h = deadlineCase("deadline-15s", 15, 13_000);
        SplitCoordinator.Result r = h.run(2);
        assertEquals("timed out with 0 of 1 items committed", r.failure());
        assertEquals(1, r.workersLost());
        assertEquals(starts(0, 2, 1, 1), h.starts());
        assertEquals(List.of(), h.leftovers);
    }

    /** The same with a generous timeout (300 s, death at 5 s): complete, as the base. */
    @Test
    void gate2P1_generousTimeout_retryOnDormantSlotCompletes() throws Exception {
        H h = deadlineCase("deadline-300s", 300, 5_000);
        SplitCoordinator.Result r = h.run(2);
        assertNull(r.failure());
        assertEquals("{0=2}", r.itemIterations().toString());
        assertEquals(3, r.ran());
        assertEquals(1, r.workersLost());
        assertEquals(starts(0, 2, 1, 1), h.starts());
        assertEquals(List.of(), h.leftovers);
    }

    /**
     * Restart after the final result (inherited, as the base): K 1, N 1, W 2, {@code die-after:0:0}, the
     * exit reported in the window after publication (J8Round07Test's probe, target 0.0). Derived: the
     * recheck commits 0.0, the slot holds no item and has its restart, so a replacement JVM starts before
     * STOP; complete, lost 0, starts {0=2} (base {0=2, 1=1}).
     */
    @Test
    void gate2_restartAfterFinalResult_extraJvmStarted() throws Exception {
        H h = new H("final-result-restart", false);
        h.plan(0, "real:die-after:0:0");
        J8Round07Test.State st = new J8Round07Test.State();
        h.realProbe = J8Round07Test.probe(h.d, 0, 0, st);
        SplitCoordinator.Result r = h.run(2);
        assertNull(st.failure, st.failure);
        assertTrue(st.intercepted);
        assertNull(r.failure());
        assertEquals(0, r.workersLost());
        assertEquals(starts(0, 2), h.starts());
        assertEquals(List.of(), h.leftovers);
    }

    /**
     * All remaining items outstanding on live workers: A3, W 6; worker 0 runs item 0 (S-a1: 0 .. N - 1) with
     * {@code die-after:0:0}, its restart exits at once (idle) and is lost while items 1..4 are outstanding
     * on workers 1..4 (held until then). Derived: live 4 = min(6, 4): no dormant start; complete, lost 1,
     * starts {0=2, 1..4=1} (base: slot 5 too).
     */
    @Test
    void gate2_slotLostWhileAllRemainingOutstanding_noDormantStart() throws Exception {
        H h = new H("a3-all-outstanding", true);
        h.plan(0, "real:die-after:0:0", "now");
        h.release = () -> h.nowDeaths.get() >= 1;
        h.releaseById.put(0, () -> true);
        SplitCoordinator.Result r = h.run(6);
        completeA3(h, r);
        assertEquals(1, r.workersLost());
        assertEquals(starts(0, 2, 1, 1, 2, 1, 3, 1, 4, 1), h.starts());
    }

    /**
     * Two slots lost with queued retries in one pass: A3; workers 3 and 4 exit at once twice (the second JVMs
     * hold 3.0 and 4.0 under S-a1's 0 .. N - 1); real workers are held until then. Derived: W 7: live 3 &lt;
     * min(7, 5): both dormant slots 5 and 6 start and take 4.1 and 3.1 (the queue's front); W 6: only slot 5 starts, the other retry waits for
     * an idle slot. Complete as sequential, lost 2.
     */
    @Test
    void gate2_twoSlotsLost_queuedRetries_dormantSlotsStarted_W7_W6() throws Exception {
        for (int w : new int[] {7, 6}) {
            H h = new H("a3-two-lost-w" + w, true);
            h.plan(3, "now").plan(4, "now");
            h.release = () -> h.nowDeaths.get() >= 4;
            SplitCoordinator.Result r = h.run(w);
            completeA3(h, r);
            assertEquals(2, r.workersLost(), "W " + w);
            TreeMap<Integer, Integer> e = starts(0, 1, 1, 1, 2, 1, 3, 2, 4, 2, 5, 1);
            if (w == 7) {
                e.put(6, 1);
            }
            assertEquals(e, h.starts(), "W " + w);
        }
    }

    // ---------------------------------------------------------------- gate round 03 P1 (accepted exception)

    /**
     * A healthy run, timeout 10 s: worker 0's real worker is released 11 s after its wrapper starts, and the
     * {@code workerCommand} callback for worker 1 takes 13 s. Base: the deadline starts after both starts, at
     * about 13 s, worker 0 is ready by then: complete, starts {0=1, 1=1}. S-a2: only worker 0 starts and the
     * deadline starts at once: "timed out with 0 of 1 items committed", starts {0=1}, lost 0, nothing left.
     */
    @Test
    void gate3P1_slowWorkerCommand_deadlineStartsEarlier_documentedDifference() throws Exception {
        H h = new H("deadline-start-10s", false);
        h.timeout = 10;
        h.releaseDelayMs.put(0, 11_000L);
        h.commandDelayMs.put(1, 13_000L);
        SplitCoordinator.Result r = h.run(2);
        assertEquals("timed out with 0 of 1 items committed", r.failure());
        assertEquals(once(1), h.starts());
        assertEquals(0, r.workersLost());
        assertEquals(List.of(), h.leftovers);
    }

    /** The same with timeout 300 s: complete in both. */
    @Test
    void gate3P1_slowWorkerCommand_generousTimeout_completes() throws Exception {
        H h = new H("deadline-start-300s", false);
        h.releaseDelayMs.put(0, 11_000L);
        h.commandDelayMs.put(1, 13_000L);
        SplitCoordinator.Result r = h.run(2);
        assertNull(r.failure());
        assertEquals("{0=2}", r.itemIterations().toString());
        assertEquals(once(1), h.starts());
        assertEquals(List.of(), h.leftovers);
    }

    // ---------------------------------------------------------------- gate round 05, gap 3

    /**
     * A dormant slot that, once started, uses its own restart allowance. A3, W 6: workers 0..3 are held
     * (items 0..3 outstanding); worker 4's two JVMs exit at once (the second holding 4.0; S-a1: 0 .. N - 1), so
     * slot 4 is lost and the dormant slot 5 starts and takes 4.1; with {@code die-after:4:1} it publishes 4.1 and
     * exits:
     * the slot holds no item and is restarted (its one restart). (a) the restart is a real worker: it stays
     * idle, the held workers are released, complete, lost 1. (b) the restart exits at once: slot 5 is lost
     * (a second death), live 4 cover the 4 items left: no further start, complete, lost 2. Both: starts
     * {0..3=1, 4=2, 5=2}, results as sequential. (The literal order "dies before its first assignment" is
     * not reachable deterministically for an activated slot: activation happens only with a queued item for
     * it, and the assignment follows in the same pass behind the real {@code isAlive} check.)
     */
    @Test
    void gate5_dormantSlotUsesItsRestart_thenCompletes_or_isLost() throws Exception {
        for (boolean secondDeath : new boolean[] {false, true}) {
            H h = new H("a3-dormant-restart-" + (secondDeath ? "lost" : "ok"), true);
            h.plan(4, "now").plan(5, "real:die-after:4:1", secondDeath ? "now" : "real");
            int nowNeeded = secondDeath ? 3 : 2;
            h.release = () -> h.starts().getOrDefault(5, 0) == 2 && h.nowDeaths.get() >= nowNeeded;
            h.releaseById.put(5, () -> true);
            SplitCoordinator.Result r = h.run(6);
            completeA3(h, r);
            assertEquals(secondDeath ? 2 : 1, r.workersLost(), h.name);
            assertEquals(starts(0, 1, 1, 1, 2, 1, 3, 1, 4, 2, 5, 2), h.starts(), h.name);
            assertTrue(Files.readString(h.d.resolve("assigned").resolve("4.1.json")).replace(" ", "")
                    .contains("\"worker\":5"), h.name);
        }
    }
}
