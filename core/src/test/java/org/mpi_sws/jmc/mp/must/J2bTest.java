package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.consistency.MpConsistency;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.programs.j2b.HiddenFailure;
import org.mpi_sws.jmc.mp.programs.j2b.J2bPrograms;
import org.mpi_sws.jmc.mp.programs.j2b.SpawnAfterAwait;
import org.mpi_sws.jmc.mp.programs.j2b.StartOrderRoles;
import org.mpi_sws.jmc.strategies.mp.MpRecordingStrategy;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

/**
 * Tester tests for J2b round 01 (plan/jmc-must/log/dev/J2b/round-01.prompt.md): the H1 defects F2
 * (task names), F3 (spawn/join edges) and F4 (awaiting a blocked process), derived before reading the
 * J2b code (scratchpad j2b/derivations.txt; the report has the derivations).
 */
class J2bTest {

    record Run(MustStrategy s, JmcModelCheckerReport report, Throwable thrown) {
        String failure() {
            StringBuilder b = new StringBuilder(report == null || report.getErrorMessage() == null
                    ? "" : report.getErrorMessage());
            for (Throwable t = thrown; t != null; t = t.getCause()) {
                b.append(" | ").append(t);
            }
            return b.toString();
        }
    }

    static Run check(String name, Runnable p, JmcCheckerConfiguration.Builder b, MustStrategy s) {
        JmcModelCheckerReport report = null;
        Throwable thrown = null;
        try {
            report = new JmcModelChecker(b.build()).check(new JmcFunctionalTestTarget(name, p::run));
        } catch (Throwable t) {
            thrown = t;
        }
        Run r = new Run(s, report, thrown);
        System.out.println("J2B " + name + (s == null ? "" : " complete=" + s.distinctCompleteGraphs() + " blocked="
                + s.distinctBlockedGraphs() + " iterations=" + s.iterations() + " exhausted=" + s.exhausted())
                + " total=" + (report == null ? "-" : report.getTotalIterations())
                + " reportBlocked=" + (report == null ? "-" : report.getBlockedIterations())
                + " failure=" + r.failure());
        return r;
    }

