package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.must.J2cTest.Run;
import org.mpi_sws.jmc.mp.programs.p4.AssumePair;
import org.mpi_sws.jmc.mp.programs.p4.AssumePairBaseline;
import org.mpi_sws.jmc.mp.programs.p4.MainAssume;
import org.mpi_sws.jmc.mp.programs.p4.P4CharPrograms;
import org.mpi_sws.jmc.mp.programs.p4.P4Programs;
import org.mpi_sws.jmc.mp.programs.seq.EventLogStrategy;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValue;
import org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy;
import org.mpi_sws.jmc.strategies.must.P4Must;

import java.util.List;
import java.util.Map;

/**
 * P4 tester round 01, {@code MpProcess.assume} (plan/jmc-must/P4-PROPOSAL.md rev 7, §3, §4 "Assume"): blocking
 * semantics, the equivalence with the unmatched-receive workaround for a non-daemon non-main process (counts,
 * graphs, summaries; the sequential and split launcher comparisons are in {@code P4LaunchTest}), main's failed
 * assume, a daemon's failed assume, the wrapper contract, the guide invariant on every stack item, and the five
 * counterexamples refused with the message in bounded time. New-API tests: this class does not compile on
 * f3a5de93; the counterexamples' receive forms are the behavioural fail-before cases (their characterisation is
 * {@code P4CharTest#ce*_today}).
 */
class P4AssumeTest {

    static final String DIR = "build/test-results/p4-tester/assume-";

    /** The fault text after the names (P4-PROPOSAL §3, Replay). */
    static final String TAIL = "; no message-passing operation may run once a process is blocked (a modeled"
            + " operation in a finally block, a suppressed JMC control-flow exception, a thread started from a"
            + " blocked process's cleanup, or runMain called by the program)";

    record Ran(Run run, P4Must s) {}

    static Ran must(String tag, Runnable program, P4Must s) {
        Run r = J2cTest.check(tag, J2cTest.main(program), new JmcCheckerConfiguration.Builder().strategyType("must")
                .strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(DIR + tag), s);
        return new Ran(r, s);
    }

    static Ran counting(String tag, Runnable program) {
        return must(tag, program, new P4Must(1L, DIR + tag));
    }

    static Ran exporting(String tag, MpProgram p) {
        return must(tag, p, new P4Must(1L, DIR + tag, p.declaredRoles(), p.visibleRoles()));
    }

    static Run random(String tag, Runnable program, int n) {
        return J2cTest.check(tag, J2cTest.main(program), new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .numIterations(n).seed(1L).reportPath(DIR + "random-" + tag), null);
    }

