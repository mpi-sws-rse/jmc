package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tester tests for J4 round 01 (plan/jmc-must/log/dev/J4/round-01.prompt.md): the A3 lock and A2 store
 * ports run through the launcher in a fresh JVM. Expected numbers (complete and blocked graphs, distinct
 * summaries, summaries with a blocked client, Spec/Impl summary differences) come from the tester's own
 * model of TraceForge's grid.rs (typed channels, per-sender FIFO per channel), written from the Rust
 * before any Java run; see the report for the derivations.
 */
class J4LauncherTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String PKG = "org.mpi_sws.jmc.mp.programs.j4.";
    private static final String DIR = "build/test-results/j4-tester/";

    record Exp(MpLauncherTest.Out out, List<String> lines) {}

    static String file(String row, String strategy) {
        return DIR + row.replace(':', '_').replace(',', '_').replace(' ', '+') + "." + strategy + ".jsonl";
    }

    static Exp export(String row, String strategy, String iterations) throws Exception {
        Path p = Path.of(file(row, strategy));
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PKG + row, strategy, iterations, "1",
                DIR + "report-" + p.getFileName(), p.toString(), "300");
        assertEquals(0, o.exit(), "exit status\n" + o.text());
        assertTrue(!o.text().contains("the format requires") && !o.text().contains("FAILED"), o.text());
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        System.out.println("J4 " + strategy + " " + row + " header " + lines.get(0) + " lines " + (lines.size() - 1));
        return new Exp(o, lines);
    }

    static Exp exportMust(String row) throws Exception {
        return export(row, "must", "0");
    }

    /** The summary lines without their multiplicity {@code n}. */
    static Set<String> summaries(List<String> lines) {
        Set<String> s = new TreeSet<>();
        for (String l : lines.subList(1, lines.size())) {
            s.add(l.replaceAll(",\"n\":[0-9]+}$", "}"));
        }
        return s;
    }

    static String visible(String row) {
        int k = Integer.parseInt(row.split(":")[1].split(",")[1]);
        StringBuilder b = new StringBuilder("\"visible\":[");
        for (int i = 0; i < k; i++) {
            b.append(i == 0 ? "" : ",").append("\"c").append(i).append('"');
        }
        return b.append(']').toString();
    }

    /** The fixture's program name for a row: {@code a3-<kind>-k<k>q<q>}, {@code a2-<kind>-k<k>q<q>[s<s>]}. */
    static String programName(String row) {
        String[] p = row.split(":")[1].split(",");
        String prefix = row.startsWith("A3Lock") ? "a3-" : "a2-";
        return prefix + p[0].trim() + "-k" + p[1] + "q" + p[2] + (p.length > 3 ? "s" + p[3] : "");
    }

    static void checkMust(String row, int complete, int blocked, int distinct, int blockedSummaries)
            throws Exception {
        Exp e = exportMust(row);
        String h = e.lines().get(0);
        assertTrue(h.contains("\"enumeration\":\"complete\""), h);
        assertTrue(h.contains("\"model\":\"p2p\""), h);
        assertTrue(h.contains(visible(row)), "visible roles are the clients: " + h);
        assertTrue(h.contains("\"program\":\"" + programName(row) + "\""),
                "program name " + programName(row) + ": " + h);
        String g = MpLauncherMustTest.field(e.out().text(), "graphs");
        System.out.println("J4 graphs " + row + ": " + g);
        assertEquals(complete + " distinct complete, " + blocked + " distinct blocked; stack exhausted", g, "graphs");
        Set<String> s = summaries(e.lines());
        assertEquals(distinct, s.size(), "distinct summaries");
        assertEquals(blockedSummaries, s.stream().filter(l -> l.contains("\"blocked\"")).count(),
                "summaries with a blocked client");
    }

    static void checkRandom(String row, boolean equal) throws Exception {
        Set<String> must = summaries(exportMust(row).lines());
        Exp r = export(row, "mp-random", "300");
        String h = r.lines().get(0);
        assertTrue(h.contains("\"enumeration\":\"sampled\"") && h.contains("\"seed\":1")
                && h.contains("\"iterations\":300"), h);
        assertTrue(h.contains(visible(row)), h);
        Set<String> random = summaries(r.lines());
        System.out.println("J4 random " + row + ": " + random.size() + " of " + must.size());
        Set<String> extra = new TreeSet<>(random);
        extra.removeAll(must);
        assertEquals(Set.of(), extra, "mp-random summaries outside the must set");
        if (equal) {
            assertEquals(must, random, "sets equal on a small row");
        }
    }

    static void relation(Set<String> spec, String row, int implOnly, int specOnly) throws Exception {
        Set<String> impl = summaries(exportMust(row).lines());
        Set<String> io = new TreeSet<>(impl);
        io.removeAll(spec);
        Set<String> so = new TreeSet<>(spec);
        so.removeAll(impl);
        System.out.println("J4 rel " + row + ": impl " + impl.size() + " spec " + spec.size() + " impl-only "
                + io.size() + " spec-only " + so.size());
        io.forEach(l -> System.out.println("J4 impl-only " + row + " " + l));
        assertEquals(implOnly, io.size(), row + ": impl-only summaries");
        assertEquals(specOnly, so.size(), row + ": spec-only summaries");
    }

    // ------------------------------------------------------------------ item 5: the argument form

    static MpLauncherTest.Out bad(String program) throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, program, "must", "0", "1", DIR + "report-bad");
        System.out.println("J4 arg " + program + " exit " + o.exit() + "\n" + o.text());
        assertEquals(2, o.exit(), o.text());
        return o;
    }

    @Test
    void arg_badKind_exit2() throws Exception {
        assertTrue(bad(PKG + "A3Lock:bogus,2,1").text().contains("unknown A3 kind bogus"));
    }

    @Test
    void arg_missingSize_exit2() throws Exception {
        assertTrue(bad(PKG + "A3Lock:fifo,2").text().contains("A3Lock takes <kind>,<k>,<q>"));
    }

    @Test
    void arg_nonInteger_exit2() throws Exception {
        assertTrue(bad(PKG + "A2Store:pb,x,1").text().contains("NumberFormatException"));
    }

    @Test
    void arg_shardsOnPb_exit2() throws Exception {
        assertTrue(bad(PKG + "A2Store:pb,2,1,2").text().contains("s is a knob of sh only"));
    }

    @Test
    void arg_noStringConstructor_exit2() throws Exception {
        assertTrue(bad("org.mpi_sws.jmc.mp.programs.MustSection13:1").text().contains("cannot load and construct"));
    }

    @Test
    void arg_noColon_defaultFifo21() throws Exception {
        Path p = Path.of(DIR + "nocolon.jsonl");
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PKG + "A3Lock", "must", "0", "1",
                DIR + "report-nocolon", p.toString(), "300");
        assertEquals(0, o.exit(), o.text());
        assertEquals("4 distinct complete, 0 distinct blocked; stack exhausted",
                MpLauncherMustTest.field(o.text(), "graphs"));
        assertTrue(Files.readAllLines(p).get(0).contains("\"program\":\"a3-fifo-k2q1\""));
    }

    // ------------------------------------------------------------------ round 02 (J4-F1, J4-N1)

    @Test
    void arg_leadingSpaceKind_equalsPlain() throws Exception {
        Exp spaced = export("A3Lock: fifo,2,1", "must", "0");
        Exp plain = exportMust("A3Lock:fifo,2,1");
        assertEquals(plain.lines().get(0), spaced.lines().get(0), "same header");
        assertTrue(spaced.lines().get(0).contains("\"program\":\"a3-fifo-k2q1\""), spaced.lines().get(0));
        assertEquals(summaries(plain.lines()), summaries(spaced.lines()), "same summaries");
        assertEquals("4 distinct complete, 0 distinct blocked; stack exhausted",
                MpLauncherMustTest.field(spaced.out().text(), "graphs"));
    }

    @Test
    void arg_misrouteS1_exit2() throws Exception {
        assertTrue(bad(PKG + "A2Store:misroute,1,2,1").text().contains(
                "misroute needs s >= 2 (at s = 1 it routes as sh; the grid runs it at s = 2)"));
    }

    @Test
    void arg_misrouteS2_accepted() throws Exception {
        Exp e = exportMust("A2Store:misroute,1,2,2");
        assertTrue(e.lines().get(0).contains("\"program\":\"a2-misroute-k1q2s2\""), e.lines().get(0));
        assertEquals("1 distinct complete, 0 distinct blocked; stack exhausted",
                MpLauncherMustTest.field(e.out().text(), "graphs"));
    }

    // ------------------------------------------------------------------ items 2-4, generated per row

    @Test
    void must_a3_spec_k2q1() throws Exception {
        checkMust("A3Lock:spec,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a3_spec_k2q1() throws Exception {
        checkRandom("A3Lock:spec,2,1", true);
    }

    @Test
    void must_a3_spec_k3q1() throws Exception {
        checkMust("A3Lock:spec,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a3_spec_k3q1() throws Exception {
        checkRandom("A3Lock:spec,3,1", false);
    }

    @Test
    void must_a3_fifo_k2q1() throws Exception {
        checkMust("A3Lock:fifo,2,1", 4, 0, 2, 0);
    }

    @Test
    void random_a3_fifo_k2q1() throws Exception {
        checkRandom("A3Lock:fifo,2,1", true);
    }

    @Test
    void must_a3_fifo_k3q1() throws Exception {
        checkMust("A3Lock:fifo,3,1", 30, 0, 12, 0);
    }

    @Test
    void random_a3_fifo_k3q1() throws Exception {
        checkRandom("A3Lock:fifo,3,1", false);
    }

    @Test
    void must_a3_controlLifo_k2q1() throws Exception {
        checkMust("A3Lock:control-lifo,2,1", 4, 0, 2, 0);
    }

    @Test
    void random_a3_controlLifo_k2q1() throws Exception {
        checkRandom("A3Lock:control-lifo,2,1", true);
    }

    @Test
    void must_a3_controlLifo_k3q1() throws Exception {
        checkMust("A3Lock:control-lifo,3,1", 30, 0, 12, 0);
    }

    @Test
    void random_a3_controlLifo_k3q1() throws Exception {
        checkRandom("A3Lock:control-lifo,3,1", false);
    }

    @Test
    void must_a3_doublegrant_k2q1() throws Exception {
        checkMust("A3Lock:double-grant,2,1", 6, 0, 4, 0);
    }

    @Test
    void random_a3_doublegrant_k2q1() throws Exception {
        checkRandom("A3Lock:double-grant,2,1", true);
    }

    @Test
    void must_a3_doublegrant_k3q1() throws Exception {
        checkMust("A3Lock:double-grant,3,1", 90, 0, 36, 0);
    }

    @Test
    void random_a3_doublegrant_k3q1() throws Exception {
        checkRandom("A3Lock:double-grant,3,1", false);
    }

    @Test
    void must_a3_nevergrant_k2q1() throws Exception {
        checkMust("A3Lock:never-grant,2,1", 3, 1, 3, 1);
    }

    @Test
    void random_a3_nevergrant_k2q1() throws Exception {
        checkRandom("A3Lock:never-grant,2,1", true);
    }

    @Test
    void must_a3_nevergrant_k3q1() throws Exception {
        checkMust("A3Lock:never-grant,3,1", 18, 10, 14, 6);
    }

    @Test
    void random_a3_nevergrant_k3q1() throws Exception {
        checkRandom("A3Lock:never-grant,3,1", false);
    }

    @Test
    void must_a3_wronground_k2q1() throws Exception {
        checkMust("A3Lock:wrong-round,2,1", 4, 0, 2, 0);
    }

    @Test
    void random_a3_wronground_k2q1() throws Exception {
        checkRandom("A3Lock:wrong-round,2,1", true);
    }

    @Test
    void must_a3_wronground_k3q1() throws Exception {
        checkMust("A3Lock:wrong-round,3,1", 30, 0, 12, 0);
    }

    @Test
    void random_a3_wronground_k3q1() throws Exception {
        checkRandom("A3Lock:wrong-round,3,1", false);
    }

    @Test
    void must_a2_spec_k2q1() throws Exception {
        checkMust("A2Store:spec,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a2_spec_k2q1() throws Exception {
        checkRandom("A2Store:spec,2,1", true);
    }

    @Test
    void must_a2_spec_k1q2() throws Exception {
        checkMust("A2Store:spec,1,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_spec_k1q2() throws Exception {
        checkRandom("A2Store:spec,1,2", true);
    }

    @Test
    void must_a2_spec_k3q1() throws Exception {
        checkMust("A2Store:spec,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a2_spec_k3q1() throws Exception {
        checkRandom("A2Store:spec,3,1", false);
    }

    @Test
    void must_a2_pb_k2q1() throws Exception {
        checkMust("A2Store:pb,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a2_pb_k2q1() throws Exception {
        checkRandom("A2Store:pb,2,1", true);
    }

    @Test
    void must_a2_pb_k1q2() throws Exception {
        checkMust("A2Store:pb,1,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_pb_k1q2() throws Exception {
        checkRandom("A2Store:pb,1,2", true);
    }

    @Test
    void must_a2_pb_k3q1() throws Exception {
        checkMust("A2Store:pb,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a2_pb_k3q1() throws Exception {
        checkRandom("A2Store:pb,3,1", false);
    }

    @Test
    void must_a2_pbbr_k2q1() throws Exception {
        checkMust("A2Store:pb-br,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a2_pbbr_k2q1() throws Exception {
        checkRandom("A2Store:pb-br,2,1", true);
    }

    @Test
    void must_a2_pbbr_k1q2() throws Exception {
        checkMust("A2Store:pb-br,1,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_pbbr_k1q2() throws Exception {
        checkRandom("A2Store:pb-br,1,2", true);
    }

    @Test
    void must_a2_pbbr_k3q1() throws Exception {
        checkMust("A2Store:pb-br,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a2_pbbr_k3q1() throws Exception {
        checkRandom("A2Store:pb-br,3,1", false);
    }

    @Test
    void must_a2_earlyack_k2q1() throws Exception {
        checkMust("A2Store:early-ack,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a2_earlyack_k2q1() throws Exception {
        checkRandom("A2Store:early-ack,2,1", true);
    }

    @Test
    void must_a2_earlyack_k1q2() throws Exception {
        checkMust("A2Store:early-ack,1,2", 2, 0, 2, 0);
    }

    @Test
    void random_a2_earlyack_k1q2() throws Exception {
        checkRandom("A2Store:early-ack,1,2", true);
    }

    @Test
    void must_a2_earlyack_k3q1() throws Exception {
        checkMust("A2Store:early-ack,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a2_earlyack_k3q1() throws Exception {
        checkRandom("A2Store:early-ack,3,1", false);
    }

    @Test
    void must_a2_staleget_k2q1() throws Exception {
        checkMust("A2Store:stale-get,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a2_staleget_k2q1() throws Exception {
        checkRandom("A2Store:stale-get,2,1", true);
    }

    @Test
    void must_a2_staleget_k1q2() throws Exception {
        checkMust("A2Store:stale-get,1,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_staleget_k1q2() throws Exception {
        checkRandom("A2Store:stale-get,1,2", true);
    }

    @Test
    void must_a2_staleget_k3q1() throws Exception {
        checkMust("A2Store:stale-get,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a2_staleget_k3q1() throws Exception {
        checkRandom("A2Store:stale-get,3,1", false);
    }

    @Test
    void must_a2_silentput_k2q1() throws Exception {
        checkMust("A2Store:silent-put,2,1", 0, 2, 2, 2);
    }

    @Test
    void random_a2_silentput_k2q1() throws Exception {
        checkRandom("A2Store:silent-put,2,1", true);
    }

    @Test
    void must_a2_silentput_k1q2() throws Exception {
        checkMust("A2Store:silent-put,1,2", 0, 1, 1, 1);
    }

    @Test
    void random_a2_silentput_k1q2() throws Exception {
        checkRandom("A2Store:silent-put,1,2", true);
    }

    @Test
    void must_a2_silentput_k3q1() throws Exception {
        checkMust("A2Store:silent-put,3,1", 0, 6, 6, 6);
    }

    @Test
    void random_a2_silentput_k3q1() throws Exception {
        checkRandom("A2Store:silent-put,3,1", false);
    }

    @Test
    void must_a2_ctlPrimary_k2q1() throws Exception {
        checkMust("A2Store:control-early-ack-primary,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a2_ctlPrimary_k2q1() throws Exception {
        checkRandom("A2Store:control-early-ack-primary,2,1", true);
    }

    @Test
    void must_a2_ctlPrimary_k1q2() throws Exception {
        checkMust("A2Store:control-early-ack-primary,1,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_ctlPrimary_k1q2() throws Exception {
        checkRandom("A2Store:control-early-ack-primary,1,2", true);
    }

    @Test
    void must_a2_ctlPrimary_k3q1() throws Exception {
        checkMust("A2Store:control-early-ack-primary,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a2_ctlPrimary_k3q1() throws Exception {
        checkRandom("A2Store:control-early-ack-primary,3,1", false);
    }

    @Test
    void must_a2_ctlFwd_k2q1() throws Exception {
        checkMust("A2Store:control-early-ack-fwd,2,1", 2, 0, 2, 0);
    }

    @Test
    void random_a2_ctlFwd_k2q1() throws Exception {
        checkRandom("A2Store:control-early-ack-fwd,2,1", true);
    }

    @Test
    void must_a2_ctlFwd_k1q2() throws Exception {
        checkMust("A2Store:control-early-ack-fwd,1,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_ctlFwd_k1q2() throws Exception {
        checkRandom("A2Store:control-early-ack-fwd,1,2", true);
    }

    @Test
    void must_a2_ctlFwd_k3q1() throws Exception {
        checkMust("A2Store:control-early-ack-fwd,3,1", 6, 0, 6, 0);
    }

    @Test
    void random_a2_ctlFwd_k3q1() throws Exception {
        checkRandom("A2Store:control-early-ack-fwd,3,1", false);
    }

    @Test
    void must_a2_sh_k2q1s2() throws Exception {
        checkMust("A2Store:sh,2,1,2", 2, 0, 2, 0);
    }

    @Test
    void random_a2_sh_k2q1s2() throws Exception {
        checkRandom("A2Store:sh,2,1,2", true);
    }

    @Test
    void must_a2_sh_k1q2s2() throws Exception {
        checkMust("A2Store:sh,1,2,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_sh_k1q2s2() throws Exception {
        checkRandom("A2Store:sh,1,2,2", true);
    }

    @Test
    void must_a2_sh_k3q1s2() throws Exception {
        checkMust("A2Store:sh,3,1,2", 6, 0, 6, 0);
    }

    @Test
    void random_a2_sh_k3q1s2() throws Exception {
        checkRandom("A2Store:sh,3,1,2", false);
    }

    @Test
    void must_a2_misroute_k2q1s2() throws Exception {
        checkMust("A2Store:misroute,2,1,2", 2, 0, 2, 0);
    }

    @Test
    void random_a2_misroute_k2q1s2() throws Exception {
        checkRandom("A2Store:misroute,2,1,2", true);
    }

    @Test
    void must_a2_misroute_k1q2s2() throws Exception {
        checkMust("A2Store:misroute,1,2,2", 1, 0, 1, 0);
    }

    @Test
    void random_a2_misroute_k1q2s2() throws Exception {
        checkRandom("A2Store:misroute,1,2,2", true);
    }

    @Test
    void must_a2_misroute_k3q1s2() throws Exception {
        checkMust("A2Store:misroute,3,1,2", 6, 0, 6, 0);
    }

    @Test
    void random_a2_misroute_k3q1s2() throws Exception {
        checkRandom("A2Store:misroute,3,1,2", false);
    }

    @Test
    void rel_a3_k2q1() throws Exception {
        Set<String> spec = summaries(exportMust("A3Lock:spec,2,1").lines());
        relation(spec, "A3Lock:fifo,2,1", 0, 0);
        relation(spec, "A3Lock:control-lifo,2,1", 0, 0);
        relation(spec, "A3Lock:double-grant,2,1", 2, 0);
        relation(spec, "A3Lock:never-grant,2,1", 1, 0);
        relation(spec, "A3Lock:wrong-round,2,1", 2, 2);
    }

    @Test
    void rel_a3_k3q1() throws Exception {
        Set<String> spec = summaries(exportMust("A3Lock:spec,3,1").lines());
        relation(spec, "A3Lock:fifo,3,1", 6, 0);
        relation(spec, "A3Lock:control-lifo,3,1", 6, 0);
        relation(spec, "A3Lock:double-grant,3,1", 30, 0);
        relation(spec, "A3Lock:never-grant,3,1", 8, 0);
        relation(spec, "A3Lock:wrong-round,3,1", 12, 6);
    }

    @Test
    void rel_a2_k2q1() throws Exception {
        Set<String> spec = summaries(exportMust("A2Store:spec,2,1").lines());
        relation(spec, "A2Store:pb,2,1", 0, 0);
        relation(spec, "A2Store:pb-br,2,1", 0, 0);
        relation(spec, "A2Store:sh,2,1,2", 0, 0);
        relation(spec, "A2Store:early-ack,2,1", 1, 1);
        relation(spec, "A2Store:stale-get,2,1", 1, 1);
        relation(spec, "A2Store:silent-put,2,1", 2, 2);
        relation(spec, "A2Store:misroute,2,1,2", 1, 1);
        relation(spec, "A2Store:control-early-ack-primary,2,1", 0, 0);
        relation(spec, "A2Store:control-early-ack-fwd,2,1", 0, 0);
    }

    @Test
    void rel_a2_k1q2() throws Exception {
        Set<String> spec = summaries(exportMust("A2Store:spec,1,2").lines());
        relation(spec, "A2Store:pb,1,2", 0, 0);
        relation(spec, "A2Store:pb-br,1,2", 0, 0);
        relation(spec, "A2Store:sh,1,2,2", 0, 0);
        relation(spec, "A2Store:early-ack,1,2", 1, 0);
        relation(spec, "A2Store:stale-get,1,2", 1, 1);
        relation(spec, "A2Store:silent-put,1,2", 1, 1);
        relation(spec, "A2Store:misroute,1,2,2", 1, 1);
        relation(spec, "A2Store:control-early-ack-primary,1,2", 0, 0);
        relation(spec, "A2Store:control-early-ack-fwd,1,2", 0, 0);
    }

    @Test
    void rel_a2_k3q1() throws Exception {
        Set<String> spec = summaries(exportMust("A2Store:spec,3,1").lines());
        relation(spec, "A2Store:pb,3,1", 0, 0);
        relation(spec, "A2Store:pb-br,3,1", 0, 0);
        relation(spec, "A2Store:sh,3,1,2", 0, 0);
        relation(spec, "A2Store:early-ack,3,1", 4, 4);
        relation(spec, "A2Store:stale-get,3,1", 3, 3);
        relation(spec, "A2Store:silent-put,3,1", 6, 6);
        relation(spec, "A2Store:misroute,3,1,2", 3, 3);
        relation(spec, "A2Store:control-early-ack-primary,3,1", 0, 0);
        relation(spec, "A2Store:control-early-ack-fwd,3,1", 0, 0);
    }
}