    static Run must(String program) {
        MustStrategy s = new MustStrategy(1L, "build/test-results/j2b-tester/" + program);
        return check(program, new J2bPrograms(program), new JmcCheckerConfiguration.Builder()
                .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L)
                .reportPath("build/test-results/j2b-tester/" + program), s);
    }

    static Run mustExport(MpProgram p) {
        String path = "build/test-results/j2b-tester/" + p.programName();
        MustStrategy s = new MustStrategy(1L, path, p.rolesInSpawnOrder(), p.visibleRoles());
        return check(p.programName(), p, new JmcCheckerConfiguration.Builder()
                .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(path), s);
    }

    static Run mpRandom(String program, int iterations) {
        return check(program, new J2bPrograms(program), new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").numIterations(iterations).seed(1L)
                .reportPath("build/test-results/j2b-tester/random-" + program), null);
    }

    static Run recorder(MpProgram p, int iterations) {
        String path = "build/test-results/j2b-tester/rec-" + p.programName();
        MpRecordingStrategy rec = new MpRecordingStrategy(1L, path, p.rolesInSpawnOrder(), p.visibleRoles());
        return check(p.programName(), p, new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").strategyConstructor(c -> rec).numIterations(iterations).seed(1L)
                .reportPath(path), null);
    }

    static void counts(String program, int complete, int blocked) {
        Run r = must(program);
        assertEquals(null, r.thrown(), "the run threw: " + r.failure());
        assertEquals(complete, r.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(blocked, r.s().distinctBlockedGraphs(), "distinct blocked graphs");
        assertEquals(complete + blocked, r.s().iterations(), "iterations == complete + blocked");
        assertTrue(r.s().exhausted(), "exhausted");
    }

    static void fails(Run r, String needle) {
        assertTrue(r.thrown() != null || (r.report() != null && r.report().getErrorMessage() != null),
                "the run must fail");
        assertTrue(r.failure().contains(needle), "failure mentions '" + needle + "': " + r.failure());
    }

    // ------------------------------------------------------------------ F3: spawn and join edges

    @Test
    void f3_spawnEdge_1() {
        counts("f3_spawn_edge", 1, 0);
    }

    @Test
    void f3_joinEdge_1() {
        counts("f3_join_edge", 1, 0);
    }

    @Test
    void f3_eventlessParent_1() {
        counts("f3_eventless_parent", 1, 0);
    }

    @Test
    void f3_joinSpawnChain_1() {
        counts("f3_join_spawn_chain", 1, 0);
    }

    @Test
    void f3_noFalseBlock_spawnOrder_2() {
        counts("f3_no_false_block_spawn_order", 2, 0);
    }

    @Test
    void f3_noFalseBlock_unrelatedJoin_2() {
        counts("f3_no_false_block_unrelated_join", 2, 0);
    }

    @Test
    void f3_export_spawnAfterAwait_refused() {
        fails(mustExport(new SpawnAfterAwait()), "spawned after main's first await");
    }

    // ------------------------------------------------------------------ F2: task names

    @Test
    void f2_nestedSpawn_2() {
        counts("f2_nested_spawn", 2, 0);
    }

    @Test
    void f2_conditionalSpawn_5() {
        counts("f2_conditional_spawn", 5, 0);
    }

    @Test
    void f2_tidPayload_2() {
        counts("f2_tid_payload", 2, 0);
    }

    @Test
    void f2_sendToUnstarted_2() {
        counts("f2_send_to_unstarted", 2, 0);
    }

    @Test
    void f2_nondeterministicSpawn_drift() {
        Run r = must("f2_nondeterministic_spawn");
        fails(r, "replay drift");
        assertEquals(1, r.s().distinctCompleteGraphs(), "the first graph only");
    }

    @Test
    void f2_guardOnTid_startOrder_1() {
        // R's guard compares the payload's Tid with T2's runtime Tid; T2 started first, so its name's
        // stable id differs from its runtime id. Derived (the program's semantics): R reads it, 1/0.
        counts("f2_guard_on_tid_start_order", 1, 0);
    }

    @Test
    void f2_guardOnPlaceholderPayload_1() {
        // main sends T a Req naming T before T starts; T's guard checks the payload names itself: 1/0.
        counts("f2_guard_on_placeholder_payload", 1, 0);
    }

    // ------------------------------------------------------------------ F4: awaiting a blocked process

    @Test
    void f4_joinBlocked_must_0_1() {
        counts("f4_join_blocked", 0, 1);
    }

    @Test
    void f4_joinBlocked_mpRandom_allBlocked() {
        Run r = mpRandom("f4_join_blocked", 20);
        assertEquals(null, r.thrown(), "no failure: " + r.failure());
        assertEquals(20, r.report().getTotalIterations(), "iterations");
        assertEquals(20, r.report().getBlockedIterations(), "every iteration blocked");
    }

    @Test
    void f34_server_must_1_0() {
        counts("f34_server", 1, 0);
    }

    @Test
    void f4_transitive_must_0_1() {
        counts("f4_transitive", 0, 1);
    }

    @Test
    void f4_transitive_mpRandom_allBlocked() {
        Run r = mpRandom("f4_transitive", 20);
        assertEquals(null, r.thrown(), "no failure: " + r.failure());
        assertEquals(20, r.report().getBlockedIterations(), "every iteration blocked");
    }

    // Round 02 (owner's ruling Q1): a process failure is an iteration error when it happens. The four
    // hidden-failure tests below replace round 01's "failed and was never awaited" expectations.

    private static final String F_FAILS = "failed: java.lang.IllegalStateException: f fails";

    @Test
    void f4_hiddenFailure_must_fails() {
        Run r = must("f4_hidden_failure");
        fails(r, "must: process m/1 (task 3) " + F_FAILS);
        assertEquals(0, r.s().distinctCompleteGraphs() + r.s().distinctBlockedGraphs(), "nothing counted");
    }

    @Test
    void f4_hiddenFailure_mpRandom_fails() {
        fails(mpRandom("f4_hidden_failure", 5), "process 3 " + F_FAILS);
    }

    @Test
    void f4_hiddenFailure_mustExport_fails() {
        Run r = mustExport(new HiddenFailure());
        fails(r, "must: process m/1 (task 3) " + F_FAILS);
        assertEquals(0, r.s().summaries().size(), "nothing exported");
        assertEquals(0, r.s().distinctCompleteGraphs() + r.s().distinctBlockedGraphs(), "nothing counted");
    }

    @Test
    void f4_hiddenFailure_recorder_fails() {
        fails(recorder(new HiddenFailure(), 5), "process 3 " + F_FAILS);
    }

    // ------------------------------------------------------------------ round 02: J2b-F1 follow-ups

    @Test
    void f1_laterIterationGuard_4() {
        // the guard of G's receive, raised in iteration 0, decides a revisit in iteration 2, where the
        // runtime ids of A's children have changed (no E): derived 4/0
        counts("f1_later_iteration_guard", 4, 0);
    }

    @Test
    void f1_guardOnUnstartedPayload_1() {
        // derived: R reads S1's Req(T), T never started: 1/0 (the guard sees the placeholder mapped back)
        counts("f1_guard_on_unstarted_payload", 1, 0);
    }

    @Test
    void f1_payloadNeverStarted_2() {
        counts("f1_payload_never_started", 2, 0);
    }

    @Test
    void f1_payloadUnstartedThenStarted_2() {
        counts("f1_payload_unstarted_then_started", 2, 0);
    }

    // f1_deferredValue_drift (round 02) tested the deferred value check that rework R1 removed: under
    // creation naming, starting U after T's creation no longer changes T's identity. Replaced by
    // r1_identityChangedBeforeT_drift / r1_identityChangedAfterT_2 below.

    // ------------------------------------------------------------------ round 02: owner's Q1, new

    @Test
    void q1_caughtFailure_must() {
        Run r = must("q1_caught_failure");
        fails(r, "must: process m/0 (task 2) " + F_FAILS);
        assertEquals(0, r.s().distinctCompleteGraphs() + r.s().distinctBlockedGraphs(), "nothing counted");
    }

    @Test
    void q1_caughtFailure_mpRandom() {
        fails(mpRandom("q1_caught_failure", 5), "process 2 " + F_FAILS);
    }

    @Test
    void q1_unawaitedMainRunsOn_must() {
        Run r = must("q1_unawaited_main_runs_on");
        fails(r, "must: process m/0 (task 2) " + F_FAILS);
        assertTrue(!r.failure().contains("main ran past"), "main stopped: " + r.failure());
    }

    @Test
    void q1_unawaitedNoAssert_mpRandom() {
        fails(mpRandom("q1_unawaited_no_assert", 5), "process 2 " + F_FAILS);
    }

    @Test
    void q1_parkedMainResumed_must() {
        Run r = must("q1_parked_main_resumed");
        fails(r, "must: process m/0 (task 2) " + F_FAILS);
        assertTrue(!r.failure().contains("main ran past"), "main resumed with the fault: " + r.failure());
    }

    @Test
    void q1_assertionWins_must() {
        Run r = must("q1_assertion_wins");
        fails(r, "main asserts");
        assertTrue(!r.failure().contains("f fails"), "the program's assertion wins: " + r.failure());
    }

    @Test
    void q1_assertionWins_mpRandom() {
        Run r = mpRandom("q1_assertion_wins", 5);
        fails(r, "main asserts");
        assertTrue(!r.failure().contains("f fails"), "the program's assertion wins: " + r.failure());
    }

    // ------------------------------------------------------------------ round 03: Codex M1-M3 and rework R1

    /** A counting run that also checks every completed graph with MpConsistency (Codex M2). */
    static Run mustChecked(String program, java.util.List<String> violations) {
        String path = "build/test-results/j2b-tester/" + program;
        MustStrategy s = new MustStrategy(1L, path) {
            @Override
            public void resetIteration(int iteration) {
                // checked before the strategy's own reset (which starts the next iteration's id map),
                // and kept only if an iteration actually ran: the checker also resets after the final
                // initIteration that found the stack empty, when the graph is the previous one
                int before = iterations();
                java.util.Optional<MpConsistency.Violation> v = MpConsistency.check(currentGraph());
                super.resetIteration(iteration);
                if (v.isPresent() && iterations() > before) {
                    violations.add("iteration " + iteration + ": " + v.get());
                }
            }
        };
        return check(program, new J2bPrograms(program), new JmcCheckerConfiguration.Builder()
                .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(path), s);
    }

    static Run countsChecked(String program, int complete, int blocked) {
        java.util.List<String> violations = new java.util.ArrayList<>();
        Run r = mustChecked(program, violations);
        System.out.println("J2B violations " + program + ": " + violations);
        assertEquals(null, r.thrown(), "the run threw: " + r.failure());
        assertEquals(complete, r.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(blocked, r.s().distinctBlockedGraphs(), "distinct blocked graphs");
        assertEquals(complete + blocked, r.s().iterations(), "iterations == complete + blocked");
        assertTrue(r.s().exhausted(), "exhausted");
        assertEquals(java.util.List.of(), violations, "every completed graph is consistent");
        return r;
    }

    @Test
    void m1_eventfulJoinChain_1() {
        // R (created first: stable 2) tryRecv's; C sends to itself, then awaits R; main awaits C, then sends
        // to R. R's receive is porf-before main's send (End(R) -> C's join -> C's end -> main's join), so
        // R reads ⊥ only: 1/0.
        Run r = countsChecked("m1_eventful_join_chain", 1, 0);
        ExecutionGraph g = r.s().currentGraph();
        System.out.println("J2B m1 graph: " + g);
        assertTrue(g.porfBefore(new EventKey(2L, 0), new EventKey(1L, 0)), "R.recv porf-before main's send");
    }

    @Test
    void m2_guardUnstartedForward_4() {
        // T created, never started; S1, S2 send Req(T); R's blocking guarded receive (created last); N's
        // binary nondet: 2 rf x 2 choices = 4, every one consistent
        countsChecked("m2_guard_unstarted_forward", 4, 0);
    }

    @Test
    void m2_guardUnstartedBackward_4() {
        // R between S1 and S2: R reads S1, S2's send revisits it (backward); with N: 4
        countsChecked("m2_guard_unstarted_backward", 4, 0);
    }

    @Test
    void m3_dstBeforeStart_2() {
        countsChecked("m3_dst_before_start", 2, 0);
    }

    @Test
    void m3_payloadBeforeStart_2() {
        countsChecked("m3_payload_before_start", 2, 0);
    }

    @Test
    void m3_dstIdentityChanged_drift() {
        // odd runs create U before T: T's creation path differs in the replay of the nondet alternative
        Run r = must("m3_dst_identity_changed");
        fails(r, "replay drift");
        assertEquals(1, r.s().distinctCompleteGraphs(), "the first graph only");
    }

    @Test
    void m3_payloadIdentityChanged_drift() {
        Run r = must("m3_payload_identity_changed");
        fails(r, "replay drift");
        assertEquals(1, r.s().distinctCompleteGraphs(), "the first graph only");
    }

    @Test
    void r1_identityChangedBeforeT_drift() {
        // replaces f1_deferredValue_drift: U created before T on odd runs changes T's name; S1's replayed
        // payload then differs from the guide's
        Run r = must("r1_identity_changed_before_t");
        fails(r, "replay drift");
        assertEquals(1, r.s().distinctCompleteGraphs(), "the first graph only");
    }

    @Test
    void r1_identityChangedAfterT_2() {
        // U created after T on odd runs: T's identity does not change, nothing to detect: 2/0
        countsChecked("r1_identity_changed_after_t", 2, 0);
    }

    @Test
    void r1_guardOnTidShifted_2() {
        // stable != runtime for R and T2 in the iteration where A's x reads S0 (no E); R's guard compares
        // T2's payload with T2's captured runtime tid: x ∈ {⊥, S0}, R reads T2 each time: 2/0
        countsChecked("r1_guard_on_tid_shifted", 2, 0);
    }

    @Test
    void r1_exportRolesByStartOrder() {
        // roles follow main's START order, not creation order (StartOrderRoles creates cli first, starts
        // srv first): exactly one summary, srv sending and cli receiving
        Run r = mustExport(new StartOrderRoles());
        assertEquals(null, r.thrown(), "the run threw: " + r.failure());
        java.util.List<String> lines = r.s().summaries().entrySet().stream().map(e -> e.getKey().toJson(e.getValue()))
                .toList();
        System.out.println("J2B roles " + lines);
        String v1 = "{\"t\":\"V\",\"f\":[1]}";
        assertEquals(java.util.List.of("{\"statuses\":{\"cli\":\"done\",\"srv\":\"done\"},\"words\":{\"cli\":[{\"r\":"
                + v1 + "}],\"srv\":[{\"s\":" + v1 + "}]},\"counts\":[1,0],\"n\":1}"), lines, "summary");
    }
}