    static void ok(Ran x, int complete, int blocked) {
        Run r = x.run();
        assertNull(r.thrown(), "the run threw: " + r.failure());
        assertNull(r.report().getErrorMessage(), "error: " + r.failure());
        assertEquals(complete, x.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(blocked, x.s().distinctBlockedGraphs(), "distinct blocked graphs");
        assertEquals(complete + blocked, x.s().iterations(), "iterations");
        assertTrue(x.s().exhausted(), "exhausted");
    }

    /** The guide invariant (§3): no stack item's graph holds a Block; non-vacuous when {@code minItems > 0}. */
    static void noBlockItems(P4Must s, int minItems) {
        assertEquals(List.of(), s.blockItems(), "stack items whose graph holds a Block");
        assertTrue(s.itemsSeen() >= minItems, "stack items inspected: " + s.itemsSeen() + ", expected at least "
                + minItems);
    }

    // ------------------------------------------------------------------ assume(true)

    /** assume(true) returns at once and raises no event: the same Must and mp-random logs as without the calls. */
    @Test
    void assumeTrue_raisesNoEvent() {
        Ran a = counting("assume_true", new P4Programs("assume_true"));
        Ran b = counting("assume_true_absent", new P4Programs("assume_true_absent"));
        ok(a, 1, 0);
        ok(b, 1, 0);
        assertEquals(b.s().log(), a.s().log(), "Must event sequence");
        EventLogStrategy la = new EventLogStrategy(5L, DIR + "log-assume_true");
        EventLogStrategy lb = new EventLogStrategy(5L, DIR + "log-assume_true_absent");
        for (Object[] x : new Object[][] {{"assume_true", la}, {"assume_true_absent", lb}}) {
            EventLogStrategy l = (EventLogStrategy) x[1];
            Run r = J2cTest.check((String) x[0], J2cTest.main(new P4Programs((String) x[0])),
                    new JmcCheckerConfiguration.Builder().strategyType("mp-random").strategyConstructor(c -> l)
                            .numIterations(20).seed(5L).reportPath(DIR + "log-" + x[0]), null);
            assertNull(r.thrown(), r.failure());
        }
        assertEquals(String.join("\n", lb.log()), String.join("\n", la.log()), "mp-random event sequence");
    }

    // ------------------------------------------------------------------ the pair: assume vs the receive workaround

    /**
     * AssumePair (non-daemon, non-main r): assume and the workaround give the same counts (derived 2 complete,
     * 3 blocked), the same graphs in the same order and the same canonical forms; the baseline-API program
     * (AssumePairBaseline:recv) too; no stack item holds a Block (3 items at least: r's alternatives).
     */
    @Test
    void pair_counting_identicalCountsAndGraphs_noBlockItems() {
        Ran assume = counting("pair-assume", new AssumePair("assume"));
        Ran recv = counting("pair-recv", new AssumePair("recv"));
        Ran base = counting("pair-baseline", new AssumePairBaseline("recv"));
        for (Ran x : List.of(assume, recv, base)) {
            ok(x, 2, 3);
            noBlockItems(x.s(), 3);
        }
        System.out.println("P4 pair graphs " + assume.s().graphs());
        assertEquals(recv.s().graphs(), assume.s().graphs(), "graphs, assume vs workaround");
        assertEquals(recv.s().canons(), assume.s().canons(), "canonical forms, assume vs workaround");
        assertEquals(base.s().graphs(), recv.s().graphs(), "graphs, create/start vs constructors");
        assertEquals(recv.s().log(), base.s().log(), "event sequence, create/start vs constructors");
    }

    /** Export: identical summaries with multiplicities; exported 5 iterations each; no shape fault. */
    @Test
    void pair_export_identicalSummaries() {
        Ran assume = exporting("pair-export-assume", new AssumePair("assume"));
        Ran recv = exporting("pair-export-recv", new AssumePair("recv"));
        Ran base = exporting("pair-export-baseline", new AssumePairBaseline("recv"));
        for (Ran x : List.of(assume, recv, base)) {
            ok(x, 2, 3);
            assertEquals(5, x.s().exportedIterations(), "exported iterations");
            noBlockItems(x.s(), 3);
        }
        Map<Summary, Integer> want = recv.s().summaries();
        System.out.println("P4 pair summaries " + J2cTest.lines(want));
        assertTrue(want.keySet().stream().anyMatch(s -> s.toJson(1).contains("\"r\":\"blocked\"")), "r blocked: "
                + J2cTest.lines(want));
        assertEquals(want, assume.s().summaries(), "summaries, assume vs workaround");
        assertEquals(want, base.s().summaries(), "summaries, create/start vs constructors");
    }

    /**
     * In-process split (K 1): the boundary is crossed (3 entries), the items run, the union of the outcomes is
     * the sequential 2 complete / 3 blocked over 5 iterations, equal for both variants; no boundary entry and
     * no worker stack item holds a Block.
     */
    @Test
    void pair_inlineSplit_crossesBoundary_itemsRun_identical_noBlockEntries() {
        for (String v : List.of("assume", "recv")) {
            P4Must s = new P4Must(1L, DIR + "pair-split-" + v);
            P4Must.InlineSplit split = new P4Must.InlineSplit(1);
            s.setSplitControl(split);
            Run r = J2cTest.check("pair-split-" + v, J2cTest.main(new AssumePair(v)),
                    new JmcCheckerConfiguration.Builder().strategyType("must").strategyConstructor(c -> s)
                            .numIterations(10000).seed(1L).reportPath(DIR + "pair-split-" + v), null);
            assertNull(r.thrown(), v + ": " + r.failure());
            assertNotNull(split.view(), v + ": the split boundary was crossed");
            assertEquals(3, split.view().stackTopFirst().size(), v + ": boundary entries");
            assertEquals(List.of(), P4Must.blockEntries(split.view()), v + ": boundary entries holding a Block");
            assertEquals(3, split.itemsThatRan(), v + ": items that ran: " + split.segments());
            assertEquals(5, split.iterations(), v + ": iterations over all segments");
            assertEquals(2, split.distinctComplete(), v + ": complete");
            assertEquals(3, split.distinctBlocked(), v + ": blocked");
            assertEquals(List.of(), s.blockItems(), v + ": stack items holding a Block");
        }
    }

    // ------------------------------------------------------------------ main's failed assume

    /** Main alone: parked at its failed assume and blocked (Must: 0/1; mp-random: every iteration blocked). */
    @Test
    void mainAlone_blocked_bothStrategies() {
        P4Programs p = new P4Programs("assume_main_alone");
        ok(counting("assume_main_alone", p), 0, 1);
        P4Programs q = new P4Programs("assume_main_alone");
        Run r = random("assume_main_alone", q, 10);
        assertNull(r.thrown(), r.failure());
        assertEquals(10, r.report().getBlockedIterations(), "blocked iterations");
        assertFalse(p.afterAssumeRan || q.afterAssumeRan, "a statement after the assume ran");
    }

    /**
     * MainAssume late (export mode): main blocks after awaiting everything when r read V(2) or V(3); no shape
     * fault; derived 1 complete, 2 blocked, 3 iterations exported; no stack item holds a Block. Early: main
     * blocks before any await; 0 complete, 3 blocked, all exported (the never-awaited rule is exempt).
     */
    @Test
    void main_export_blockedWithoutShapeFault() {
        Ran late = exporting("main-late", new MainAssume("late"));
        ok(late, 1, 2);
        assertEquals(3, late.s().exportedIterations(), "late: exported");
        noBlockItems(late.s(), 2);
        Ran early = exporting("main-early", new MainAssume("early"));
        ok(early, 0, 3);
        assertEquals(3, early.s().exportedIterations(), "early: exported");
        noBlockItems(early.s(), 2);
        System.out.println("P4 main summaries late " + J2cTest.lines(late.s().summaries()) + " early "
                + J2cTest.lines(early.s().summaries()));
    }

    /** MainAssume late and early under an in-process split (K 1) in export mode: no fault, same union counts. */
    @Test
    void main_export_inlineSplit_aroundTheAssume() {
        for (String v : List.of("late", "early")) {
            MainAssume p = new MainAssume(v);
            P4Must s = new P4Must(1L, DIR + "main-split-" + v, p.declaredRoles(), p.visibleRoles());
            P4Must.InlineSplit split = new P4Must.InlineSplit(1);
            s.setSplitControl(split);
            Run r = J2cTest.check("main-split-" + v, J2cTest.main(p), new JmcCheckerConfiguration.Builder()
                    .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L)
                    .reportPath(DIR + "main-split-" + v), null);
            assertNull(r.thrown(), v + ": " + r.failure());
            assertNotNull(split.view(), v + ": the split boundary was crossed");
            assertEquals(2, split.itemsThatRan(), v + ": items that ran: " + split.segments());
            assertEquals(3, split.iterations(), v + ": iterations");
            assertEquals(v.equals("late") ? 1 : 0, split.distinctComplete(), v + ": complete");
            assertEquals(v.equals("late") ? 2 : 3, split.distinctBlocked(), v + ": blocked");
            assertEquals(List.of(), P4Must.blockEntries(split.view()), v + ": boundary entries holding a Block");
            assertEquals(List.of(), s.blockItems(), v + ": stack items holding a Block");
        }
    }

