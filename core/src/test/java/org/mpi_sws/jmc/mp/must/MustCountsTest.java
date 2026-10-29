package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.programs.MustSection13;
import org.mpi_sws.jmc.mp.programs.j1.BlockedAlone;
import org.mpi_sws.jmc.mp.programs.j1.SRnb;
import org.mpi_sws.jmc.mp.programs.j2.DriftStructure;
import org.mpi_sws.jmc.mp.programs.j2.DriftValue;
import org.mpi_sws.jmc.mp.programs.j2.NNR;
import org.mpi_sws.jmc.mp.programs.j2.NSR;
import org.mpi_sws.jmc.mp.programs.j2.NSRNSel;
import org.mpi_sws.jmc.mp.programs.j2.NSRNVariant;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.programs.j2.NondetK;
import org.mpi_sws.jmc.mp.programs.j2.R3EarlyNondet;
import org.mpi_sws.jmc.mp.programs.j2.R3Guard;
import org.mpi_sws.jmc.mp.programs.j2.R3Model;
import org.mpi_sws.jmc.mp.programs.j2.R3SendThenNondet;
import org.mpi_sws.jmc.mp.programs.j2.R3SkipSpawn;
import org.mpi_sws.jmc.mp.programs.j2.SSR;
import org.mpi_sws.jmc.mp.programs.j2.SSRBR;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.strategies.SchedulingStrategyFactory;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Map;

/**
 * Tester tests for J2 round 01 (plan/jmc-must/log/dev/J2/round-01.prompt.md): distinct graph counts
 * under {@code must}, derived from the Must paper's examples and the litmus table (state.md §3.4)
 * before reading the strategy. Every count test also asserts optimality
 * ({@code iterations == distinct complete + distinct blocked}) and exhaustion. Each
 * {@code *_detects_*} method requires the same check to fail on a mutated program or number.
 */
class MustCountsTest {

    record Run(MustStrategy s, JmcModelCheckerReport report, Throwable thrown) {}

    static Run run(MpProgram p, long seed, boolean export) throws Exception {
        String path = "build/test-results/j2-tester/" + p.getClass().getSimpleName();
        MustStrategy s = export
                ? new MustStrategy(seed, path, p.rolesInSpawnOrder(), p.visibleRoles())
                : new MustStrategy(seed, path);
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("must")
                .strategyConstructor(c -> s)
                .numIterations(10000)
                .seed(seed)
                .reportPath(path)
                .build();
        JmcModelCheckerReport report = null;
        Throwable thrown = null;
        try {
            report = new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
        } catch (Throwable t) {
            thrown = t;
        }
        System.out.println("MUST " + p.getClass().getSimpleName() + " " + p.size() + " seed=" + seed
                + " complete=" + s.distinctCompleteGraphs() + " blocked=" + s.distinctBlockedGraphs()
                + " iterations=" + s.iterations() + " exhausted=" + s.exhausted()
                + " reportBlocked=" + (report == null ? "-" : report.getBlockedIterations())
                + " thrown=" + (thrown == null ? "none" : thrown.getClass().getSimpleName() + ": " + thrown.getMessage()));
        return new Run(s, report, thrown);
    }

