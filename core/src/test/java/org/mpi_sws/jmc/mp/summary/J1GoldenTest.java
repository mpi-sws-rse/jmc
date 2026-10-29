package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.programs.MustSection13;
import org.mpi_sws.jmc.mp.programs.j1.BlockedAfterOne;
import org.mpi_sws.jmc.mp.programs.j1.BlockedAlone;
import org.mpi_sws.jmc.mp.programs.j1.MustSection13SendsSwapped;
import org.mpi_sws.jmc.mp.programs.j1.NondetSend;
import org.mpi_sws.jmc.mp.programs.j1.SRnb;
import org.mpi_sws.jmc.mp.programs.j1.SendsTid;
import org.mpi_sws.jmc.strategies.mp.MpRecordingStrategy;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tester tests for J1 round 01, claims C1 and C2: golden summary lines, derived by hand from
 * plan/jmc-must/SUMMARY-FORMAT.md before reading the recorder, against
 * {@link MpRecordingStrategy#summaries()} of an in-process run driven through
 * {@code JmcCheckerConfiguration.Builder.strategyConstructor}.
 *
 * <p>Every {@code *_detects_*} method runs the same comparison against a mutated expected line or a
 * mutated fixture and requires it to fail.
 */
class J1GoldenTest {

    private static final long SEED = 20261026L;

    record Run(MpRecordingStrategy rec, JmcModelCheckerReport report) {}

    static Run run(MpProgram p, int n, long seed) throws Exception {
        MpRecordingStrategy rec = new MpRecordingStrategy(
                seed, "build/test-results/j1-tester/" + p.getClass().getSimpleName(),
                p.rolesInSpawnOrder(), p.visibleRoles());
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random")
                .strategyConstructor(c -> rec)
                .numIterations(n)
                .seed(seed)
                .reportPath("build/test-results/j1-tester/" + p.getClass().getSimpleName())
                .build();
        JmcModelCheckerReport report =
                new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Summary, Integer> e : rec.summaries().entrySet()) {
            lines.add(e.getKey().toJson(e.getValue()));
        }
        System.out.println("RUN " + p.getClass().getSimpleName() + " n=" + n + " seed=" + seed
                + " total=" + report.getTotalIterations() + " blocked=" + report.getBlockedIterations()
                + " exported=" + rec.exportedIterations() + " skipped=" + rec.skippedIterations());
        for (String l : lines) {
            System.out.println("LINE " + l);
        }
        return new Run(rec, report);
    }

    private static final Pattern N_FIELD = Pattern.compile(",\"n\":(\\d+)}$");

    private static String stripN(String line) {
        return N_FIELD.matcher(line).replaceFirst("}");
    }

    /** The exact lines (with n), as a set: the format fixes no line order. */
    static void assertExact(TreeSet<String> expected, Run r) {
        TreeSet<String> actual = new TreeSet<>();
        r.rec().summaries().forEach((s, n) -> actual.add(s.toJson(n)));
        assertEquals(String.join("\n", expected), String.join("\n", actual), "summary lines");
    }

    /** The lines without n, as a set, and the n values summing to the exported iterations == expectedExported. */
    static void assertLines(TreeSet<String> expectedNoN, Run r, int expectedExported) {
        TreeSet<String> actual = new TreeSet<>();
        int sum = 0;
        for (Map.Entry<Summary, Integer> e : r.rec().summaries().entrySet()) {
            String line = e.getKey().toJson(e.getValue());
            Matcher m = N_FIELD.matcher(line);
            assertTrue(m.find(), "line ends with n: " + line);
            assertEquals(e.getValue().intValue(), Integer.parseInt(m.group(1)), "n in the line");
            assertTrue(e.getValue() > 0, "n > 0");
            sum += e.getValue();
            actual.add(stripN(line));
        }
        assertEquals(String.join("\n", expectedNoN), String.join("\n", actual), "summary lines (n stripped)");
        assertEquals(expectedExported, sum, "sum of n");
        assertEquals(expectedExported, r.rec().exportedIterations(), "exported iterations");
    }

    static TreeSet<String> set(String... lines) {
        return new TreeSet<>(List.of(lines));
    }

    static void mutant(String what, ThrowingRunnable body) {
        AssertionFailedError e = assertThrows(AssertionFailedError.class, () -> {
            try {
                body.run();
            } catch (AssertionFailedError a) {
                throw a;
            } catch (Exception x) {
                throw new RuntimeException(x);
            }
        });
        System.out.println("MUTANT VERDICT (" + what + "): " + e.getMessage().split("\n")[0]);
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ------------------------------------------------------------------ C1: Must §1.3 under p2p

    static final String M1 = "{\"t\":\"Msg\",\"f\":[1]}";
    static final String M2 = "{\"t\":\"Msg\",\"f\":[2]}";
    static final String M3 = "{\"t\":\"Msg\",\"f\":[3]}";
    static final String ST3 = "\"statuses\":{\"t1\":\"done\",\"t2\":\"done\",\"t3\":\"done\"}";
    static final String W12 = "\"t1\":[{\"s\":" + M1 + "},{\"s\":" + M2 + "}],\"t2\":[{\"r\":" + M2 + "},{\"s\":" + M3 + "}]";

    /** t3 reads 1 then 3. Counts: t1 0,0 0,0 | t2 (t1=2,t3=0)x2 | t3 e0 t1=1,t2=0; e1 t1=2,t2=2. */
    static String c1Line13(int n) {
        return "{" + ST3 + ",\"words\":{" + W12 + ",\"t3\":[{\"r\":" + M1 + "},{\"r\":" + M3 + "}]},"
                + "\"counts\":[0,0,0,0,2,0,2,0,1,0,2,2],\"n\":" + n + "}";
    }

    /** t3 reads 3 then 1: both t3 events after all of t1 and t2. */
    static String c1Line31(int n) {
        return "{" + ST3 + ",\"words\":{" + W12 + ",\"t3\":[{\"r\":" + M3 + "},{\"r\":" + M1 + "}]},"
                + "\"counts\":[0,0,0,0,2,0,2,0,2,2,2,2],\"n\":" + n + "}";
    }

    private static final Pattern TALLY = Pattern.compile("t3 read 1 first: (\\d+); 3 first: (\\d+); other first: (\\d+)");

    static void checkC1(MpProgram p, int n, long seed, boolean useTally) throws Exception {
        Run r = run(p, n, seed);
        assertNull(r.report().getErrorMessage(), "error recorded");
        int n13;
        int n31;
        if (useTally) {
            Matcher m = TALLY.matcher(p.toString());
            assertTrue(m.matches(), "tally: " + p);
            n13 = Integer.parseInt(m.group(1));
            n31 = Integer.parseInt(m.group(2));
            assertEquals(0, Integer.parseInt(m.group(3)), "other first");
        } else {
            n13 = r.rec().summaries().values().stream().findFirst().orElse(0);
            n31 = n - n13;
        }
        assertEquals(n, n13 + n31, "tally sums to n");
        assertTrue(n13 > 0 && n31 > 0, "both outcomes sampled");
        assertExact(set(c1Line13(n13), c1Line31(n31)), r);
        assertEquals(n, r.rec().exportedIterations(), "exported");
        assertEquals(0, r.rec().skippedIterations(), "skipped");
    }

    @Test
    void c1_golden_mustSection13() throws Exception {
        checkC1(new MustSection13(), 2000, SEED, true);
    }

    @Test
    void c1_golden_mustSection13_exactN_seed20261026() throws Exception {
        // the launcher run of record gave 1978 / 22 for this seed and 2000 iterations
        Run r = run(new MustSection13(), 2000, SEED);
        assertExact(set(c1Line13(1978), c1Line31(22)), r);
    }

    @Test
    void c1_detects_mutatedCounts() {
        mutant("C1 counts", () -> {
            Run r = run(new MustSection13(), 2000, SEED);
            // mutation: t3's first read of 1 counted with t1=2 (as if the whole of t1 preceded it)
            assertExact(set(c1Line13(1978).replace("2,0,1,0,2,2]", "2,0,2,0,2,2]"), c1Line31(22)), r);
        });
    }

    @Test
    void c1_detects_mutatedStatusOrder() {
        mutant("C1 key order", () -> {
            Run r = run(new MustSection13(), 2000, SEED);
            // mutation: roles in spawn order instead of ASCII order inside "statuses"
            String bad = "\"statuses\":{\"t3\":\"done\",\"t2\":\"done\",\"t1\":\"done\"}";
            assertExact(set(c1Line13(1978).replace(ST3, bad), c1Line31(22).replace(ST3, bad)), r);
        });
    }

    @Test
    void c1_detects_SendsSwappedFixture() {
        mutant("C1 fixture MustSection13SendsSwapped", () -> checkC1(new MustSection13SendsSwapped(), 2000, SEED, false));
    }

    // ------------------------------------------------------------------ C2(a): S+R^nb under asyn

    static final String V7 = "{\"t\":\"V\",\"f\":[7]}";
    static final String SRNB_BOTTOM = "{\"statuses\":{\"r\":\"done\",\"s\":\"done\"},\"words\":{\"r\":[{\"r\":null}],\"s\":[{\"s\":" + V7 + "}]},\"counts\":[0,0]}";
    static final String SRNB_READ = "{\"statuses\":{\"r\":\"done\",\"s\":\"done\"},\"words\":{\"r\":[{\"r\":" + V7 + "}],\"s\":[{\"s\":" + V7 + "}]},\"counts\":[1,0]}";

    @Test
    void c2a_golden_SRnb() throws Exception {
        Run r = run(new SRnb(), 400, SEED);
        assertNull(r.report().getErrorMessage());
        assertEquals(0, r.report().getBlockedIterations(), "blocked");
        assertLines(set(SRNB_BOTTOM, SRNB_READ), r, 400);
    }

    @Test
    void c2a_detects_bottomCounted() {
        mutant("C2a", () -> {
            Run r = run(new SRnb(), 400, SEED);
            // mutation: a bottom read counted as if it read s's send
            assertLines(set(SRNB_BOTTOM.replace("[0,0]", "[1,0]"), SRNB_READ), r, 400);
        });
    }

    // ------------------------------------------------------------------ C2(b): blocked for good

    static final String V1 = "{\"t\":\"V\",\"f\":[1]}";
    static final String BLOCKED1 = "{\"statuses\":{\"r\":\"blocked\",\"s\":\"done\"},\"words\":{\"r\":[{\"r\":" + V1 + "}],\"s\":[{\"s\":" + V1 + "}]},\"counts\":[1,0]}";
    static final String BLOCKED0 = "{\"statuses\":{\"r\":\"blocked\"},\"words\":{\"r\":[]},\"counts\":[]}";

    @Test
    void c2b_golden_BlockedAfterOne() throws Exception {
        Run r = run(new BlockedAfterOne(), 200, SEED);
        assertNull(r.report().getErrorMessage(), "error recorded");
        assertEquals(200, r.report().getTotalIterations(), "total");
        assertEquals(200, r.report().getBlockedIterations(), "every iteration blocked");
        assertLines(set(BLOCKED1), r, 200);
        // the Block label is the last event of r in the graph
        var g = r.rec().lastGraph();
        System.out.println("LASTGRAPH " + g);
    }

    @Test
    void c2b_golden_BlockedAlone() throws Exception {
        Run r = run(new BlockedAlone(), 50, SEED);
        assertNull(r.report().getErrorMessage(), "error recorded");
        assertEquals(50, r.report().getBlockedIterations(), "every iteration blocked");
        assertLines(set(BLOCKED0), r, 50);
    }

    @Test
    void c2b_detects_blockAsObservation() {
        mutant("C2b", () -> {
            Run r = run(new BlockedAfterOne(), 200, SEED);
            // mutation: the parked receive written as an observation reading bottom and counted
            assertLines(set(BLOCKED1.replace("\"r\":[{\"r\":" + V1 + "}]", "\"r\":[{\"r\":" + V1 + "},{\"r\":null}]")
                    .replace("[1,0]", "[1,1,0]")), r, 200);
        });
    }

    // ------------------------------------------------------------------ C2(c): nondet before a send

    static String nd(int x) {
        String v = "{\"t\":\"V\",\"f\":[" + x + "]}";
        return "{\"statuses\":{\"r\":\"done\",\"s\":\"done\"},\"words\":{\"r\":[{\"r\":" + v + "}],\"s\":[{\"s\":" + v + "}]},\"counts\":[1,0]}";
    }

    @Test
    void c2c_golden_NondetSend() throws Exception {
        Run r = run(new NondetSend(), 400, SEED);
        assertNull(r.report().getErrorMessage());
        assertLines(set(nd(5), nd(9)), r, 400);
    }

    @Test
    void c2c_detects_nondetAsPosition() {
        mutant("C2c", () -> {
            Run r = run(new NondetSend(), 400, SEED);
            // mutation: the nondet counted as a visible event of s, so r's receive has two of s's
            // events porf-before it
            assertLines(set(nd(5).replace("[1,0]", "[2,0]"), nd(9).replace("[1,0]", "[2,0]")), r, 400);
        });
    }

    // ------------------------------------------------------------------ C2(d): main relays

    static final String RELAY = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":[{\"s\":" + V1 + "}],\"b\":[{\"r\":" + V1 + "}]},\"counts\":[0,1]}";

    // MainRelay (main receives and sends) is refused since round 04: MpLauncherExportTest.shape_mainRelay_refused

    // ------------------------------------------------------------------ C3 in a run: Tid payloads

    static final String WHO = "{\"t\":\"Who\",\"f\":[\"a\",\"main\"]}";
    static final String TIDLINE = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":[{\"s\":" + WHO + "}],\"b\":[{\"r\":" + WHO + "}]},\"counts\":[0,1]}";

    @Test
    void c3_golden_SendsTid() throws Exception {
        Run r = run(new SendsTid(), 100, SEED);
        assertNull(r.report().getErrorMessage());
        assertLines(set(TIDLINE), r, 100);
    }

    @Test
    void c3_detects_tidAsNumber() {
        mutant("C3 tid", () -> {
            Run r = run(new SendsTid(), 100, SEED);
            // mutation: a's Tid as its raw id (3: main 1, b 2, a 3)
            assertLines(set(TIDLINE.replace("[\"a\",\"main\"]", "[3,\"main\"]")), r, 100);
        });
    }
}