    /**
     * Main parked at its failed assume, then a (m/0, task 2) fails: an error of the iteration, delivered to
     * main; no statement after the assume runs. Must and mp-random.
     */
    @Test
    @Timeout(120)
    void mainParkedAtAssume_childFails_error_noStatementAfter() {
        P4Programs p = new P4Programs("assume_main_then_child_fails");
        J2cTest.fails(counting("assume_main_then_child_fails", p).run(),
                "must: process m/0 (task 2) failed: java.lang.AssertionError: boom");
        assertFalse(p.afterAssumeRan, "Must: a statement after the assume ran");
        P4Programs q = new P4Programs("assume_main_then_child_fails");
        J2cTest.fails(random("assume_main_then_child_fails", q, 5), "process 2 failed: java.lang.AssertionError: boom");
        assertFalse(q.afterAssumeRan, "mp-random: a statement after the assume ran");
    }

    // ------------------------------------------------------------------ daemons and processes

    /**
     * A daemon's failed assume makes the execution blocked (Must 0/1; mp-random every iteration blocked), unlike
     * a daemon's unmatched receive (J2cTest#r1_daemonBlockedOnReceive_complete_must: 1/0).
     */
    @Test
    void daemonAssume_blocked_bothStrategies() {
        Ran x = counting("assume_daemon", new P4Programs("assume_daemon"));
        ok(x, 0, 1);
        Run r = random("assume_daemon", new P4Programs("assume_daemon"), 10);
        assertNull(r.thrown(), r.failure());
        assertEquals(10, r.report().getBlockedIterations(), "blocked iterations");
    }

