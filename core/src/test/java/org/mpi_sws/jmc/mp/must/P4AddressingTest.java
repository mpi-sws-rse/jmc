package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.must.J2cTest.Rec;
import org.mpi_sws.jmc.mp.must.J2cTest.Run;
import org.mpi_sws.jmc.mp.programs.p4.AddressingExport;
import org.mpi_sws.jmc.mp.programs.p4.AssumePair;
import org.mpi_sws.jmc.mp.programs.p4.P4CharPrograms;
import org.mpi_sws.jmc.mp.programs.p4.P4Programs;
import org.mpi_sws.jmc.mp.programs.seq.EventLogStrategy;
import org.mpi_sws.jmc.strategies.must.P4Must;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * P4 tester round 01, addressing (plan/jmc-must/P4-PROPOSAL.md rev 7, §1, §2, §4 "Addressing", "Lifecycle of
 * Pending"): {@code MpProcess.create}/{@code createDaemon}, {@code Pending.tid}/{@code start}, the refusals with
 * their exact messages, and {@code MpProgram.declaredRoles()}. New-API tests: this class does not compile on
 * f3a5de93 (that is not behavioural fail-before evidence); the send-to-99 tests are the behavioural ones (their
 * characterisation is {@code P4CharTest#send99_*_today}). Programs marked counting-only in {@link P4Programs}
 * are run under Must's counting constructor and mp-random only.
 *
 * <p>Must names: main is {@code m} (stable 1), main's k-th creation {@code m/k} (stable k + 2 here); runtime ids
 * follow allocation order (main 1, then 2, 3, ...).
 */
class P4AddressingTest {

    static final String DIR = "build/test-results/p4-tester/addr-";

    record Ran(Run run, P4Must s, P4Programs p) {}

    static Ran must(String program) {
        P4Programs p = new P4Programs(program);
        P4Must s = new P4Must(1L, DIR + program);
        Run r = J2cTest.check(program, J2cTest.main(p), new JmcCheckerConfiguration.Builder().strategyType("must")
                .strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(DIR + program), s);
        return new Ran(r, s, p);
    }

    record RanRandom(Run run, EventLogStrategy log, P4Programs p) {}

    static RanRandom random(String program, int n) {
        P4Programs p = new P4Programs(program);
        String path = DIR + "random-" + program;
        EventLogStrategy log = new EventLogStrategy(1L, path);
        Run r = J2cTest.check(program, J2cTest.main(p), new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .strategyConstructor(c -> log).numIterations(n).seed(1L).reportPath(path), null);
        return new RanRandom(r, log, p);
    }

    static Ran counts(String program, int complete, int blocked) {
        Ran x = must(program);
        Run r = x.run();
        assertNull(r.thrown(), "the run threw: " + r.failure());
        assertNull(r.report().getErrorMessage(), "error: " + r.failure());
        assertEquals(complete, x.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(blocked, x.s().distinctBlockedGraphs(), "distinct blocked graphs");
        assertEquals(complete + blocked, x.s().iterations(), "iterations");
        assertTrue(x.s().exhausted(), "exhausted");
        return x;
    }

    static RanRandom randomOk(String program, int n, int blocked) {
        RanRandom x = random(program, n);
        assertNull(x.run().thrown(), "the run threw: " + x.run().failure());
        assertNull(x.run().report().getErrorMessage(), x.run().failure());
        assertEquals(n, x.run().report().getTotalIterations(), "iterations");
        assertEquals(blocked, x.run().report().getBlockedIterations(), "blocked iterations");
        return x;
    }

    static void rf(ExecutionGraph g, EventKey recv, EventKey send) {
        assertEquals(Optional.of(send), g.rf(recv), recv + " reads " + send + " in " + g);
    }

    static EventKey k(long t, int i) {
        return new EventKey(t, i);
    }

    // ------------------------------------------------------------------ addressing before start

    /** a (m/0, 2) sends to b (m/1, 3) through b's Pending tid, before b runs. Derived: 1 complete; b reads a. */
    @Test
    void peerAddressedBeforeStart_must() {
        Ran x = counts("addr_peer_before_start", 1, 0);
        rf(x.s().currentGraph(), k(3, 0), k(2, 0));
    }

    @Test
    void peerAddressedBeforeStart_mpRandom() {
        randomOk("addr_peer_before_start", 20, 0);
    }

    /**
     * create-all-then-start-all raises workaround (b)'s event sequence (§1): the same Must log as
     * {@code P4CharPrograms ctor_all_then_start}, and the same mp-random log for the same seed.
     */
    @Test
    void createThenStart_eventSequence_equalsWorkaroundB() {
        P4Must a = must("addr_peer_before_start").s();
        P4Must b = new P4Must(1L, DIR + "ctor_all_then_start");
        Run rb = J2cTest.check("ctor_all_then_start", J2cTest.main(new P4CharPrograms("ctor_all_then_start")),
                new JmcCheckerConfiguration.Builder().strategyType("must").strategyConstructor(c -> b)
                        .numIterations(10000).seed(1L).reportPath(DIR + "ctor_all_then_start"), b);
        assertNull(rb.thrown(), rb.failure());
        System.out.println("P4 create/start log " + a.log());
        assertEquals(b.log(), a.log(), "Must event sequence");
        EventLogStrategy la = random("addr_peer_before_start", 20).log();
        EventLogStrategy lb = new EventLogStrategy(1L, DIR + "random-ctor_all_then_start");
        J2cTest.check("ctor_all_then_start", J2cTest.main(new P4CharPrograms("ctor_all_then_start")),
                new JmcCheckerConfiguration.Builder().strategyType("mp-random").strategyConstructor(c -> lb)
                        .numIterations(20).seed(1L).reportPath(DIR + "random-ctor_all_then_start"), null);
        assertEquals(String.join("\n", lb.log()), String.join("\n", la.log()), "mp-random event sequence");
    }

    /** b names itself in a payload to a (created before b); a answers b. Derived: 1 complete. */
    @Test
    void tidInPayload_beforeStart_must_mpRandom() {
        Ran x = counts("addr_tid_payload", 1, 0);
        ExecutionGraph g = x.s().currentGraph();
        rf(g, k(2, 0), k(3, 0));
        rf(g, k(3, 1), k(2, 1));
        randomOk("addr_tid_payload", 20, 0);
    }

    /** Counting-only: main sends to p before p starts. Derived: 1 complete; p's receive (2,0) reads (1,0). */
    @Test
    void mainSendsBeforeStart_countingOnly() {
        Ran x = counts("addr_main_sends_first", 1, 0);
        rf(x.s().currentGraph(), k(2, 0), k(1, 0));
        randomOk("addr_main_sends_first", 20, 0);
    }

    /** Counting-only: a's send to b is raised and a awaited before b starts. Derived: 1 complete. */
    @Test
    void peerSendsBeforeTargetStarts_countingOnly() {
        Ran x = counts("addr_peer_sends_before_target_starts", 1, 0);
        rf(x.s().currentGraph(), k(3, 0), k(2, 0));
        randomOk("addr_peer_sends_before_target_starts", 20, 0);
    }

    /** Counting-only: a Pending created by a child (a = m/0, 2; c = m/0/0, 3). Derived: 1 complete. */
    @Test
    void pendingCreatedByChild_countingOnly() {
        Ran x = counts("addr_child_creates", 1, 0);
        rf(x.s().currentGraph(), k(3, 0), k(2, 0));
        randomOk("addr_child_creates", 20, 0);
    }

    /** create A, B; start and await A; then start and await B: accepted (the check is at the iteration's end). */
    @Test
    void startAwaitSequentially_accepted_bothStrategies() {
        counts("addr_start_await_sequential", 1, 0);
        randomOk("addr_start_await_sequential", 20, 0);
    }

    /** Counting-only: a daemon Pending addressed by main before it starts reads the message and finishes. */
    @Test
    void daemonPending_addressedBeforeStart_countingOnly() {
        counts("addr_daemon_pending", 1, 0);
        randomOk("addr_daemon_pending", 20, 0);
    }

    /** Export: create/start and workaround (b) give the same single summary under Must and the recorder. */
    @Test
    void export_createStart_equalsWorkaroundB_mustAndRecorder() {
        Run mc = J2cTest.mustExport(new AddressingExport("create"), "p4-addr-create");
        Run mo = J2cTest.mustExport(new AddressingExport("ctor"), "p4-addr-ctor");
        Rec rc = J2cTest.recorder(new AddressingExport("create"), "p4-addr-create", 10);
        Rec ro = J2cTest.recorder(new AddressingExport("ctor"), "p4-addr-ctor", 10);
        for (Run r : List.of(mc, mo, rc.run(), ro.run())) {
            assertNull(r.thrown(), r.failure());
            assertNull(r.report().getErrorMessage(), r.failure());
        }
        Set<String> want = J2cTest.lines(mo.s().summaries());
        System.out.println("P4 addressing export " + want);
        assertEquals(1, want.size(), "one summary");
        assertEquals(want, J2cTest.lines(mc.s().summaries()), "must, create/start");
        assertEquals(want, J2cTest.lines(rc.rec().summaries()), "recorder, create/start");
        assertEquals(want, J2cTest.lines(ro.rec().summaries()), "recorder, constructors");
    }

    // ------------------------------------------------------------------ §2 declaredRoles

    @Test
    void declaredRoles_defaultsToRolesInSpawnOrder() {
        MpProgram p = new MpProgram() {
            @Override
            public String programName() {
                return "x";
            }

            @Override
            public String size() {
                return "";
            }

            @Override
            public CommunicationModel model() {
                return CommunicationModel.P2P;
            }

            @Override
            public List<String> rolesInSpawnOrder() {
                return List.of("b", "a");
            }

            @Override
            public void run() {
            }
        };
        assertEquals(List.of("b", "a"), p.declaredRoles());
        assertEquals(List.of("b", "a"), p.visibleRoles());
        assertEquals(List.of("s1", "s2", "s3", "r"), new AssumePair("assume").declaredRoles());
    }

    // ------------------------------------------------------------------ refusals

    /** The events of one task in the log, in order (iteration prefix kept). */
    static List<String> eventsOf(List<String> log, long task) {
        return log.stream().filter(l -> l.matches("it=\\d+ \\S+ task=" + task + "( .*)?")).toList();
    }

    /**
     * Only the creator may start a Pending: a (task 3) starting main's b (task 2) is refused with the message
     * before any event or pause; a raises only its START and FINISH; b's single START comes after a's FINISH
     * (main starts it after awaiting a; counting-only). Must and mp-random.
     */
    @Test
    @Timeout(120)
    void startByNonCreator_refused_noEvent() {
        String msg = "process b was created by task 1; only its creator may start it";
        Ran x = counts("ref_non_creator", 1, 0);
        assertEquals(List.of(msg), x.p().refusals, "refusals");
        List<String> log = x.s().log();
        assertEquals(List.of("it=0 START_EVENT task=3 startedBy=1", "it=0 FINISH_EVENT task=3"), eventsOf(log, 3),
                "a's events: " + log);
        assertEquals(1, log.stream().filter(l -> l.startsWith("it=0 START_EVENT task=2")).count(), "b started once");
        assertTrue(log.indexOf("it=0 FINISH_EVENT task=3") < log.indexOf("it=0 START_EVENT task=2 startedBy=1"),
                "b's START after a's FINISH: " + log);
        RanRandom y = randomOk("ref_non_creator", 10, 0);
        assertEquals(10, y.p().refusals.size(), "refusals " + y.p().refusals);
        assertTrue(y.p().refusals.stream().allMatch(msg::equals), "refusals " + y.p().refusals);
        for (int it = 0; it < 10; it++) {
            String pre = "it=" + it + " ";
            List<String> a = eventsOf(y.log().log(), 3).stream().filter(l -> l.startsWith(pre)).toList();
            assertEquals(List.of(pre + "START_EVENT task=3 startedBy=1", pre + "FINISH_EVENT task=3"), a,
                    "a's events in iteration " + it);
        }
    }

    /** A second start is refused; the process is started once. */
    @Test
    void secondStart_refused() {
        Ran x = counts("ref_second_start", 1, 0);
        assertEquals(List.of("process a was already started"), x.p().refusals);
        assertEquals(1, x.s().log().stream().filter(l -> l.startsWith("it=0 START_EVENT task=2")).count());
        RanRandom y = randomOk("ref_second_start", 5, 0);
        assertEquals(5, y.p().refusals.size());
        assertTrue(y.p().refusals.stream().allMatch("process a was already started"::equals), "" + y.p().refusals);
    }

    /** start(null): NullPointerException("body") before the one-shot start is used; start(body) then succeeds. */
    @Test
    void startNull_refused_thenValidStartSucceeds() {
        Ran x = counts("ref_null_body", 1, 0);
        assertEquals(List.of("NPE body"), x.p().refusals);
        assertEquals(1, x.s().log().stream().filter(l -> l.startsWith("it=0 START_EVENT task=2")).count());
        RanRandom y = randomOk("ref_null_body", 5, 0);
        assertTrue(y.p().refusals.stream().allMatch("NPE body"::equals), "" + y.p().refusals);
    }

    static final String STALE = "process a belongs to an earlier execution";
    static final List<String> STALE_BOTH = List.of("tid: " + STALE, "start: " + STALE);

    /**
     * A Pending of an earlier iteration: tid() and start() are refused (Must: the nondet's second iteration;
     * derived 2 complete). Assumption, from P4-PROPOSAL §1 and the lead's message: on a stale Pending that was
     * started, start() reports the stale message (the staleness check precedes the one-shot check).
     */
    @Test
    void stalePending_laterIteration_refused() {
        Ran x = counts("stale_iteration", 2, 0);
        assertEquals(STALE_BOTH, x.p().refusals, "Must, iteration 1");
        RanRandom y = randomOk("stale_iteration", 3, 0);
        assertEquals(List.of(STALE_BOTH.get(0), STALE_BOTH.get(1), STALE_BOTH.get(0), STALE_BOTH.get(1)),
                y.p().refusals, "mp-random, iterations 1 and 2");
    }

    /** A Pending of an earlier run (the same JVM): tid() and start() refused in the next run, both strategies. */
    @Test
    void stalePending_laterRun_refused() {
        for (String strategy : List.of("must", "mp-random")) {
            P4Programs.clearPreviousRun();
            Run r1 = strategy.equals("must") ? must("stale_run_1").run() : random("stale_run_1", 1).run();
            assertNull(r1.thrown(), r1.failure());
            if (strategy.equals("must")) {
                Ran x = counts("stale_run_2", 1, 0);
                assertEquals(STALE_BOTH, x.p().refusals, strategy);
            } else {
                RanRandom y = randomOk("stale_run_2", 1, 0);
                assertEquals(STALE_BOTH, y.p().refusals, strategy);
            }
        }
        P4Programs.clearPreviousRun();
    }

    /** Never started, each way the iteration can end, under Must and mp-random: the iteration fails. */
    static void neverStarted(String program, String message) {
        Ran x = must(program);
        J2cTest.fails(x.run(), "must: " + message);
        assertFalse(x.p().afterAssumeRan, "no statement after a failed assume ran");
        RanRandom y = random(program, 3);
        J2cTest.fails(y.run(), message);
        assertFalse(y.p().afterAssumeRan, "no statement after a failed assume ran");
    }

    @Test
    @Timeout(120)
    void neverStarted_mainEndsNormally() {
        neverStarted("never_started_normal", "process a (task 2) was created by create() but never started");
    }

    @Test
    @Timeout(120)
    void neverStarted_mainBlockedAtAssume() {
        neverStarted("never_started_assume", "process a (task 2) was created by create() but never started");
    }

    @Test
    @Timeout(120)
    void neverStarted_mainBlockedAtAwait() {
        neverStarted("never_started_await", "process a (task 2) was created by create() but never started");
    }

    @Test
    @Timeout(120)
    void neverStarted_duringDaemonDrain() {
        neverStarted("never_started_drain", "process a (task 2) was created by create() but never started");
    }

    @Test
    @Timeout(120)
    void neverStarted_daemonPending() {
        neverStarted("never_started_daemon", "process task 2 (task 2) was created by create() but never started");
    }

    static final String NO_TASK = "a send names task 99, which no process of this execution has; use the Tid of a"
            + " Pending or Handle (MpProcess.create/spawn)";

    /** Behaviour change (Must: the message reworded). Characterised by P4CharTest#send99_must_today. */
    @Test
    void send99_refused_must() {
        P4Must s = new P4Must(1L, DIR + "send_99");
        Run r = J2cTest.check("send_99", J2cTest.main(new P4CharPrograms("send_99")),
                new JmcCheckerConfiguration.Builder().strategyType("must").strategyConstructor(c -> s)
                        .numIterations(10000).seed(1L).reportPath(DIR + "send_99"), s);
        J2cTest.fails(r, "must: " + NO_TASK);
    }

    /** Behaviour change (mp-random: newly refused). Characterised by P4CharTest#send99_mpRandom_buffered_today. */
    @Test
    @Timeout(120)
    void send99_refused_mpRandom() {
        Run r = J2cTest.check("send_99", J2cTest.main(new P4CharPrograms("send_99")),
                new JmcCheckerConfiguration.Builder().strategyType("mp-random").numIterations(5).seed(1L)
                        .reportPath(DIR + "random-send_99"), null);
        J2cTest.fails(r, NO_TASK);
    }

    // ------------------------------------------------------------------ round 03 (Codex code review 01)

    /**
     * P2a: under mp-random a send to a JmcThread (which raises START but no TASK_CREATED) is valid: no error, no
     * blocked iteration. The refusal of a send to a task no process has still holds (Tid.of(99)). Fails before
     * the lead's fix (the set of destinations was filled by TASK_CREATED only).
     */
    @Test
    @Timeout(120)
    void jmcThreadDestination_valid_send99StillRefused_mpRandom() {
        RanRandom x = randomOk("jt_send", 20, 0);
        assertEquals(20, x.log().log().stream().filter(l -> l.matches("it=\\d+ MP_SEND_EVENT task=3 dst=2")).count(),
                "one send from s (task 3) to j (task 2) per iteration: " + x.log().log());
        Run r = J2cTest.check("send_99", J2cTest.main(new P4CharPrograms("send_99")),
                new JmcCheckerConfiguration.Builder().strategyType("mp-random").numIterations(5).seed(1L)
                        .reportPath(DIR + "random-send_99-r3"), null);
        J2cTest.fails(r, NO_TASK);
    }

    /**
     * P2b: a never-started Pending with main ending by core JmcAssume.assume(false) (no FINISH, no parked main):
     * the iteration fails with the never-started message under mp-random (fails before the lead's fix) and Must.
     */
    @Test
    @Timeout(120)
    void neverStarted_mainEndsByCoreAssume() {
        neverStarted("never_started_core_assume", "process a (task 2) was created by create() but never started");
    }

    /**
     * Codex P3 (4): the TASK_CREATED parameters of every creation path. No {@code pending} key for spawn,
     * spawnDaemon and the three public MpThread constructors; {@code pending=true} for create and createDaemon;
     * role and daemon as given (tasks 2..8 in creation order).
     */
    @Test
    void creationParameters_everyPath() {
        Ran x = counts("creation_kinds", 1, 0);
        System.out.println("P4 creations " + x.s().creations());
        assertEquals(List.of(
                "it=0 createdTask=2 role=a daemon=false pending=<absent>",
                "it=0 createdTask=3 role= daemon=true pending=<absent>",
                "it=0 createdTask=4 role= daemon=false pending=<absent>",
                "it=0 createdTask=5 role=c daemon=false pending=<absent>",
                "it=0 createdTask=6 role= daemon=true pending=<absent>",
                "it=0 createdTask=7 role=e daemon=false pending=true",
                "it=0 createdTask=8 role= daemon=true pending=true"), x.s().creations());
    }
}