    static Run count(MpProgram p, int complete, int blocked) throws Exception {
        Run r = run(p, 1L, false);
        assertEquals(null, r.thrown(), "the run threw");
        assertEquals(complete, r.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(blocked, r.s().distinctBlockedGraphs(), "distinct blocked graphs");
        assertEquals(r.s().distinctCompleteGraphs() + r.s().distinctBlockedGraphs(), r.s().iterations(),
                "optimality: iterations == distinct complete + distinct blocked");
        assertTrue(r.s().exhausted(), "exhausted");
        return r;
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

    // ------------------------------------------------------------------ C1 litmus counts

    @Test
    void c1_nsr3_p2p_3() throws Exception {
        count(new NSR(3), 3, 0);
    }

    @Test
    void c1_detects_nsr2_has2() throws Exception {
        count(new NSR(2), 2, 0);
        mutant("C1 NS+R N=2 expected 3", () -> count(new NSR(2), 3, 0));
    }

    @Test
    void c1_detects_nsr3_mutatedNumber6() {
        mutant("C1 NS+R eager 6", () -> count(new NSR(3), 6, 0));
    }

    @Test
    void c1_nnr3_asyn_1() throws Exception {
        count(new NNR(3), 1, 0);
    }

    @Test
    void c1_detects_nnr3_naive8() {
        mutant("C1 NNR naive 2^N", () -> count(new NNR(3), 8, 0));
    }

    @Test
    void c1_ssr_p2p_2() throws Exception {
        count(new SSR(2, 1, true), 2, 0);
    }

    @Test
    void c1_detects_ssr_oneSender_has1() throws Exception {
        count(new SSR(2, 1, false), 1, 0);
        mutant("C1 S+S+R one sender expected 2", () -> count(new SSR(2, 1, false), 2, 0));
    }

    @Test
    void c1_ssr_twoReceives_2() throws Exception {
        // derived: r reads 1 then 2, or 2 then 1 (distinct senders, p2p) -> 2
        count(new SSR(2, 2, true), 2, 0);
    }

    @Test
    void c1_srnb_asyn_2() throws Exception {
        count(new SRnb(), 2, 0);
    }

    @Test
    void c1_detects_srnb_mutatedNumber1() {
        mutant("C1 S+R^nb expected 1", () -> count(new SRnb(), 1, 0));
    }

    @Test
    void c1_mustSection13_p2p_2() throws Exception {
        count(new MustSection13(), 2, 0);
    }

    @Test
    void c1_detects_mustSection13_cdCount1() {
        mutant("C1 §1.3 expected 1 (cd)", () -> count(new MustSection13(), 1, 0));
    }

    @Test
    void c1_nsrnSel3_p2p_1() throws Exception {
        count(new NSRNSel(3, true), 1, 0);
    }

    @Test
    void c1_detects_nsrn_unguarded_has6() throws Exception {
        count(new NSRNSel(3, false), 6, 0);
        mutant("C1 NS+RN unguarded expected 1", () -> count(new NSRNSel(3, false), 1, 0));
    }

    // ------------------------------------------------------------------ C2 blocking, backward revisits

    @Test
    void c2_ex41_receiverFirst_2() throws Exception {
        count(new SSR(0, 1, true), 2, 0);
    }

    @Test
    void c2_ex42_receiverBetween_2() throws Exception {
        count(new SSR(1, 1, true), 2, 0);
    }

    @Test
    void c2_detects_ex42_mutatedNumber1() {
        mutant("C2 Ex.4.2 expected 1 (no backward revisit)", () -> count(new SSR(1, 1, true), 1, 0));
    }

    @Test
    void c2_blockedReceiver_0complete_1blocked() throws Exception {
        Run r = count(new BlockedAlone(), 0, 1);
        assertEquals(1, r.report().getBlockedIterations(), "report blockedIterations");
    }

    @Test
    void c2_detects_blocked_countedComplete() {
        mutant("C2 blocked counted as complete", () -> count(new BlockedAlone(), 1, 0));
    }

    @Test
    void c2_ex43_ssrbr_4_optimal() throws Exception {
        count(new SSRBR(), 4, 0);
    }

    @Test
    void c2_detects_ex43_duplicateD() {
        // mutated expectation: graph D explored twice (5 iterations) would fail optimality
        mutant("C2 Ex.4.3 expected 5", () -> count(new SSRBR(), 5, 0));
    }

    // ------------------------------------------------------------------ C3 nondet

    @Test
    void c3_nondet3_3() throws Exception {
        count(new NondetK(3, false), 3, 0);
    }

    @Test
    void c3_nondet3_withSSR_6() throws Exception {
        count(new NondetK(3, true), 6, 0);
    }

    @Test
    void c3_detects_nondet2_withSSR_has4() throws Exception {
        count(new NondetK(2, true), 4, 0);
        mutant("C3 k=2 expected 6", () -> count(new NondetK(2, true), 6, 0));
    }

    // ------------------------------------------------------------------ C4 replay drift

    static void checkDrift(MpProgram p) throws Exception {
        Run r = run(p, 1L, false);
        assertTrue(r.thrown() != null, "the run must fail");
        String msg = String.valueOf(r.thrown().getMessage()) + " / " + String.valueOf(r.report() == null ? "" : r.report().getErrorMessage());
        Throwable t = r.thrown();
        StringBuilder all = new StringBuilder(msg);
        while (t != null) {
            all.append(" | ").append(t.getMessage());
            t = t.getCause();
        }
        System.out.println("DRIFT " + p.getClass().getSimpleName() + ": " + all);
        assertTrue(all.toString().contains("must: replay drift"), "replay drift reported: " + all);
    }

    @Test
    void c4_driftValue_fails() throws Exception {
        checkDrift(new DriftValue());
    }

    @Test
    void c4_driftStructure_fails() throws Exception {
        checkDrift(new DriftStructure());
    }

    @Test
    void c4_detects_deterministicProgram() {
        mutant("C4 control", () -> checkDrift(new SSR(2, 1, true)));
    }

    // ------------------------------------------------------------------ C6 factory and L1

    @Test
    void c6_factory_must() throws Exception {
        assertTrue(SchedulingStrategyFactory.isValidStrategy("must"), "isValidStrategy(must)");
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("must").numIterations(100).seed(1L)
                .reportPath("build/test-results/j2-tester/factory").build();
        SSR p = new SSR(2, 1, true);
        JmcModelCheckerReport report =
                new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
        System.out.println("FACTORY must: total=" + report.getTotalIterations() + " error=" + report.getErrorMessage());
        assertEquals(2, report.getTotalIterations(), "two iterations, then the stack is empty");
    }

    @Test
    void c6_detects_unknownStrategy() {
        assertTrue(!SchedulingStrategyFactory.isValidStrategy("must2"), "must2 is not valid");
    }

    @Test
    void c6_l1_seedIndependent() throws Exception {
        Run a = run(new NondetK(3, true), 1L, true);
        Run b = run(new NondetK(3, true), 987654321L, true);
        Map<Summary, Integer> sa = a.s().summaries();
        Map<Summary, Integer> sb = b.s().summaries();
        sa.forEach((k, v) -> System.out.println("L1 seed1 " + k.toJson(v)));
        assertEquals(6, sa.size(), "6 summaries");
        assertEquals(new java.util.HashSet<>(sa.keySet()), new java.util.HashSet<>(sb.keySet()), "same summaries");
        assertEquals(a.s().iterations(), b.s().iterations(), "same iterations");
    }

    // ------------------------------------------------------------------ probes of the J2-F1 defect

    @Test
    void probe_nsrn2_unguarded_2() throws Exception {
        // 2 senders, 2 unguarded receives, receiver spawned first; p2p: orders (1,2), (2,1) -> 2
        count(new NSRNSel(2, false), 2, 0);
    }

    @Test
    void probe_nsrn3_unguarded_6() throws Exception {
        count(new NSRNSel(3, false), 6, 0);
    }

    @Test
    void probe_ssr_receiverFirst_twoReceives_2() throws Exception {
        count(new SSR(0, 2, true), 2, 0);
    }

    @Test
    void probe_ssr_receiverBetween_twoReceives_2() throws Exception {
        count(new SSR(1, 2, true), 2, 0);
    }

    // ------------------------------------------------------------------ round 02 (J2-F1 fixed)

    @Test
    void r2_nsrn3_receiverLast_p2p_6() throws Exception {
        count(new NSRNVariant(3, true, CommunicationModel.P2P), 6, 0);
    }

    @Test
    void r2_nsrn3_receiverFirst_asyn_6() throws Exception {
        count(new NSRNVariant(3, false, CommunicationModel.ASYN), 6, 0);
    }

    @Test
    void r2_nsrn3_receiverFirst_p2p_6() throws Exception {
        count(new NSRNVariant(3, false, CommunicationModel.P2P), 6, 0);
    }

    @Test
    void r2_nsrn4_receiverFirst_p2p_24() throws Exception {
        // a larger instance of the J2-F1 shape: 4! = 24
        count(new NSRNVariant(4, false, CommunicationModel.P2P), 24, 0);
    }

    @Test
    void r2_detects_nsrn3_receiverLast_mutatedNumber() {
        mutant("r2 receiver last expected 3", () -> count(new NSRNVariant(3, true, CommunicationModel.P2P), 3, 0));
    }

    // ------------------------------------------------------------------ round 03 (reviewer majors M1-M4)

    /**
     * The run must fail with a message containing {@code needle}, after exactly {@code complete}
     * distinct complete graphs and no blocked one (the failing iteration is not counted).
     */
    static Run failsWith(MpProgram p, String needle, int complete) throws Exception {
        Run r = run(p, 1L, false);
        assertTrue(r.thrown() != null, "the run must fail");
        StringBuilder all = new StringBuilder(String.valueOf(r.report() == null ? "" : r.report().getErrorMessage()));
        for (Throwable t = r.thrown(); t != null; t = t.getCause()) {
            all.append(" | ").append(t.getMessage());
        }
        System.out.println("R3 FAIL " + p.getClass().getSimpleName() + ": " + all);
        assertTrue(all.toString().contains(needle), "message contains '" + needle + "': " + all);
        assertEquals(complete, r.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(0, r.s().distinctBlockedGraphs(), "distinct blocked graphs");
        return r;
    }

    @Test
    void r3_m1_earlyNondet_drift() throws Exception {
        failsWith(new R3EarlyNondet(true), "finished at guide position 0 of 1", 1);
    }

    @Test
    void r3_m1_earlyNondet_control_2() throws Exception {
        count(new R3EarlyNondet(false), 2, 0);
    }

    @Test
    void r3_m1_sendThenNondet_drift() throws Exception {
        failsWith(new R3SendThenNondet(true), "finished at guide position 1 of 2", 1);
    }

    @Test
    void r3_m1_sendThenNondet_control_2() throws Exception {
        count(new R3SendThenNondet(false), 2, 0);
    }

    @Test
    void r3_m1_skipSpawn_unconsumedGuide() throws Exception {
        failsWith(new R3SkipSpawn(), "replay drift", 1);
    }

    @Test
    void r3_detects_m1_earlyNondet_countedAs2() {
        mutant("r3 M1 early return counted as 2", () -> count(new R3EarlyNondet(true), 2, 0));
    }

    @Test
    void r3_m2_guardNb_immediate_drift() throws Exception {
        failsWith(new R3Guard(false, true), "recv whose guard rejects the committed message", 1);
    }

    @Test
    void r3_m2_guardNb_control_2() throws Exception {
        count(new R3Guard(false, false), 2, 0);
    }

    @Test
    void r3_m2_guardParked_drift() throws Exception {
        failsWith(new R3Guard(true, true), "its guard rejects", 1);
    }

    @Test
    void r3_m2_guardParked_control_2() throws Exception {
        count(new R3Guard(true, false), 2, 0);
    }

    @Test
    void r3_detects_m2_guardParked_countedAs2() {
        mutant("r3 M2 switched guard counted as 2", () -> count(new R3Guard(true, true), 2, 0));
    }

    private static final String UNSUPPORTED = "which must does not support (p2p and asyn only)";

    @Test
    void r3_m3_cdSend_refused() throws Exception {
        failsWith(new R3Model("cd-send"), "uses model CD, " + UNSUPPORTED, 0);
    }

    @Test
    void r3_m3_mboxRecv_refused() throws Exception {
        failsWith(new R3Model("mbox-recv"), "uses model MBOX, " + UNSUPPORTED, 0);
    }

    @Test
    void r3_m3_cdOnReplay_refused() throws Exception {
        failsWith(new R3Model("cd-replay"), "uses model CD, " + UNSUPPORTED, 1);
    }

    @Test
    void r3_m3_asyn_control_2() throws Exception {
        count(new R3Model("asyn"), 2, 0);
    }

    @Test
    void r3_m3_p2p_control_2() throws Exception {
        count(new R3Model("p2p"), 2, 0);
    }

    @Test
    void r3_m4_duplicateHiddenRoles_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new MustStrategy(0L, null, List.of("r", "hidden", "hidden"), List.of("r")));
        System.out.println("R3 M4 hidden: " + e.getMessage());
        assertTrue(e.getMessage().contains("roles are not distinct"), e.getMessage());
    }

    @Test
    void r3_m4_duplicateVisibleRoles_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new MustStrategy(0L, null, List.of("r", "r"), List.of("r")));
        System.out.println("R3 M4 visible: " + e.getMessage());
        assertTrue(e.getMessage().contains("roles are not distinct"), e.getMessage());
    }

    @Test
    void r3_m4_distinctRoles_accepted() {
        MustStrategy s = new MustStrategy(0L, null, List.of("r", "h1", "h2"), List.of("r"));
        assertEquals(0, s.iterations(), "constructed, nothing ran");
    }
}