    /** A process's failed assume, awaited by main: blocked, as the workaround (J2cTest r1_nondaemon_recv). */
    @Test
    void processAssume_awaited_blocked_bothStrategies() {
        Ran x = counting("assume_proc_awaited", new P4Programs("assume_proc_awaited"));
        ok(x, 0, 1);
        Run r = random("assume_proc_awaited", new P4Programs("assume_proc_awaited"), 10);
        assertNull(r.thrown(), r.failure());
        assertEquals(10, r.report().getBlockedIterations(), "blocked iterations");
    }

    // ------------------------------------------------------------------ the wrapper contract

    /**
     * mp-random, but the failed assume's request is answered at once with {@code answer} instead of being
     * parked (a strategy breaking the contract), to reach the wrapper's two answer paths.
     */
    static final class AnsweringStrategy extends MpRandomSchedulingStrategy {
        private final SchedulingChoiceValue answer;
        private volatile Long pending;

        AnsweringStrategy(SchedulingChoiceValue answer, String path) {
            super(1L, path);
            this.answer = answer;
        }

        @Override
        public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
            Map<String, Object> ps = event.getParams();
            if (event.getType() == JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT && ps != null
                    && Boolean.TRUE.equals(ps.get("assume"))) {
                pending = event.getTaskId();
                return;
            }
            super.updateEvent(event);
        }

