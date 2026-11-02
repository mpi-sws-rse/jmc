package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/**
 * J8S tester round 02 through the coordinator's API (one case at a time: the root runs in this JVM):
 * the wall-first versus sequential-first test (risk 15), the one-JVM sequence in reversed order, and risk 16
 * (another spec file, {@code --stop-at-first}, malformed online objects).
 */
class J8SApiTest {

    static final String JAVA = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    static final String CP = System.getProperty("java.class.path");

    static List<String> realWorker(Path d, int id, String program, String tag) {
        return realWorker(d, id, program, tag, List.of());
    }

    /** A real worker with extra JVM options (e.g. {@code -Djmc.split.fault=…}). */
    static List<String> realWorker(Path d, int id, String program, String tag, List<String> jvm) {
        List<String> c = new ArrayList<>(List.of(JAVA, "-Dj8.tag=" + tag, "-Xss4m"));
        c.addAll(jvm);
        c.addAll(List.of("-cp", CP, "org.mpi_sws.jmc.mp.launch.MpLauncher",
                program, "must", "0", "1", d.resolve("worker-report").toString(), "-", "300", "--worker", d.toString(),
                Integer.toString(id)));
        return c;
    }

    /**
     * Round 04 (M2): the committed envelopes' {@code first_at_ms} values that are a miss's (not -1), by item,
     * read from the run directory.
     */
    static java.util.TreeMap<Integer, Long> firstAts(Path d) throws Exception {
        java.util.TreeMap<Integer, Long> m = new java.util.TreeMap<>();
        try (var s = Files.list(d)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                java.util.regex.Matcher x = java.util.regex.Pattern.compile("item-(\\d+)\\.json").matcher(p.getFileName().toString());
                if (x.matches()) {
                    long t = JsonParser.parseString(Files.readString(p)).getAsJsonObject().getAsJsonObject("online")
                            .get("first_at_ms").getAsLong();
                    if (t >= 0) {
                        m.put(Integer.parseInt(x.group(1)), t);
                    }
                }
            }
        }
        return m;
    }

    static List<String> leftovers(String tag) {
        List<String> out = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(p -> p.info().commandLine().ifPresent(c -> {
            if (c.contains(tag) && p.isAlive()) {
                out.add(p.pid() + "");
            }
        }));
        return out;
    }

    static Path dir(String name) {
        return J8SSupport.DIR.resolve("api-" + name + "-" + System.currentTimeMillis() + "-" + System.nanoTime());
    }

    static SplitCoordinator.Result run(Path d, Path spec, int k, int w, boolean reversed,
                                       BiFunction<Path, Integer, List<String>> cmd) throws Exception {
        OnlineBank p = new OnlineBank("2");
        SpecificationFile.Loaded loaded = SpecificationFile.load(spec, p.visibleRoles());
        return SplitCoordinator.run(new SplitCoordinator.Config(J8SSupport.BANK + "|p2p", p, p.rolesInSpawnOrder(),
                p.visibleRoles(), k, w, 300, d, cmd, reversed, Process::isAlive, loaded));
    }

    static JsonObject env(Path d, int j) throws Exception {
        return JsonParser.parseString(Files.readString(d.resolve("item-" + j + ".json"))).getAsJsonObject();
    }

    /**
     * Wall minimum (risk 15). F6 (bank2-w1seq: misses at sequential iterations 3 and 8), K 4, W 1,
     * reversed assignment, {@code delay:0:0}: the worker explores item0 (holding iteration 8's miss) first,
     * 2000 ms after its assignment, and item3 (holding iteration 3's, the sequential-first) last. Derived:
     * first_miss_iteration 3, witness = iteration 3's line, answer NoSig. Round 04 (M2), the oracle observed
     * independently of the result: {@code before} is taken before {@code run} and {@code t1} at the first
     * worker-command call (after the coordinator's start, before any worker exists), so the coordinator's
     * start lies in {@code [before, t1]} and first_miss_wall_ms lies in {@code [min − t1, min − before]},
     * {@code min} the least committed {@code first_at_ms} (item0's); {@code min − t1 ≥ 2000} (the delay), so
     * a constant 0 is refused, and item3's later time falls outside the upper bound. The worker also serves
     * miss (item0) → no miss (item1) → empty (item2) → different miss (item3) in one JVM.
     */
    @Test
    void wallFirst_reversed_W1_and_oneJvmSequence() throws Exception {
        Path spec = J8SSupport.bankSpec("w1seq", 3, 8);
        Path d = dir("wall-reversed");
        String tag = UUID.randomUUID().toString();
        long[] t1 = {-1};
        long before = System.currentTimeMillis();
        SplitCoordinator.Result r = run(d, spec, 4, 1, true, (x, id) -> {
            if (t1[0] < 0) {
                t1[0] = System.currentTimeMillis();
            }
            return realWorker(x, id, J8SSupport.BANK, tag, List.of("-Djmc.split.fault=delay:0:0"));
        });
        assertNull(r.failure(), r.failure());
        SplitCoordinator.Online on = r.online();
        assertNotNull(on);
        assertEquals(3, on.firstMissIteration());
        assertEquals(J8SSupport.bankLine(3), on.witness());
        assertEquals("NoSig", on.answer());
        assertEquals(2L, on.uncoveredTotal());
        assertEquals(2, on.uncoveredDistinct());
        java.util.TreeMap<Integer, Long> at = firstAts(d);
        long min = at.values().stream().mapToLong(Long::longValue).min().orElseThrow();
        long e0 = at.get(0);
        long e3 = at.get(3);
        long wall = on.firstMissWallMs();
        System.out.println("J8S wall-first (M2): first_at_ms by item " + at + ", before " + before + ", t1 " + t1[0]
                + ", first_miss_wall_ms " + wall + ", bounds [" + (min - t1[0]) + ", " + (min - before)
                + "], item3 from before " + (e3 - before));
        assertEquals(java.util.Set.of(0, 3), at.keySet(), "the committed misses");
        assertEquals(e0, min, "item0's miss is the earliest (reversed order)");
        assertTrue(min - t1[0] >= 2000, "the delay separates the start from the first miss: " + (min - t1[0]));
        assertTrue(min - t1[0] <= wall && wall <= min - before,
                "first_miss_wall_ms " + wall + " outside [" + (min - t1[0]) + ", " + (min - before) + "]");
        // (item3's time is caught by the upper bound when e3 − min exceeds the coordinator's start − before,
        // which is at most t1 − before; printed, not asserted here: the W 4 case asserts a 1500 ms gap)
        System.out.println("J8S wall-first (M2): e3 − min " + (e3 - min) + " vs t1 − before " + (t1[0] - before));
        // the one-JVM sequence: item0 miss (first_local 2), item1 no miss, item2 empty, item3 miss (first_local 1)
        long[][] expect = {{0, 1, 2}, {1, 0, 0}, {2, 0, 0}, {3, 1, 1}};
        for (long[] e : expect) {
            JsonObject o = env(d, (int) e[0]).getAsJsonObject("online");
            assertEquals(e[1], o.get("uncovered").getAsLong(), "item " + e[0] + " " + o);
            assertEquals(e[2], o.get("first_local").getAsLong(), "item " + e[0] + " " + o);
            if (e[1] == 0) {
                assertEquals(-1, o.get("first_at_ms").getAsLong());
                assertEquals(0, o.getAsJsonArray("lines").size());
                assertTrue(o.get("witness").isJsonNull() && o.get("answer").isJsonNull());
            }
        }
        assertEquals(List.of(), leftovers(tag));
    }

    /**
     * Wall minimum with the delay seam (risk 15), through the launcher: F6, K 4, W 4, normal order,
     * {@code delay:3:0} (item3, which holds the sequential-first miss, starts 2000 ms late). Derived:
     * first miss iteration 3 (sequential order), its time from item0 (iteration 8's miss), earlier than
     * item3's; the header's first_miss_wall_ms equals the printed earliest time.
     */
    @Test
    void wallFirst_delaySeam_W4() throws Exception {
        Path spec = J8SSupport.bankSpec("w1seq", 3, 8);
        long before = System.currentTimeMillis();
        J8SSupport.Out o = J8SSupport.launch(List.of("-Djmc.split.fault=delay:3:0"), J8SSupport.BANK, true, "--spec",
                spec.toString(), "--split", "4", "--k", "4");
        assertEquals(0, o.exit(), o.text());
        String fm = o.field("first miss");
        System.out.println("J8S delay seam: first miss " + fm);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("iteration 3 \\(sequential order\\), earliest (\\d+) ms")
                .matcher(fm);
        assertTrue(m.find(), fm);
        long t = Long.parseLong(m.group(1));
        long e0 = env(o.runDir(), 0).getAsJsonObject("online").get("first_at_ms").getAsLong();
        long e3 = env(o.runDir(), 3).getAsJsonObject("online").get("first_at_ms").getAsLong();
        System.out.println("J8S delay seam: item0 " + e0 + ", item3 " + e3 + ", printed " + t);
        assertTrue(e3 - e0 >= 1500, "item3 delayed: " + (e3 - e0));
        // Round 04 (M2): bounds observed outside the result. The coordinator's start lies after the launch
        // ({@code before}) and before run.json's modification time.
        long min = firstAts(o.runDir()).values().stream().mapToLong(Long::longValue).min().orElseThrow();
        long runJson = Files.getLastModifiedTime(o.runDir().resolve("run.json")).toMillis();
        System.out.println("J8S delay seam (M2): min first_at_ms " + min + ", before " + before + ", run.json "
                + runJson + ", bounds [" + (min - runJson) + ", " + (min - before) + "], item3 from before " + (e3 - before));
        assertEquals(e0, min);
        assertTrue(min - runJson > 0, "a miss after run.json: " + (min - runJson));
        assertTrue(min - runJson <= t && t <= min - before, "printed " + t + " outside [" + (min - runJson) + ", "
                + (min - before) + "]");
        assertEquals(String.valueOf(t), o.block().get("first_miss_wall_ms"));
        String header = Files.readAllLines(o.exportFile()).get(0);
        assertTrue(header.contains("\"first_miss_wall_ms\":" + t + ","), header);
        assertTrue(header.contains("\"first_miss_iteration\":3,"), header);
    }

    /**
     * Round 04 (M2), through the API: F6, K 4, W 4, normal order, {@code delay:3:0}: item3 (the
     * sequential-first miss, iteration 3) starts 2000 ms after its assignment; item0's miss (iteration 8)
     * comes first in wall time. Oracle as in {@link #wallFirst_reversed_W1_and_oneJvmSequence}:
     * first_miss_wall_ms in {@code [min − t1, min − before]}, {@code min} = item0's first_at_ms;
     * {@code min − t1 > 0} (a worker JVM starts after t1), so a constant 0 is refused; item3's time is
     * at least 1500 ms later, outside the upper bound, so the sequential-first segment's time is refused.
     */
    @Test
    void wallFirst_delaySeam_W4_api_independentStart() throws Exception {
        Path spec = J8SSupport.bankSpec("w1seq", 3, 8);
        Path d = dir("wall-w4");
        String tag = UUID.randomUUID().toString();
        long[] t1 = {-1};
        long before = System.currentTimeMillis();
        SplitCoordinator.Result r = run(d, spec, 4, 4, false, (x, id) -> {
            synchronized (t1) {
                if (t1[0] < 0) {
                    t1[0] = System.currentTimeMillis();
                }
            }
            return realWorker(x, id, J8SSupport.BANK, tag, List.of("-Djmc.split.fault=delay:3:0"));
        });
        assertNull(r.failure(), r.failure());
        SplitCoordinator.Online on = r.online();
        assertEquals(3, on.firstMissIteration());
        assertEquals(J8SSupport.bankLine(3), on.witness());
        java.util.TreeMap<Integer, Long> at = firstAts(d);
        long min = at.values().stream().mapToLong(Long::longValue).min().orElseThrow();
        long wall = on.firstMissWallMs();
        System.out.println("J8S wall W4 api (M2): first_at_ms by item " + at + ", before " + before + ", t1 " + t1[0]
                + ", first_miss_wall_ms " + wall + ", bounds [" + (min - t1[0]) + ", " + (min - before)
                + "], item3 − min " + (at.get(3) - min));
        assertEquals(java.util.Set.of(0, 3), at.keySet(), "the committed misses");
        assertEquals((long) at.get(0), min, "item0's miss is the earliest");
        assertTrue(at.get(3) - min >= 1500, "item3 delayed: " + (at.get(3) - min));
        assertTrue(min - t1[0] > 0, "a miss after the first worker command: " + (min - t1[0]));
        assertTrue(min - t1[0] <= wall && wall <= min - before,
                "first_miss_wall_ms " + wall + " outside [" + (min - t1[0]) + ", " + (min - before) + "]");
        assertTrue(at.get(3) - t1[0] > min - before, "the sequential-first item's time is outside the bounds");
        assertEquals(List.of(), leftovers(tag));
    }

    /** Risk 16: a worker whose spec file differs (its SHA-256): startup failure, abort, no retry. */
    @Test
    void anotherSpecFile_startupFailure_noRetry() throws Exception {
        Path spec = J8SSupport.bankSpec("multi", 3, 5, 9, 12);
        Path copy = J8SSupport.DIR.resolve("tamper-" + System.nanoTime() + ".jsonl");
        Files.copy(spec, copy);
        Path d = dir("tamper");
        String tag = UUID.randomUUID().toString();
        SplitCoordinator.Result r = run(d, copy, 1, 2, false, (x, id) -> {
            List<String> c = new ArrayList<>(List.of(JAVA, "-Dj8.tag=" + tag, "-cp", CP, J8SpecTamperWorker.class.getName(),
                    copy.toAbsolutePath().toString()));
            c.addAll(realWorker(x, id, J8SSupport.BANK, tag));
            return c;
        });
        System.out.println("J8S tamper: " + r.failure());
        assertNotNull(r.failure());
        assertTrue(r.failure().contains(".fail.json") && r.failure().contains("SHA-256"), r.failure());
        try (var s = Files.list(d.resolve("assigned"))) {
            assertTrue(s.noneMatch(p -> p.getFileName().toString().endsWith(".1.json")), "no retry");
        }
        // Round 05: the coordinator ends the wrapper (J8SpecTamperWorker) with destroyForcibly, which skips
        // the wrapper's shutdown hook, so the real worker it started can outlive it for a moment (it fails at
        // startup by itself: its hash differs). Such a grandchild is not the coordinator's process; it must
        // end by itself within 30 s, and anything else tagged must be gone at once.
        List<String> left = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(p -> p.info().commandLine().ifPresent(c -> {
            if (c.contains(tag) && p.isAlive()) {
                left.add(p.pid() + " " + (c.contains(J8SpecTamperWorker.class.getName()) ? "wrapper" : "worker")
                        + " parent " + p.parent().map(q -> q.pid() + "").orElse("-"));
            }
        }));
        System.out.println("J8S tamper: tagged processes right after the run " + left);
        assertTrue(left.stream().allMatch(x -> x.contains(" worker parent ")), "a wrapper left: " + left);
        long until = System.currentTimeMillis() + 30_000;
        while (!leftovers(tag).isEmpty() && System.currentTimeMillis() < until) {
            Thread.sleep(100);
        }
        System.out.println("J8S tamper: tagged processes after waiting " + leftovers(tag));
        assertEquals(List.of(), leftovers(tag));
    }

    /** Risk 16: --stop-at-first with --split exits 2; both API call orders throw. */
    @Test
    void stopAtFirst_refused_launcherAndBothApiOrders() throws Exception {
        Path spec = J8SSupport.bankSpec("multi", 3, 5, 9, 12);
        J8SSupport.Out o = J8SSupport.launch(J8SSupport.BANK, false, "--spec", spec.toString(), "--stop-at-first",
                "--split", "2");
        System.out.println("J8S stop-at-first: exit " + o.exit() + " " + o.text().trim().replace('\n', ' '));
        assertEquals(2, o.exit());
        assertTrue(o.text().contains("--stop-at-first cannot be combined with --split"), o.text());
        OnlineBank p = new OnlineBank("2");
        SpecificationFile.Loaded loaded = SpecificationFile.load(spec, p.visibleRoles());
        MustStrategy.SplitControl ctl = new MustStrategy.SplitControl() {
            @Override public int splitThreshold() {
                return 1;
            }

            @Override public void outcome(boolean blocked, GraphCanon canon, LongFunction<String> names) {}

            @Override public boolean atBoundary(MustStrategy.SplitView view) {
                return false;
            }

            @Override public void segmentDone(MustStrategy.Segment segment) {}

            @Override public OptionalInt nextItem() {
                return OptionalInt.empty();
            }
        };
        MustStrategy a = new MustStrategy(1L, "r", p.rolesInSpawnOrder(), p.visibleRoles());
        a.checkAgainst(loaded.index(), true);
        assertThrows(IllegalStateException.class, () -> a.setSplitControl(ctl));
        MustStrategy b = new MustStrategy(1L, "r", p.rolesInSpawnOrder(), p.visibleRoles());
        b.setSplitControl(ctl);
        assertThrows(IllegalStateException.class, () -> b.checkAgainst(loaded.index(), true));
        // full mode stays allowed in both orders
        MustStrategy c = new MustStrategy(1L, "r", p.rolesInSpawnOrder(), p.visibleRoles());
        c.checkAgainst(loaded.index(), false);
        c.setSplitControl(ctl);
    }

    /** An online object for the fake worker: the given JSON with {@code L3}, {@code L5}, {@code L1} replaced. */
    static String online(String template) throws Exception {
        return template.replace("L3", quote(J8SSupport.bankLine(3))).replace("L5", quote(J8SSupport.bankLine(5)))
                .replace("L1", quote(J8SSupport.bankLine(1)));
    }

    static String quote(String s) {
        return new com.google.gson.JsonPrimitive(s).toString();
    }

    /**
     * Risk 16, malformed online objects (fake worker 0 publishes item 2 at K 1 with one field wrong; worker
     * 1 real, tagged), spec bank2-multi (iterations 3 and 5 uncovered, NoSig; iteration 1 covered). Each
     * refused with the validation's message and no worker left; the well-formed control accepted.
     */
    @Test
    void malformedOnlineObjects_refused_noWorkerLeft_controlAccepted() throws Exception {
        String base = "{\"uncovered\":1,\"lines\":[L3],\"first_local\":1,\"witness\":L3,\"answer\":\"NoSig\","
                + "\"first_at_ms\":@NOW@,\"lookups\":1,\"found\":0,\"not_found\":0,\"no_sig\":1}";
        String noMiss = "{\"uncovered\":0,\"lines\":[],\"first_local\":0,\"witness\":null,\"answer\":null,"
                + "\"first_at_ms\":-1,\"lookups\":1,\"found\":1,\"not_found\":0,\"no_sig\":0}";
        String[][] cases = {
            // label, online template, iterations, expected fragment (null = accepted)
            {"control", base, "1", null},
            {"control no miss", noMiss, "1", null},
            {"negative count", base.replace("\"uncovered\":1", "\"uncovered\":-1"), "1", "online field uncovered"},
            {"fractional count", base.replace("\"lookups\":1", "\"lookups\":1.5"), "1", "online field lookups"},
            {"sentinel first_local", noMiss.replace("\"first_local\":0", "\"first_local\":1"), "1", "no uncovered graph but"},
            {"sentinel lines", noMiss.replace("\"lines\":[]", "\"lines\":[L3]"), "1", "no uncovered graph but"},
            {"sentinel witness", noMiss.replace("\"witness\":null", "\"witness\":L3"), "1", "no uncovered graph but"},
            {"sentinel answer", noMiss.replace("\"answer\":null", "\"answer\":\"NoSig\""), "1", "no uncovered graph but"},
            {"sentinel first_at_ms", noMiss.replace("\"first_at_ms\":-1", "\"first_at_ms\":@NOW@"), "1",
                "no uncovered graph but"},
            {"#lines > uncovered", base.replace("\"lines\":[L3]", "\"lines\":[L3,L5]"), "1", "counts are impossible"},
            {"uncovered > iterations - first_local + 1", base.replace("\"uncovered\":1", "\"uncovered\":2")
                .replace("\"lookups\":1", "\"lookups\":2").replace("\"no_sig\":1", "\"no_sig\":2"), "1", "counts are impossible"},
            {"first_local 0 with a miss", base.replace("\"first_local\":1", "\"first_local\":0"), "1", "outside 1..1"},
            {"duplicate line", base.replace("\"lines\":[L3]", "\"lines\":[L3,L3]").replace("\"uncovered\":1", "\"uncovered\":2")
                .replace("\"lookups\":1", "\"lookups\":2").replace("\"no_sig\":1", "\"no_sig\":2"), "2", "repeat a summary"},
            {"witness != lines[0]", base.replace("\"lines\":[L3]", "\"lines\":[L3,L5]").replace("\"witness\":L3", "\"witness\":L5")
                .replace("\"uncovered\":1", "\"uncovered\":2").replace("\"lookups\":1", "\"lookups\":2")
                .replace("\"no_sig\":1", "\"no_sig\":2"), "2", "witness is not the first uncovered line"},
            {"covered line", base.replace("L3", "L1"), "1", "is covered by the specification"},
            {"wrong answer", base.replace("\"answer\":\"NoSig\"", "\"answer\":\"NotFound\"").replace("\"not_found\":0", "\"not_found\":1")
                .replace("\"no_sig\":1", "\"no_sig\":0"), "1", "differs from the index's"},
            {"unknown answer", base.replace("\"answer\":\"NoSig\"", "\"answer\":\"Maybe\""), "1", "not NoSig or NotFound"},
            {"timestamp before the run", base.replace("@NOW@", "1"), "1", "outside the run"},
            {"timestamp after the commit", base.replace("@NOW@", "99999999999999"), "1", "outside the run"},
            {"lookups sum broken", base.replace("\"lookups\":1", "\"lookups\":2"), "1", "do not add up"},
            {"uncovered sum broken", base.replace("\"no_sig\":1", "\"no_sig\":0").replace("\"found\":0", "\"found\":1"), "1",
                "do not add up"},
            {"no online object", "MISSING", "1", "carries no online object"},
        };
        Path spec = J8SSupport.bankSpec("multi", 3, 5, 9, 12);
        List<String> bad = new ArrayList<>();
        for (String[] c : cases) {
            String tag = UUID.randomUUID().toString();
            Path file = J8SSupport.DIR.resolve("online-" + System.nanoTime() + ".json");
            Files.writeString(file, c[1].equals("MISSING") ? "MISSING" : online(c[1]));
            Path d = dir("malformed");
            SplitCoordinator.Result r = run(d, spec, 1, 2, false, (x, id) -> id == 0
                    ? List.of(JAVA, "-Dj8.tag=" + tag, "-cp", CP, J8OnlineFakeWorker.class.getName(), x.toString(), "0",
                            file.toString(), c[2])
                    : realWorker(x, id, J8SSupport.BANK, tag));
            List<String> left = leftovers(tag);
            System.out.println("J8S malformed " + c[0] + " -> " + (r.failure() == null ? "accepted" : r.failure())
                    + "; leftovers " + left);
            if (!left.isEmpty()) {
                bad.add(c[0] + ": leftovers " + left);
            }
            if (c[3] == null ? r.failure() != null : r.failure() == null || !r.failure().contains("item 2")
                    || !r.failure().contains(c[3])) {
                bad.add(c[0] + ": expected " + (c[3] == null ? "accepted" : "item 2 … " + c[3]) + ", got " + r.failure());
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * J8S round 03 (the {@code str()} change): a result envelope whose {@code run} or {@code fingerprint} is
     * an explicit JSON null. Derived: {@code str()} returns null, the envelope does not match the assignment,
     * and the coordinator refuses it as a semantic failure ({@code item 2: envelope … does not match the
     * assignment …}) instead of throwing; the run is incomplete and no worker is left.
     */
    @Test
    void nullRunOrFingerprint_refusedAsForeignEnvelope_noCrash() throws Exception {
        String base = "{\"uncovered\":1,\"lines\":[L3],\"first_local\":1,\"witness\":L3,\"answer\":\"NoSig\","
                + "\"first_at_ms\":@NOW@,\"lookups\":1,\"found\":0,\"not_found\":0,\"no_sig\":1}";
        Path spec = J8SSupport.bankSpec("multi", 3, 5, 9, 12);
        List<String> bad = new ArrayList<>();
        for (String field : List.of("run", "fingerprint")) {
            String tag = UUID.randomUUID().toString();
            Path file = J8SSupport.DIR.resolve("online-null-" + System.nanoTime() + ".json");
            Files.writeString(file, online(base));
            Path d = dir("null-" + field);
            SplitCoordinator.Result r = run(d, spec, 1, 2, false, (x, id) -> id == 0
                    ? List.of(JAVA, "-Dj8.tag=" + tag, "-cp", CP, J8OnlineFakeWorker.class.getName(), x.toString(), "0",
                            file.toString(), "1", field)
                    : realWorker(x, id, J8SSupport.BANK, tag));
            String env = Files.readString(d.resolve("item-2.json"));
            List<String> left = leftovers(tag);
            System.out.println("J8S null " + field + ": envelope has \"" + field + "\":null " + env.contains("\"" + field
                    + "\":null") + "; failure " + r.failure() + "; leftovers " + left);
            if (!env.contains("\"" + field + "\":null")) {
                bad.add(field + ": the fake did not write an explicit null");
            }
            if (r.failure() == null || !r.failure().startsWith("item 2: envelope ")
                    || !r.failure().contains("does not match the assignment")) {
                bad.add(field + ": expected a refused envelope, got " + r.failure());
            }
            if (!left.isEmpty()) {
                bad.add(field + ": leftovers " + left);
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
