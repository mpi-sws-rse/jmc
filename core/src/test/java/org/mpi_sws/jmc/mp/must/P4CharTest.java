package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.mp.must.J2cTest.Run;
import org.mpi_sws.jmc.mp.fixtures.addressing.P4CharPrograms;
import org.mpi_sws.jmc.mp.fixtures.seq.EventLogStrategy;
import org.mpi_sws.jmc.strategies.must.P4Must;

import java.util.List;

/**
 * P4 tester round 01, characterisation and old-API guards (plan/jmc-must/P4-PROPOSAL.md rev 7, §0, §3, §4;
 * reviews p4-design-01..07). Uses only the API of f3a5de93 (fixtures {@link P4CharPrograms}, probe
 * {@link P4Must}), so it compiles and runs on the baseline; every expectation was derived from the code at
 * f3a5de93 before running (the report, plan/jmc-must/log/dev/P4/round-01.report.md, has the derivations).
 *
 * <p>The characterisation tests kept here pin behaviour P4 does not change (core {@code JmcAssume} and the
 * mp-random live rule): they pass before and after. The characterisations of the behaviour P4 changes on purpose
 * were removed in round 02; the comment before the guards names them and their replacements.
 */
class P4CharTest {

    static final String DIR = "build/test-results/p4-tester/char-";

    static Run must(String program, P4Must s) {
        String path = DIR + program;
        return J2cTest.check(program, J2cTest.main(new P4CharPrograms(program)), new JmcCheckerConfiguration.Builder()
                .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(path), s);
    }

    static P4Must probe(String program) {
        return new P4Must(1L, DIR + program);
    }