        @Override
        public SchedulingChoice<?> nextTask() {
            Long t = pending;
            if (t != null) {
                pending = null;
                return SchedulingChoice.task(t, answer);
            }
            return super.nextTask();
        }
    }

    static Run answered(SchedulingChoiceValue answer, String tag, P4Programs p) {
        AnsweringStrategy s = new AnsweringStrategy(answer, DIR + tag);
        return J2cTest.check(tag, J2cTest.main(p), new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .strategyConstructor(c -> s).numIterations(1).seed(1L).reportPath(DIR + tag), null);
    }

    /** A normal answer to a failed assume is refused: "an assume(false) was answered"; nothing after it runs. */
    @Test
    @Timeout(120)
    void wrapper_normalAnswer_refused() {
        P4Programs p = new P4Programs("assume_answered");
        J2cTest.fails(answered(new PrimitiveValue(0), "answered-normal", p), "an assume(false) was answered");
        assertFalse(p.afterAssumeRan, "a statement after the assume ran");
    }

    /** A fault answer is thrown on the caller as the iteration's error with the strategy's message. */
    @Test
    @Timeout(120)
    void wrapper_faultAnswer_thrownAsError() {
        P4Programs p = new P4Programs("assume_answered");
        J2cTest.fails(answered(new MpFaultValue("injected fault P4"), "answered-fault", p), "injected fault P4");
        assertFalse(p.afterAssumeRan, "a statement after the assume ran");
    }

    // ------------------------------------------------------------------ the five counterexamples, refused

    /**
     * A counterexample under Must after P4: refused with the message, in bounded time (the method's timeout),
     * no stack item holding a Block, and no process thread left registered alive afterwards (within 10 s).
     */
    static void refused(String tag, Runnable program, String names) throws InterruptedException {
        P4Must s = new P4Must(1L, DIR + tag);
        Run r = must(tag, program, s).run();
        System.out.println("P4 refused " + tag + ": " + r.failure() + " items " + s.itemsSeen());
        J2cTest.fails(r, "must: " + names + TAIL);
        assertEquals(List.of(), s.blockItems(), "stack items holding a Block");
        assertEquals(List.of(), P4Must.awaitNoProcessThreads(10_000), "process threads still alive");
    }

    @Test
    @Timeout(120)
    void ce1_refused_recv() throws Exception {
        refused("ce1-recv", new P4CharPrograms("ce1_recv"), "task m/0 raised a nondet after process m/0 was blocked");
    }

    @Test
    @Timeout(120)
    void ce1_refused_assume() throws Exception {
        refused("ce1-assume", new P4Programs("ce1_assume"), "task m/0 raised a nondet after process m/0 was blocked");
    }

    @Test
    @Timeout(120)
    void ce2_refused_recv() throws Exception {
        refused("ce2-recv", new P4CharPrograms("ce2_recv"), "task m raised a nondet after process m/0 was blocked");
    }

    @Test
    @Timeout(120)
    void ce2_refused_assume() throws Exception {
        refused("ce2-assume", new P4Programs("ce2_assume"), "task m raised a nondet after process m/0 was blocked");
    }

    @Test
    @Timeout(120)
    void ce3_refused_recv() throws Exception {
        refused("ce3-recv", new P4CharPrograms("ce3_recv"), "task m/0 raised a nondet after process m/1 was blocked");
    }

    @Test
    @Timeout(120)
    void ce3_refused_assume() throws Exception {
        refused("ce3-assume", new P4Programs("ce3_assume"), "task m/0 raised a nondet after process m/1 was blocked");
    }

    @Test
    @Timeout(120)
    void ce4_refused_recv() throws Exception {
        refused("ce4-recv", new P4CharPrograms("ce4_recv"), "task m raised a nondet after process m/0 was blocked");
    }

    @Test
    @Timeout(120)
    void ce4_refused_assume() throws Exception {
        refused("ce4-assume", new P4Programs("ce4_assume"), "task m raised a nondet after process m/0 was blocked");
    }

    /** The scheduler path: the fault reaches the checker through R's thread; no task is left paused. */
    @Test
    @Timeout(120)
    void ce5_refused_recv() throws Exception {
        refused("ce5-recv", new P4CharPrograms("ce5_recv"), "task m/0 raised a receive after process m/3 was blocked");
    }

    @Test
    @Timeout(120)
    void ce5_refused_assume() throws Exception {
        refused("ce5-assume", new P4Programs("ce5_assume"),
                "task m/0 raised a receive after process m/3 was blocked");
    }

    // ------------------------------------------------------------------ round 04 (Codex code review 02, P2)

    /**
     * The mp-random exporter (MpRecordingStrategy) with main's failed assume: MainAssume early (main blocks before
     * any await) and late (after all awaits), and AssumePair:assume (a non-main assume) for contrast. Derived, per
     * program, for 100 iterations at seed 1: no error, no "main performs a receive", no never-awaited refusal,
     * every iteration exported; MainAssume: every summary has r, s1, s2, s3 done (main is invisible), early blocks
     * every iteration; AssumePair: some summary has r blocked. mp-random with a fixed seed is deterministic, so the
     * set of sampled summary lines (multiplicities dropped) is compared with the Must export's set: equality is
     * expected (each of the 3 resp. 5 executions has a sizeable probability per iteration; at seed 1 the outcome
     * is fixed, not re-drawn). Fails before the lead's fix (the recorder refused main's assume request).
     */
    @Test
    @Timeout(300)
    void recorder_mainAssumeAndPair_export_equalsMust() {
        List<String> bad = new java.util.ArrayList<>();
        for (String program : List.of("MainAssume:early", "MainAssume:late", "AssumePair:assume")) {
            boolean pair = program.startsWith("AssumePair");
            String v = program.substring(program.indexOf(':') + 1);
            MpProgram p = pair ? new AssumePair(v) : new MainAssume(v);
            MpProgram q = pair ? new AssumePair(v) : new MainAssume(v);
            String tag = "rec-" + program.replace(':', '-');
            J2cTest.Rec rec = J2cTest.recorder(p, tag, 100);
            Ran must = exporting(tag + "-must", q);
            String f = rec.run().failure();
            System.out.println("P4 recorder " + program + " failure '" + f + "' blocked "
                    + (rec.run().report() == null ? "-" : rec.run().report().getBlockedIterations()) + " lines "
                    + J2cTest.lines(rec.rec().summaries()));
            if (rec.run().thrown() != null || rec.run().report().getErrorMessage() != null) {
                bad.add(program + ": the recorder run failed: " + f);
                continue;
            }
            if (f.contains("main performs a receive") || f.contains("never awaited")) {
                bad.add(program + ": refusal " + f);
            }
            if (rec.rec().exportedIterations() != 100 || rec.rec().skippedIterations() != 0) {
                bad.add(program + ": exported " + rec.rec().exportedIterations() + ", skipped "
                        + rec.rec().skippedIterations());
            }
            if (program.equals("MainAssume:early") && rec.run().report().getBlockedIterations() != 100) {
                bad.add(program + ": blocked iterations " + rec.run().report().getBlockedIterations() + " of 100");
            }
            java.util.Set<String> lines = J2cTest.lines(rec.rec().summaries());
            for (String l : lines) {
                boolean allDone = l.contains("\"r\":\"done\"") && l.contains("\"s1\":\"done\"")
                        && l.contains("\"s2\":\"done\"") && l.contains("\"s3\":\"done\"");
                if (!pair && !allDone) {
                    bad.add(program + ": a visible process not done: " + l);
                }
            }
            if (pair && lines.stream().noneMatch(l -> l.contains("\"r\":\"blocked\""))) {
                bad.add(program + ": no summary with r blocked: " + lines);
            }
            if (must.run().thrown() != null) {
                bad.add(program + ": the Must export failed: " + must.run().failure());
            } else if (!J2cTest.lines(must.s().summaries()).equals(lines)) {
                bad.add(program + ": recorder lines " + lines + " vs Must " + J2cTest.lines(must.s().summaries()));
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
