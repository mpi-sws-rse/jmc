package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.summary.SetIndex;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.mp.summary.Summary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tester tests for J7 round 02 (plan/jmc-must/log/dev/J7/round-02.prompt.md): the launcher's online mode
 * ({@code must ... --spec <file>}) on a subset of J4's 74 rows, both directions, plus its stop semantics,
 * the {@code partial} verdict, the export beside {@code --spec} and the usage errors. Every expected
 * verdict, answer class and uncovered summary comes from the tester's derivation
 * (plan/jmc-must/log/dev/J7/derivations.md §3 and scratchpad j7/t2/uncovered.txt: the J4 typed-channel
 * model with TraceForge's covers), never from a run.
 *
 * <p>Each specification file is JMC's own complete export of the specification side, made in the same
 * method by a plain {@code must} export run (J4 showed these exports equal the model's sets and
 * byte-identical to {@code plan/jmc-must/log/runs/J4}); the tests therefore do not read the plan tree.
 *
 * <p>OL round 05 (MESSAGE-online-mode.md §1): the online check now runs in <em>full</em> mode by default
 * (continue past a miss, count every uncovered graph) and in <em>bug-finder</em> mode with
 * {@code --stop-at-first}. Verdicts do not depend on the mode. Cases that assert the stop at the first miss
 * ({@link #reports}, the stop/export/not-exhausted cases) run in bug-finder mode ({@code forwardStop},
 * {@code reverseStop}); conforming and partial cases run in the default full mode and also assert
 * {@code uncovered: 0 graphs (0 distinct summaries)} and no {@code first miss:} line. The first uncovered
 * summary is now printed as {@code witness:} (was {@code uncovered:}).
 */
class J7OnlineTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String PKG = "org.mpi_sws.jmc.mp.programs.j4.";
    private static final String DIR = "build/test-results/j7-tester/";
    private static final String TIMEOUT = "300";

    /** {@code uncovered} is the witness line (the first uncovered summary); {@code count} the uncovered-count line. */
    record Run(MpLauncherTest.Out out, String verdict, String at, String uncovered, String lookups, String ran,
            String graphs, String count, String mode, String firstMiss) {}

    static String slug(String row) {
        return row.replace(':', '-').replace(',', '-').replace(' ', '+');
    }

    /** JMC's complete must export of {@code row}, written fresh. */
    static Path export(String row) throws Exception {
        Path p = Path.of(DIR + slug(row) + ".must.jsonl");
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PKG + row, "must", "0", "1",
                DIR + "report-" + slug(row), p.toString(), TIMEOUT);
        assertEquals(0, o.exit(), o.text());
        String h = Files.readAllLines(p, StandardCharsets.UTF_8).get(0);
        assertTrue(h.contains("\"enumeration\":\"complete\""), h);
        return p;
    }

    static String field(String text, String key) {
        Matcher m = Pattern.compile("(?m)^" + key + ":\\s+(.*)$").matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    static Run parse(MpLauncherTest.Out o) {
        String t = o.text();
        return new Run(o, field(t, "verdict"), field(t, "at"), field(t, "witness"), field(t, "lookups"),
                field(t, "ran"), field(t, "graphs"), field(t, "uncovered"), field(t, "mode"), field(t, "first miss"));
    }

    /** {@code impl} under must against {@code spec}; extra positional/option arguments replace the default tail. */
    static Run online(String impl, Path spec, String... tail) throws Exception {
        return online(false, impl, spec, tail);
    }

    /** As {@link #online(String, Path, String...)}; {@code stopAtFirst} adds {@code --stop-at-first} to the default tail. */
    static Run online(boolean stopAtFirst, String impl, Path spec, String... tail) throws Exception {
        List<String> args = new ArrayList<>(List.of(PKG + impl));
        if (tail.length == 0) {
            args.addAll(List.of("must", "0", "1", DIR + "report-online-" + slug(impl), "-", TIMEOUT, "--spec",
                    spec.toString()));
            if (stopAtFirst) {
                args.add("--stop-at-first");
            }
        } else {
            args.addAll(Arrays.asList(tail));
        }
        Run r = parse(MpLauncherTest.launch(LAUNCHER, args.toArray(new String[0])));
        assertEquals(0, r.out().exit(), r.out().text());
        return r;
    }

    static String specRow(String row) {
        String[] p = row.split(":")[1].split(",");
        return row.split(":")[0] + ":spec," + p[1] + "," + p[2];
    }

    static Run forward(String row) throws Exception {
        return online(row, export(specRow(row)));
    }

    static Run reverse(String row) throws Exception {
        return online(specRow(row), export(row));
    }

    /** {@link #forward} in bug-finder mode. */
    static Run forwardStop(String row) throws Exception {
        return online(true, row, export(specRow(row)));
    }

    /** {@link #reverse} in bug-finder mode. */
    static Run reverseStop(String row) throws Exception {
        return online(true, specRow(row), export(row));
    }

    static void conforms(Run r, long lookups) {
        assertEquals("conforms", r.verdict(), r.out().text());
        assertEquals(lookups + " (" + lookups + " found, 0 signature misses, 0 not found)", r.lookups(),
                r.out().text());
        assertTrue(r.graphs().endsWith("stack exhausted"), r.graphs());
        assertEquals(null, r.uncovered(), "no witness");
        assertEquals("0 graphs (0 distinct summaries)", r.count(), r.out().text());
        assertEquals(null, r.firstMiss(), "no first-miss line");
    }

    /**
     * A report: the answer class, the uncovered summary one of the derived ones, the stop right after its
     * iteration ({@code ran:} equals {@code at:}'s iteration; every iteration before it was queried and
     * covered; {@code graphs:} exhausted iff the uncovered graph was the last of the row's {@code graphs}).
     */
    static void reports(Run r, int graphs, String answer, String... derivedUncovered) {
        String t = r.out().text();
        assertEquals("reports", r.verdict(), t);
        assertEquals("bug-finder (--stop-at-first)", r.mode(), t);
        assertEquals("1 graphs (1 distinct summaries)", r.count(), "bug-finder: the run stops at the first miss");
        assertTrue(r.at() != null && r.at().matches("iteration [0-9]+, " + answer + " .*"), "at: " + r.at());
        int at = Integer.parseInt(r.at().split("[ ,]")[1]);
        assertEquals(at + " iterations", r.ran(), t);
        boolean noSig = answer.equals("NoSig");
        assertEquals(at + " (" + (at - 1) + " found, " + (noSig ? 1 : 0) + " signature misses, " + (noSig ? 0 : 1)
                + " not found)", r.lookups(), t);
        // Round-02 fix of the exhausted nit: the stack is empty at the stop iff the uncovered graph was the
        // row's last one (derived: at == the row's graph count).
        assertTrue(r.graphs().endsWith(at == graphs ? "stack exhausted" : "stack not exhausted"),
                "at " + at + " of " + graphs + " graphs: " + r.graphs());
        assertTrue(Set.of(derivedUncovered).contains(r.uncovered()), "uncovered: " + r.uncovered());
        List<String> vis = SpecificationFile.parseLine(r.uncovered(), roles(r.uncovered())).roles();
        assertEquals(roles(r.uncovered()), vis);
    }

    static List<String> roles(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"(c[0-9]+)\":\"(done|blocked|err)\"").matcher(line);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    // ---------------------------------------------------------------- derived uncovered summaries

    private static final String A3W = "\"words\":{\"c0\":[{\"s\":{\"t\":\"Acq\",\"f\":[0]}},{\"r\":{\"t\":\"Gran"
            + "t\",\"f\":[0,0]}},"
            + "{\"s\":{\"t\":\"Rel\",\"f\":[0]}}],\"c1\":[{\"s\":{\"t\":\"Acq\",\"f\":[1]}},{\"r\":{\"t\":\"Gran"
            + "t\",\"f\":[1,0]}},"
            + "{\"s\":{\"t\":\"Rel\",\"f\":[1]}}]}";
    private static final String A3WR = A3W.replace("\"f\":[1,0]", "\"f\":[1,1]");
    private static final String D2 = "{\"statuses\":{\"c0\":\"done\",\"c1\":\"done\"},";
    private static final String A2W21 = "\"words\":{\"c0\":[{\"s\":{\"t\":\"Put\",\"f\":[0,0,0]}},{\"r\":{\"t\":\"Ac"
            + "k\",\"f\":[]}}],"
            + "\"c1\":[{\"s\":{\"t\":\"Get\",\"f\":[1,0]}},{\"r\":";
    private static final String NONE = "{\"t\":\"None\",\"f\":[]}";
    private static final String VAL0 = "{\"t\":\"Val\",\"f\":[0]}";
    private static final String A2W12 = "{\"statuses\":{\"c0\":\"done\"},\"words\":{\"c0\":[{\"s\":{\"t\":\"Pu"
            + "t\",\"f\":[0,0,0]}},"
            + "{\"r\":{\"t\":\"Ack\",\"f\":[]}},{\"s\":{\"t\":\"Get\",\"f\":[0,0]}},{\"r\":";

    static String a3Line(String head, String words, String counts) {
        return head + words + ",\"counts\":[" + counts + "],\"n\":1}";
    }

    static String a2l21(String recv, String counts) {
        return D2 + A2W21 + recv + "}]},\"counts\":[" + counts + "],\"n\":1}";
    }

    static String a2l12(String recv) {
        return A2W12 + recv + "}]},\"counts\":[],\"n\":1}";
    }

    static final String SILENT_PUT_11 =
            "{\"statuses\":{\"c0\":\"blocked\"},\"words\":{\"c0\":[{\"s\":{\"t\":\"Put\",\"f\":[0,0,0]}}]},\"count"
                    + "s\":[],\"n\":1}";
    static final String SPEC_A2_11 = "{\"statuses\":{\"c0\":\"done\"},\"words\":{\"c0\":[{\"s\":{\"t\":\"Pu"
            + "t\",\"f\":[0,0,0]}},"
            + "{\"r\":{\"t\":\"Ack\",\"f\":[]}}]},\"counts\":[],\"n\":1}";
    static final String NG21 = "{\"statuses\":{\"c0\":\"done\",\"c1\":\"blocked\"},\"words\":{\"c0\":[{\"s\":"
            + "{\"t\":\"Acq\",\"f\":[0]}},"
            + "{\"r\":{\"t\":\"Grant\",\"f\":[0,0]}},{\"s\":{\"t\":\"Rel\",\"f\":[0]}}],\"c1\":[{\"s\":{\"t\":\"Ac"
            + "q\",\"f\":[1]}}]},"
            + "\"counts\":[0,0,0,0],\"n\":1}";
    private static final String NG3_ACQ = "{\"s\":{\"t\":\"Acq\",\"f\":[%d]}}";
    private static final String NG3_FULL = "{\"s\":{\"t\":\"Acq\",\"f\":[%1$d]}},{\"r\":{\"t\":\"Grant\",\"f\":[%1$d"
            + ",0]}},"
            + "{\"s\":{\"t\":\"Rel\",\"f\":[%1$d]}}";

    static String ng31(String s0, String s1, String counts) {
        String w0 = String.format(s0.equals("done") ? NG3_FULL : NG3_ACQ, 0);
        String w1 = String.format(s1.equals("done") ? NG3_FULL : NG3_ACQ, 1);
        return "{\"statuses\":{\"c0\":\"" + s0 + "\",\"c1\":\"" + s1 + "\",\"c2\":\"blocked\"},\"words\":{\"c0\":[" + w0
                + "],\"c1\":[" + w1 + "],\"c2\":[" + String.format(NG3_ACQ,
                2) + "]},\"counts\":[" + counts + "],\"n\":1}";
    }

    static final String[] NG31 = {
        ng31("blocked", "done", "0,0,0,0,0,0,0,0,0,0"),
        ng31("done", "blocked", "0,0,0,0,0,0,0,0,0,0"),
        ng31("done", "done", "0,0,0,0,0,0,0,0,3,0,3,0,0,0"),
        ng31("done", "done", "0,0,0,0,0,0,0,0,3,1,3,1,0,0"),
        ng31("done", "done", "0,0,3,0,3,0,0,0,0,0,0,0,0,0"),
        ng31("done", "done", "0,0,3,1,3,1,0,0,0,0,0,0,0,0"),
    };

    // ---------------------------------------------------------------- forward (row against its spec)

    @Test
    void fwd_a3_spec_k2q1_ownExport() throws Exception {
        conforms(forward("A3Lock:spec,2,1"), 2);
    }

    @Test
    void fwd_a2_spec_k2q3_ownExport() throws Exception {
        conforms(forward("A2Store:spec,2,3"), 20);
    }

    @Test
    void fwd_a3_fifo_k2q1() throws Exception {
        conforms(forward("A3Lock:fifo,2,1"), 4);
    }

    @Test
    void fwd_a3_fifo_k3q1_scanPath() throws Exception {
        conforms(forward("A3Lock:fifo,3,1"), 30);
    }

    @Test
    void fwd_a3_controlLifo_k3q1_scanPath() throws Exception {
        conforms(forward("A3Lock:control-lifo,3,1"), 30);
    }

    @Test
    void fwd_a3_doubleGrant_k2q1_notFound() throws Exception {
        reports(forwardStop("A3Lock:double-grant,2,1"), 6, "NotFound", a3Line(D2, A3W, "0,0,0,0,1,1"), a3Line(D2, A3W,
                "0,1,1,0,0,0"));
    }

    @Test
    void fwd_a3_wrongRound_k2q1_noSig() throws Exception {
        reports(forwardStop("A3Lock:wrong-round,2,1"), 4, "NoSig", a3Line(D2, A3WR, "0,0,0,0,3,3"), a3Line(D2, A3WR,
                "0,3,3,0,0,0"));
    }

    @Test
    void fwd_a3_neverGrant_k2q1_blockedSummaryQueried() throws Exception {
        reports(forwardStop("A3Lock:never-grant,2,1"), 4, "NoSig", NG21);
    }

    @Test
    void fwd_a3_neverGrant_k3q1_scanPathRow() throws Exception {
        reports(forwardStop("A3Lock:never-grant,3,1"), 28, "NoSig", NG31);
    }

    @Test
    void fwd_a2_pb_k2q1() throws Exception {
        conforms(forward("A2Store:pb,2,1"), 2);
    }

    @Test
    void fwd_a2_pbBr_k3q1() throws Exception {
        conforms(forward("A2Store:pb-br,3,1"), 6);
    }

    @Test
    void fwd_a2_sh_k2q1s2() throws Exception {
        conforms(forward("A2Store:sh,2,1,2"), 2);
    }

    @Test
    void fwd_a2_earlyAck_k2q1_notFound() throws Exception {
        reports(forwardStop("A2Store:early-ack,2,1"), 2, "NotFound", a2l21(NONE, "0,0,0,0"));
    }

    @Test
    void fwd_a2_earlyAck_k1q2_noSig() throws Exception {
        reports(forwardStop("A2Store:early-ack,1,2"), 2, "NoSig", a2l12(NONE));
    }

    @Test
    void fwd_a2_staleGet_k2q1_notFound() throws Exception {
        reports(forwardStop("A2Store:stale-get,2,1"), 2, "NotFound", a2l21(NONE, "0,0,0,1"));
    }

    @Test
    void fwd_a2_silentPut_k1q1_blockedSummaryQueried() throws Exception {
        Run r = forwardStop("A2Store:silent-put,1,1");
        reports(r, 1, "NoSig", SILENT_PUT_11);
        assertEquals("iteration 1", r.at().split(",")[0]);
    }

    @Test
    void fwd_a2_misroute_k1q2s2_noSig() throws Exception {
        reports(forwardStop("A2Store:misroute,1,2,2"), 1, "NoSig", a2l12(NONE));
    }

    @Test
    void fwd_a2_controlEarlyAckPrimary_k2q1() throws Exception {
        conforms(forward("A2Store:control-early-ack-primary,2,1"), 2);
    }

    @Test
    void fwd_a2_controlEarlyAckFwd_k3q1() throws Exception {
        conforms(forward("A2Store:control-early-ack-fwd,3,1"), 6);
    }

    // ---------------------------------------------------------------- reverse (spec program against the row)

    @Test
    void rev_a3_fifo_k2q1() throws Exception {
        conforms(reverse("A3Lock:fifo,2,1"), 2);
    }

    @Test
    void rev_a3_controlLifo_k2q1() throws Exception {
        conforms(reverse("A3Lock:control-lifo,2,1"), 2);
    }

    @Test
    void rev_a3_doubleGrant_k2q1() throws Exception {
        conforms(reverse("A3Lock:double-grant,2,1"), 2);
    }

    @Test
    void rev_a3_wrongRound_k2q1_noSig() throws Exception {
        reports(reverseStop("A3Lock:wrong-round,2,1"), 2, "NoSig", a3Line(D2, A3W, "0,0,0,0,3,3"), a3Line(D2, A3W,
                "0,3,3,0,0,0"));
    }

    @Test
    void rev_a3_neverGrant_k2q1() throws Exception {
        conforms(reverse("A3Lock:never-grant,2,1"), 2);
    }

    @Test
    void rev_a2_pb_k2q1() throws Exception {
        conforms(reverse("A2Store:pb,2,1"), 2);
    }

    @Test
    void rev_a2_pbBr_k3q1() throws Exception {
        conforms(reverse("A2Store:pb-br,3,1"), 6);
    }

    @Test
    void rev_a2_sh_k2q1s2() throws Exception {
        conforms(reverse("A2Store:sh,2,1,2"), 2);
    }

    @Test
    void rev_a2_earlyAck_k2q1_scanPath() throws Exception {
        conforms(reverse("A2Store:early-ack,2,1"), 2);
    }

    @Test
    void rev_a2_earlyAck_k3q1_scanPath() throws Exception {
        conforms(reverse("A2Store:early-ack,3,1"), 6);
    }

    @Test
    void rev_a2_earlyAck_k2q2_scanPath() throws Exception {
        conforms(reverse("A2Store:early-ack,2,2"), 6);
    }

    @Test
    void rev_a2_earlyAck_k2q3_scanPath() throws Exception {
        conforms(reverse("A2Store:early-ack,2,3"), 20);
    }

    @Test
    void rev_a2_staleGet_k2q1_noSig() throws Exception {
        reports(reverseStop("A2Store:stale-get,2,1"), 2, "NoSig", a2l21(VAL0, "0,0,0,1"));
    }

    @Test
    void rev_a2_silentPut_k1q1_noSig() throws Exception {
        reports(reverseStop("A2Store:silent-put,1,1"), 1, "NoSig", SPEC_A2_11);
    }

    @Test
    void rev_a2_misroute_k1q2s2_noSig() throws Exception {
        reports(reverseStop("A2Store:misroute,1,2,2"), 1, "NoSig", a2l12(VAL0));
    }

    @Test
    void rev_a2_controlEarlyAckPrimary_k2q1() throws Exception {
        conforms(reverse("A2Store:control-early-ack-primary,2,1"), 2);
    }

    @Test
    void rev_a2_controlEarlyAckFwd_k3q1() throws Exception {
        conforms(reverse("A2Store:control-early-ack-fwd,3,1"), 6);
    }

    // ---------------------------------------------------------------- stop semantics, partial, export

    /** Every graph of wrong-round (3,1) is uncovered: the run must stop after iteration 1 of 30. */
    @Test
    void stop_reportsAfterFirstIteration_wrongRound_k3q1() throws Exception {
        Run r = forwardStop("A3Lock:wrong-round,3,1");
        assertEquals("reports", r.verdict());
        assertTrue(r.at().startsWith("iteration 1, NoSig"), r.at());
        assertEquals("1 iterations", r.ran());
        assertEquals("1 (0 found, 1 signature misses, 0 not found)", r.lookups());
    }

    /** A spec of 90 graphs against itself: every lookup an exact hit, 90 lookups, exhausted. */
    @Test
    void stop_conformsLookupsEqualGraphs_a2spec_k3q2() throws Exception {
        Run r = forward("A2Store:spec,3,2");
        conforms(r, 90);
        assertEquals("90 distinct complete, 0 distinct blocked; stack exhausted", r.graphs());
    }

    /** Bounded at one iteration with nothing uncovered (every fifo (2,1) graph is covered): partial, exit 0. */
    @Test
    void partial_boundedRun() throws Exception {
        Path spec = export("A3Lock:spec,2,1");
        Run r = online("A3Lock:fifo,2,1", spec, "must", "1", "1", DIR + "report-partial", "-", TIMEOUT, "--spec",
                spec.toString());
        assertEquals("partial", r.verdict(), r.out().text());
        assertEquals("1 (1 found, 0 signature misses, 0 not found)", r.lookups());
        assertEquals(null, r.uncovered());
        assertEquals("0 graphs (0 distinct summaries)", r.count());
    }

    /**
     * --spec with an export file, conforms: the summary lines are byte-identical to a plain export of the row;
     * the header is the plain header with the online check's three fields (§1) before {@code encoding}:
     * {@code "first_miss_iteration":0,"first_miss_wall_ms":0,"uncovered_total":0} (no miss).
     */
    @Test
    void export_besideSpec_conforms_identical() throws Exception {
        Path spec = export("A3Lock:spec,3,1");
        Path plain = export("A3Lock:fifo,3,1");
        Path withSpec = Path.of(DIR + "fifo31-with-spec.jsonl");
        Files.deleteIfExists(withSpec);
        Run r = online("A3Lock:fifo,3,1", spec, "must", "0", "1", DIR + "report-exp", withSpec.toString(), TIMEOUT,
                "--spec", spec.toString());
        assertEquals("conforms", r.verdict());
        List<String> pl = Files.readAllLines(plain, StandardCharsets.UTF_8);
        List<String> wl = Files.readAllLines(withSpec, StandardCharsets.UTF_8);
        assertEquals(pl.subList(1, pl.size()), wl.subList(1, wl.size()), "summary lines identical");
        assertEquals(pl.get(0).replace(",\"encoding\":1}",
                ",\"first_miss_iteration\":0,\"first_miss_wall_ms\":0,\"uncovered_total\":0,\"encoding\":1}"), wl.get(0));
    }

    /** --spec with an export file, reports, bug-finder mode: the run stopped early, so the export is not complete. */
    @Test
    void export_besideSpec_reports_notComplete() throws Exception {
        Path spec = export("A3Lock:spec,3,1");
        Path withSpec = Path.of(DIR + "wr31-with-spec.jsonl");
        Files.deleteIfExists(withSpec);
        Run r = online("A3Lock:wrong-round,3,1", spec, "must", "0", "1", DIR + "report-exp2", withSpec.toString(),
                TIMEOUT, "--spec", spec.toString(), "--stop-at-first");
        assertEquals("reports", r.verdict());
        List<String> lines = Files.readAllLines(withSpec, StandardCharsets.UTF_8);
        System.out.println("J7 export header beside a report: " + lines.get(0));
        assertTrue(lines.get(0).contains("\"enumeration\":\"bounded\""), lines.get(0));
        assertTrue(lines.get(0).contains(",\"first_miss_iteration\":1,"), lines.get(0));
        assertTrue(lines.get(0).contains(",\"uncovered_total\":1,"), lines.get(0));
        assertEquals(2, lines.size(), "one summary line (the uncovered one)");
        assertEquals(r.uncovered(), lines.get(1));
    }

    @Test
    void specOption_inTheMiddle() throws Exception {
        Path spec = export("A3Lock:spec,2,1");
        Run r = online("A3Lock:fifo,2,1", spec, "--spec", spec.toString(), "must", "0", "1", DIR + "report-mid", "-",
                TIMEOUT);
        conforms(r, 4);
    }

    @Test
    void specOption_first() throws Exception {
        Path spec = export("A3Lock:spec,2,1");
        Run r = parse(MpLauncherTest.launch(LAUNCHER, "--spec", spec.toString(), PKG + "A3Lock:fifo,2,1", "must", "0",
                "1", DIR + "report-first", "-", TIMEOUT));
        assertEquals(0, r.out().exit(), r.out().text());
        conforms(r, 4);
    }

    // ---------------------------------------------------------------- usage errors (exit 2)

    static void exit2(String expect, String... args) throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, args);
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.text().contains(expect), "'" + expect + "' in\n" + o.text());
        assertTrue(!o.text().contains("verdict:"), o.text());
    }

    @Test
    void usage_specWithoutFile() throws Exception {
        exit2("--spec needs a file", PKG + "A3Lock:fifo,2,1", "must", "0", "1", DIR + "r", "-", TIMEOUT, "--spec");
    }

    @Test
    void usage_specTwice() throws Exception {
        Path spec = export("A3Lock:spec,2,1");
        exit2("twice", PKG + "A3Lock:fifo,2,1", "must", "0", "1", DIR + "r", "-", TIMEOUT, "--spec", spec.toString(),
                "--spec", spec.toString());
    }

    @Test
    void usage_specWithMpRandom() throws Exception {
        Path spec = export("A3Lock:spec,2,1");
        exit2("needs strategy must", PKG + "A3Lock:fifo,2,1", "mp-random", "5", "1", DIR + "r", "-", TIMEOUT, "--spec",
                spec.toString());
    }

    @Test
    void usage_specWithNonMpProgram() throws Exception {
        Path spec = export("A3Lock:spec,2,1");
        exit2("MpProgram", "org.mpi_sws.jmc.mp.programs.mutants.T2NoRecv", "must", "0", "1", DIR + "r", "-", TIMEOUT,
                "--spec", spec.toString());
    }

    @Test
    void usage_sampledSpecFile() throws Exception {
        Path sampled = Path.of(DIR + "spec21.random.jsonl");
        Files.deleteIfExists(sampled);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PKG + "A3Lock:spec,2,1", "mp-random", "20", "1",
                DIR + "report-sampled", sampled.toString());
        assertEquals(0, o.exit(), o.text());
        exit2("not complete", PKG + "A3Lock:fifo,2,1", "must", "0", "1", DIR + "r", "-", TIMEOUT, "--spec",
                sampled.toString());
    }

    @Test
    void usage_visibleMismatch() throws Exception {
        Path spec = export("A3Lock:spec,2,1");
        exit2("visible roles", PKG + "A2Store:pb,1,1", "must", "0", "1", DIR + "r", "-", TIMEOUT, "--spec",
                spec.toString());
    }

    @Test
    void usage_missingSpecFile() throws Exception {
        exit2("refused", PKG + "A3Lock:fifo,2,1", "must", "0", "1", DIR + "r", "-", TIMEOUT, "--spec",
                DIR + "no-such-file.jsonl");
    }

    // ---------------------------------------------------------------- JMC's own export re-renders identically

    static void ownExportRerenders(String row) throws Exception {
        Path p = export(row);
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        String h = lines.get(0);
        List<String> vis = roles(lines.get(1));
        SpecificationFile.Loaded loaded = SpecificationFile.load(p, vis);
        assertEquals(lines.size() - 1, loaded.index().size());
        assertEquals(0, loaded.duplicates());
        for (String line : lines.subList(1, lines.size())) {
            Summary s = SpecificationFile.parseLine(line, vis);
            int n = Integer.parseInt(line.replaceAll(".*,\"n\":([0-9]+)}$", "$1"));
            assertEquals(line, s.toJson(n), "re-rendered byte-identically");
            SetIndex.Covers c = loaded.index().covers(s);
            assertInstanceOf(SetIndex.Covers.Found.class, c);
            assertTrue(((SetIndex.Covers.Found) c).exact(), line);
        }
        assertEquals(lines.size() - 1, loaded.index().stats().exactHits());
        if (!h.contains("\"enumeration\":\"complete\"")) {
            fail(h);
        }
    }

    @Test
    void ownExport_a3spec_k3q1_rerendersIdentically() throws Exception {
        ownExportRerenders("A3Lock:spec,3,1");
    }

    @Test
    void ownExport_a2spec_k2q3_rerendersIdentically() throws Exception {
        ownExportRerenders("A2Store:spec,2,3");
    }

    // ---------------------------------------------------------------- the exhausted nit (fixed after the pause)

    /** silent-put (1,1) has one graph: the stop finds the stack empty, so exhausted, and the export is complete. */
    @Test
    void nit_silentPut11_exhausted_exportComplete() throws Exception {
        Path spec = export("A2Store:spec,1,1");
        Path withSpec = Path.of(DIR + "sp11-with-spec.jsonl");
        Files.deleteIfExists(withSpec);
        Run r = online("A2Store:silent-put,1,1", spec, "must", "0", "1", DIR + "report-nit1", withSpec.toString(),
                TIMEOUT, "--spec", spec.toString());
        assertEquals("reports", r.verdict());
        assertEquals("0 distinct complete, 1 distinct blocked; stack exhausted", r.graphs());
        List<String> lines = Files.readAllLines(withSpec, StandardCharsets.UTF_8);
        assertTrue(lines.get(0).contains("\"enumeration\":\"complete\""), lines.get(0));
        assertEquals(List.of(SILENT_PUT_11), lines.subList(1, lines.size()));
    }

    /** double-grant (3,1): 90 graphs, a NotFound report long before the last; still not exhausted (bug-finder). */
    @Test
    void nit_doubleGrant31_notExhausted() throws Exception {
        Run r = forwardStop("A3Lock:double-grant,3,1");
        assertEquals("reports", r.verdict());
        assertTrue(r.at().matches("iteration [0-9]+, NotFound .*"), r.at());
        int at = Integer.parseInt(r.at().split("[ ,]")[1]);
        assertTrue(at < 90, r.at());
        assertTrue(r.graphs().endsWith("stack not exhausted"), r.graphs());
    }

    // ---------------------------------------------------------------- Codex J7 m2 (round 03): the model is printed

    /** A p2p program against its own export relabelled asyn: printed, not compared; coverage decides. */
    @Test
    void m2_asynModelPrintedAndAccepted() throws Exception {
        Path own = export("A3Lock:spec,2,1");
        List<String> lines = new ArrayList<>(Files.readAllLines(own, StandardCharsets.UTF_8));
        assertTrue(lines.get(0).contains("\"model\":\"p2p\""), lines.get(0));
        lines.set(0, lines.get(0).replace("\"model\":\"p2p\"", "\"model\":\"asyn\""));
        Path asyn = Path.of(DIR + "a3-spec-k2q1.asyn.jsonl");
        Files.write(asyn, lines, StandardCharsets.UTF_8);
        Run r = online("A3Lock:spec,2,1", asyn);
        String spec = field(r.out().text(), "spec");
        assertTrue(spec != null && spec.contains("model asyn"), "spec: " + spec);
        conforms(r, 2);
    }

    /** The unmodified p2p export prints model p2p. */
    @Test
    void m2_p2pModelPrinted() throws Exception {
        Run r = online("A3Lock:spec,2,1", export("A3Lock:spec,2,1"));
        String spec = field(r.out().text(), "spec");
        assertTrue(spec != null && spec.contains("model p2p"), "spec: " + spec);
        conforms(r, 2);
    }
}