    static Run random(String program, int n) {
        return J2cTest.check(program, J2cTest.main(new P4CharPrograms(program)), new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").numIterations(n).seed(1L).reportPath(DIR + "random-" + program), null);
    }

    static void counts(Run r, int complete, int blocked) {
        assertNull(r.thrown(), "the run threw: " + r.failure());
        assertNull(r.report().getErrorMessage(), "error: " + r.failure());
        assertEquals(complete, r.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(blocked, r.s().distinctBlockedGraphs(), "distinct blocked graphs");
        assertEquals(complete + blocked, r.s().iterations(), "iterations");
        assertTrue(r.s().exhausted(), "exhausted");
    }

    // ------------------------------------------------------------------ core JmcAssume.assume(false) under Must

    /**
     * Main with nothing outstanding. Derived: ASSUME_EVENT has no case in MustStrategy.updateEvent; main resumes
     * and throws HaltTaskException.blocked, which the checker catches without FINISH; resetIteration finds no
     * parked task, the (empty) guide consumed, no Block: one complete graph, no blocked iteration. Unchanged.
     */
    @Test
    void coreAssume_main_nothingOutstanding_countedComplete() {
        Run r = must("core_assume_main_false", probe("core_assume_main_false"));
        counts(r, 1, 0);
        assertEquals(0, r.report().getBlockedIterations(), "blocked iterations");
    }

    /**
     * Main's nondet chooses TRUE (index 0), then FALSE: the pruned execution is counted as a second complete
     * graph (main: ND(2,1), no Block). Derived: 2 complete, 0 blocked, 2 iterations. Unchanged.
     */
    @Test
    void coreAssume_main_afterNondet_prunedCountedComplete() {
        Run r = must("core_assume_main_nondet", probe("core_assume_main_nondet"));
        counts(r, 2, 0);
        assertEquals(0, r.report().getBlockedIterations(), "blocked iterations");
    }

    /**
     * A child awaited by main. Derived: the child's HaltTaskException.blocked reaches MpThread's harness, which
     * sets endedBlocked and raises FINISH; main's await raises the awaitOfBlocked request, is parked, and is
     * blocked at quiescence (Block on main, countsBlocking): 0 complete, 1 blocked. Unchanged.
     */
    @Test
    void coreAssume_childAwaitedByMain_blocked_must() {
        counts(must("core_assume_child_awaited", probe("core_assume_child_awaited")), 0, 1);
    }

    /** As above under mp-random: main's awaitOfBlocked request is parked and blocked, every iteration. Unchanged. */
    @Test
    void coreAssume_childAwaitedByMain_blocked_mpRandom() {
        Run r = random("core_assume_child_awaited", 10);
        assertNull(r.thrown(), r.failure());
        assertEquals(10, r.report().getTotalIterations(), "iterations");
        assertEquals(10, r.report().getBlockedIterations(), "blocked iterations");
    }

    /**
     * With another task parked: P (stable 2) parked on a receive, Q finished and awaited, then main's core
     * assume(false). Derived: main halts with HaltTaskException; resetIteration finds P parked and throws
     * "must: iteration ended with parked tasks [2]"; nothing is counted. Unchanged.
     */
    @Test
    @Timeout(120)
    void coreAssume_withAnotherTaskParked_iterationFault_must() {
        P4Must s = probe("core_assume_other_parked");
        Run r = must("core_assume_other_parked", s);
        J2cTest.fails(r, "must: iteration ended with parked tasks [2]");
        assertEquals(0, s.distinctCompleteGraphs() + s.distinctBlockedGraphs(), "nothing counted");
    }

    /**
     * The same under mp-random: the ASSUME_EVENT(false) of main runs the live-process check. Derived: refused
     * with "main halted on assume(false) while process(es) [2] were still live". Unchanged.
     */
    @Test
    @Timeout(120)
    void coreAssume_withAnotherTaskParked_liveRule_mpRandom() {
        J2cTest.fails(random("core_assume_other_parked", 3), "main halted on assume(false) while process(es) [2]"
                + " were still live");
    }

    // ------------------------------------------------------------------ removed in round 02
    //
    // The characterisations of the behaviour P4 changes on purpose were removed after the change (round 02,
    // coordinator's order). They passed on the base tree (f3a5de93 + the four old-API P4 files, brain03, one
    // method per invocation; plan/jmc-must/log/dev/P4/round-01.report.md §2 and round-02.report.md) and failed
    // after the change, as intended. Removed, with their replacements:
    //   ce1_finallyNondet_blockItemsAndDrift_today        -> P4AssumeTest.ce1_refused_recv, ce1_refused_assume
    //   ce2_suppressedBlock_blockItemsAndDrift_today      -> P4AssumeTest.ce2_refused_recv, ce2_refused_assume
    //   ce3_threadStartedFromCleanup_blockItemsAndDrift_today -> P4AssumeTest.ce3_refused_recv, ce3_refused_assume
    //   ce4_programCallsRunMain_blockItemsAndDrift_today  -> P4AssumeTest.ce4_refused_recv, ce4_refused_assume
    //   ce5_schedulerReissue_blockItemAndDrift_today      -> P4AssumeTest.ce5_refused_recv, ce5_refused_assume
    //   ce1_split_boundaryEntriesHoldBlock_andDrift_today -> P4AssumeTest.ce1_refused_recv (refused in iteration 0,
    //                                                        before any split boundary)
    //   send99_must_today                                 -> P4AddressingTest.send99_refused_must
    //   send99_mpRandom_buffered_today                    -> P4AddressingTest.send99_refused_mpRandom
    // Their fixtures (P4CharPrograms ce1_recv .. ce5_recv, send_99) stay: the replacement tests use them.

    // ------------------------------------------------------------------ old-API guards (pass before and after)

    /** spawn's event sequence under Must, derived from the code (lowest id first). Guard. */
    static final List<String> SPAWN_TWO = List.of(
            "it=0 START_EVENT task=1 startedBy=1",
            "it=0 TASK_CREATED_EVENT task=1 createdTask=2",
            "it=0 START_EVENT task=2 startedBy=1",
            "it=0 TASK_CREATED_EVENT task=1 createdTask=3",
            "it=0 START_EVENT task=3 startedBy=1",
            "it=0 JOIN_REQUEST_EVENT task=1 waitingTask=3",
            "it=0 MP_RECV_REQUEST_EVENT task=2",
            "it=0 MP_SEND_EVENT task=3 dst=2",
            "it=0 FINISH_EVENT task=3",
            "it=0 JOIN_COMPLETE_EVENT task=1 joinedTask=3",
            "it=0 JOIN_REQUEST_EVENT task=1 waitingTask=2",
            "it=0 FINISH_EVENT task=2",
            "it=0 JOIN_COMPLETE_EVENT task=1 joinedTask=2",
            "it=0 FINISH_EVENT task=1");

    /** spawn keeps its event sequence: the golden list above, and the public constructor's sequence. Guard. */
    @Test
    void spawn_eventSequence_golden_must() {
        P4Must a = probe("spawn_two");
        P4Must b = probe("ctor_two");
        counts(must("spawn_two", a), 1, 0);
        counts(must("ctor_two", b), 1, 0);
        System.out.println("P4 spawn log " + a.log());
        assertEquals(SPAWN_TWO, a.log(), "spawn's event sequence");
        assertEquals(a.log(), b.log(), "spawn = construct + start");
    }

    /** The same comparison under mp-random (same seed, so the same schedule). Guard. */
    @Test
    void spawn_eventSequence_equalsConstructorStart_mpRandom() {
        EventLogStrategy a = new EventLogStrategy(3L, DIR + "spawn-two-log");
        EventLogStrategy b = new EventLogStrategy(3L, DIR + "ctor-two-log");
        for (Object[] x : new Object[][] {{"spawn_two", a}, {"ctor_two", b}}) {
            EventLogStrategy l = (EventLogStrategy) x[1];
            Run r = J2cTest.check((String) x[0], J2cTest.main(new P4CharPrograms((String) x[0])),
                    new JmcCheckerConfiguration.Builder().strategyType("mp-random").strategyConstructor(c -> l)
                            .numIterations(20).seed(3L).reportPath(DIR + "random-log-" + x[0]), null);
            assertNull(r.thrown(), r.failure());
        }
        assertTrue(a.log().size() > 20 * 10, "events: " + a.log().size());
        assertEquals(String.join("\n", a.log()), String.join("\n", b.log()), "identical event sequences");
    }

    /**
     * spawn(role, null) is refused with NullPointerException("body") before any id is allocated: no
     * TASK_CREATED for it, and the next spawn is task 2 (the fixture asserts both). Guard.
     */
    @Test
    void spawn_nullBody_refusedBeforeAllocation() {
        P4Must s = probe("spawn_null_body");
        counts(must("spawn_null_body", s), 1, 0);
        assertEquals(1, s.log().stream().filter(l -> l.contains("TASK_CREATED_EVENT")).count(),
                "one creation event (the second spawn's): " + s.log());
    }

    /**
     * Round 03 (Codex code review 01, P3 (4)): the TASK_CREATED parameters of the old creation paths (spawn,
     * spawnDaemon, the three public MpThread constructors; tasks 2..6): role and daemon as given and no
     * {@code pending} key. Guard (passes before and after).
     */
    @Test
    void creationParameters_oldPaths_noPendingKey() {
        P4Must s = probe("creation_old");
        counts(must("creation_old", s), 1, 0);
        System.out.println("P4 creations " + s.creations());
        assertEquals(List.of(
                "it=0 createdTask=2 role=a daemon=false pending=<absent>",
                "it=0 createdTask=3 role= daemon=true pending=<absent>",
                "it=0 createdTask=4 role= daemon=false pending=<absent>",
                "it=0 createdTask=5 role=c daemon=false pending=<absent>",
                "it=0 createdTask=6 role= daemon=true pending=<absent>"), s.creations());
    }
}
