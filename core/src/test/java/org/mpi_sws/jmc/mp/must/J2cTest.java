package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.programs.j2c.DaemonRoles;
import org.mpi_sws.jmc.mp.programs.j2c.J2cPrograms;
import org.mpi_sws.jmc.mp.programs.seq.EventLogStrategy;
import org.mpi_sws.jmc.mp.programs.seq.SeqMpOk;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.strategies.mp.MpRecordingStrategy;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tester tests for J2c round 01 (daemon processes; plan/jmc-must/log/dev/J2c/round-01.prompt.md): the
 * owner's rulings 1-7, Q-J2c-1 and the runMain condition, derived before running (scratchpad
 * j2c/derivations.txt; the report has the derivations). Programs run as {@code MpProcess.runMain(p)},
 * as the launcher does, unless a test says otherwise.
 */
class J2cTest {

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

    static Run check(String name, Runnable target, JmcCheckerConfiguration.Builder b, MustStrategy s) {
        JmcModelCheckerReport report = null;
        Throwable thrown = null;
        try {
            report = new JmcModelChecker(b.build()).check(new JmcFunctionalTestTarget(name, target::run));
        } catch (Throwable t) {
            thrown = t;
        }
        Run r = new Run(s, report, thrown);
        System.out.println("J2C " + name + (s == null ? "" : " complete=" + s.distinctCompleteGraphs() + " blocked="
                + s.distinctBlockedGraphs() + " iterations=" + s.iterations() + " exhausted=" + s.exhausted())
                + " total=" + (report == null ? "-" : report.getTotalIterations())
                + " reportBlocked=" + (report == null ? "-" : report.getBlockedIterations())
                + " failure=" + r.failure());
        return r;
    }

    static Runnable main(Runnable p) {
        return () -> MpProcess.runMain(p);
    }

    static Run must(String program) {
        String path = "build/test-results/j2c-tester/" + program;
        MustStrategy s = new MustStrategy(1L, path);
        return check(program, main(new J2cPrograms(program)), new JmcCheckerConfiguration.Builder()
                .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(path), s);
    }

    static Run mpRandom(String program, int n) {
        return check(program, main(new J2cPrograms(program)), new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").numIterations(n).seed(1L)
                .reportPath("build/test-results/j2c-tester/random-" + program), null);
    }

    static Run mustExport(MpProgram p, String tag) {
        String path = "build/test-results/j2c-tester/export-" + tag;
        MustStrategy s = new MustStrategy(1L, path, p.rolesInSpawnOrder(), p.visibleRoles());
        return check(tag, main(p), new JmcCheckerConfiguration.Builder()
                .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(path), s);
    }

    record Rec(MpRecordingStrategy rec, Run run) {}

    static Rec recorder(MpProgram p, String tag, int n) {
        String path = "build/test-results/j2c-tester/rec-" + tag;
        MpRecordingStrategy rec = new MpRecordingStrategy(1L, path, p.rolesInSpawnOrder(), p.visibleRoles());
        return new Rec(rec, check(tag, main(p), new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").strategyConstructor(c -> rec).numIterations(n).seed(1L).reportPath(path),
                null));
    }

    static Run counts(String program, int complete, int blocked) {
        Run r = must(program);
        assertEquals(null, r.thrown(), "the run threw: " + r.failure());
        assertEquals(complete, r.s().distinctCompleteGraphs(), "distinct complete graphs");
        assertEquals(blocked, r.s().distinctBlockedGraphs(), "distinct blocked graphs");
        assertEquals(complete + blocked, r.s().iterations(), "iterations == complete + blocked");
        assertTrue(r.s().exhausted(), "exhausted");
        return r;
    }

    static void randomOk(String program, int blockedIterations) {
        Run r = mpRandom(program, 20);
        assertEquals(null, r.thrown(), "the run threw: " + r.failure());
        assertEquals(20, r.report().getTotalIterations(), "iterations");
        assertEquals(blockedIterations, r.report().getBlockedIterations(), "blocked iterations");
    }

    static void fails(Run r, String needle) {
        assertTrue(r.thrown() != null || (r.report() != null && r.report().getErrorMessage() != null),
                "the run must fail");
        assertTrue(r.failure().contains(needle), "failure mentions '" + needle + "': " + r.failure());
    }

    static Set<String> lines(java.util.Map<Summary, Integer> m) {
        Set<String> s = new TreeSet<>();
        m.keySet().forEach(k -> s.add(k.toJson(0).replaceAll(",\"n\":0}$", "}")));
        return s;
    }

    // ------------------------------------------------------------------ ruling 1: classification

    @Test
    void r1_daemonBlockedOnReceive_complete_must() {
        counts("r1_daemon_recv", 1, 0);
    }

    @Test
    void r1_daemonBlockedOnReceive_complete_mpRandom() {
        randomOk("r1_daemon_recv", 0);
    }

    @Test
    void r1_daemonAwaitsBlocked_blocked_must() {
        counts("r1_daemon_await_blocked", 0, 1);
    }

    @Test
    void r1_daemonAwaitsBlocked_blocked_mpRandom() {
        randomOk("r1_daemon_await_blocked", 20);
    }

    @Test
    void r1_nonDaemonBlockedOnReceive_blocked_must() {
        counts("r1_nondaemon_recv", 0, 1);
    }

    @Test
    void r1_mixedComplete_must() {
        counts("r1_mixed_complete", 1, 0);
    }

    @Test
    void r1_mixedComplete_mpRandom() {
        randomOk("r1_mixed_complete", 0);
    }

    @Test
    void r1_mixedBlocked_must() {
        counts("r1_mixed_blocked", 0, 1);
    }

    // ------------------------------------------------------------------ ruling 2: after main's body

    @Test
    void r2_daemonFinishesAfterMain_must() {
        Run r = counts("r2_daemon_finishes", 1, 0);
        ExecutionGraph g = r.s().currentGraph();
        System.out.println("J2C r2 finishes: " + g);
        assertEquals(java.util.Optional.of(new EventKey(1L, 0)), g.rf(new EventKey(2L, 0)), "D reads main's send");
    }

    @Test
    void r2_daemonBlocksAfterMain_must() {
        counts("r2_daemon_blocks", 1, 0);
    }

    @Test
    void r2_daemonToDaemonAfterMain_must() {
        Run r = counts("r2_daemon_to_daemon", 1, 0);
        ExecutionGraph g = r.s().currentGraph();
        System.out.println("J2C r2 d2d: " + g);
        // D2 created first (stable 2), D1 second (3): D2's receive reads D1's send (3,1)
        assertEquals(java.util.Optional.of(new EventKey(3L, 1)), g.rf(new EventKey(2L, 0)), "D2 reads D1's send");
    }

    @Test
    void r2_afterMain_mpRandom() {
        randomOk("r2_daemon_finishes", 0);
        randomOk("r2_daemon_blocks", 0);
        randomOk("r2_daemon_to_daemon", 0);
    }

    // ------------------------------------------------------------------ ruling 3: daemons are in the graph

    @Test
    void r3_onlyDaemonNondetDiffers_2() {
        counts("r3_daemon_nondet", 2, 0);
    }

    @Test
    void r3_onlyDaemonRfDiffers_2() {
        counts("r3_daemon_rf", 2, 0);
    }

    // ------------------------------------------------------------------ ruling 5: a daemon is not awaitable

    @Test
    void r5_awaitDaemon_refused() {
        fails(must("r5_await_daemon"), "is a daemon, which is not awaitable");
        fails(mpRandom("r5_await_daemon", 3), "is a daemon, which is not awaitable");
    }

    @Test
    void r5_awaitDaemon_noEvent() {
        // the refusal raises no event: no JOIN event of main in the log, and the program goes on
        J2cPrograms p = new J2cPrograms("r5_await_daemon_caught");
        EventLogStrategy log = new EventLogStrategy(1L, "build/test-results/j2c-tester/r5-log");
        check("r5_log", main(p), new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .strategyConstructor(c -> log).numIterations(1).seed(1L)
                .reportPath("build/test-results/j2c-tester/r5-log"), null);
        System.out.println("J2C r5 log " + log.log());
        assertTrue(String.valueOf(p.refusal).contains("is a daemon, which is not awaitable"), "refusal: " + p.refusal);
        assertTrue(log.log().stream().noneMatch(l -> l.contains("JOIN_")), "no JOIN event: " + log.log());
    }

    // ------------------------------------------------------------------ ruling 6: a daemon's failure

    @Test
    void r6_daemonFailure_must() {
        fails(must("r6_daemon_fails"), "must: process m/0 (task 2) failed: java.lang.IllegalStateException: d fails");
    }

    @Test
    void r6_daemonFailure_mpRandom() {
        fails(mpRandom("r6_daemon_fails", 3), "process 2 failed: java.lang.IllegalStateException: d fails");
    }

    // ------------------------------------------------------------------ ruling 7: sends to daemons

    @Test
    void r7_daemonNonBlocking_revisit_2() {
        counts("r7_daemon_nb", 2, 0);
    }

    @Test
    void r7_daemonUnread_1() {
        counts("r7_daemon_unread", 1, 0);
    }

    // ------------------------------------------------------------------ ruling 4 and Q-J2c-1: export

    private static final String V1 = "{\"t\":\"V\",\"f\":[1]}";
    private static final String AB = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":[{\"s\":" + V1
            + "}],\"b\":[{\"r\":" + V1 + "}]},\"counts\":[0,1]}";

    @Test
    void r4_rolesSkipDaemons_must() {
        Run with = mustExport(new DaemonRoles(true, false), "with");
        Run without = mustExport(new DaemonRoles(false, false), "without");
        assertEquals(null, with.thrown(), with.failure());
        assertEquals(null, without.thrown(), without.failure());
        System.out.println("J2C r4 must with " + lines(with.s().summaries()) + " without "
                + lines(without.s().summaries()));
        assertEquals(Set.of(AB), lines(without.s().summaries()), "without daemons");
        assertEquals(lines(without.s().summaries()), lines(with.s().summaries()), "daemons change no summary");
    }

    @Test
    void r4_rolesSkipDaemons_recorder() {
        Rec with = recorder(new DaemonRoles(true, false), "with", 20);
        Rec without = recorder(new DaemonRoles(false, false), "without", 20);
        assertEquals(null, with.run().thrown(), with.run().failure());
        assertEquals(null, without.run().thrown(), without.run().failure());
        System.out.println("J2C r4 rec with " + lines(with.rec().summaries()) + " without "
                + lines(without.rec().summaries()));
        assertEquals(Set.of(AB), lines(without.rec().summaries()), "without daemons");
        assertEquals(lines(without.rec().summaries()), lines(with.rec().summaries()), "daemons change no summary");
    }

    private static final String Q1 = "a thread id is encoded as a role name and a daemon has none";

    @Test
    void q1_payloadNamesDaemon_refused_must() {
        Run r = mustExport(new DaemonRoles(true, true), "payload");
        fails(r, Q1);
        assertEquals(0, r.s().summaries().size(), "nothing exported");
    }

    @Test
    void q1_payloadNamesDaemon_refused_recorder() {
        Rec r = recorder(new DaemonRoles(true, true), "payload", 5);
        fails(r.run(), Q1);
        assertEquals(0, r.rec().summaries().size(), "nothing exported");
    }

    // ------------------------------------------------------------------ runMain

    @Test
    void runMain_noDaemons_noNewEvent() {
        // owner's condition: a program without daemons run through runMain raises exactly the events of
        // its plain run (same seed, same schedule)
        EventLogStrategy a = new EventLogStrategy(7L, "build/test-results/j2c-tester/seq-plain");
        EventLogStrategy b = new EventLogStrategy(7L, "build/test-results/j2c-tester/seq-runmain");
        SeqMpOk p = new SeqMpOk();
        check("plain", p::run, new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .strategyConstructor(c -> a).numIterations(50).seed(7L)
                .reportPath("build/test-results/j2c-tester/seq-plain"), null);
        check("runMain", main(new SeqMpOk()), new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .strategyConstructor(c -> b).numIterations(50).seed(7L)
                .reportPath("build/test-results/j2c-tester/seq-runmain"), null);
        assertTrue(a.log().size() > 50 * 10, "events: " + a.log().size());
        assertEquals(String.join("\n", a.log()), String.join("\n", b.log()), "identical event sequences");
    }

    @Test
    @Timeout(120)
    void noRunMain_mpRandom_refusedByLiveRule() {
        J2cPrograms p = new J2cPrograms("no_runmain");
        Run r = check("no_runmain", p::run, new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .numIterations(5).seed(1L).reportPath("build/test-results/j2c-tester/norunmain-random"), null);
        fails(r, "were still live");
    }

    @Test
    @Timeout(120)
    void noRunMain_must_refused() {
        // J2c-F2 fixed (round 02): must refuses a daemon program that did not end through runMain, and
        // does not hang
        J2cPrograms p = new J2cPrograms("no_runmain");
        String path = "build/test-results/j2c-tester/norunmain-must";
        MustStrategy s = new MustStrategy(1L, path);
        Run r = check("no_runmain_must", p::run, new JmcCheckerConfiguration.Builder().strategyType("must")
                .strategyConstructor(c -> s).numIterations(100).seed(1L).reportPath(path), s);
        fails(r, "daemon(s) were spawned but main did not end through MpProcess.runMain; daemons would outlive"
                + " the iteration");
        assertEquals(0, s.distinctCompleteGraphs() + s.distinctBlockedGraphs(), "nothing counted");
    }

    // ------------------------------------------------------------------ round 02: roles by name (Q-J2c-3)

    private static final String CLI_SRV = "{\"statuses\":{\"cli\":\"done\",\"srv\":\"done\"},\"words\":{\"cli\":"
            + "[{\"r\":" + V1 + "}],\"srv\":[{\"s\":" + V1 + "}]},\"counts\":[1,0]}";

    @Test
    void owner_swappedOrder_sameExportBothStrategies() {
        // StartOrderRoles creates cli, then srv, and starts srv first; roles come from the names, so
        // must and the recorder export the same summary: cli receives, srv sends
        Run m = mustExport(new org.mpi_sws.jmc.mp.programs.j2b.StartOrderRoles(), "start-order");
        Rec r = recorder(new org.mpi_sws.jmc.mp.programs.j2b.StartOrderRoles(), "start-order", 10);
        System.out.println("J2C start-order must " + lines(m.s().summaries()) + " recorder "
                + lines(r.rec().summaries()) + " recorder failure " + r.run().failure());
        assertEquals(null, m.thrown(), m.failure());
        assertEquals(null, r.run().thrown(), r.run().failure());
        assertEquals(Set.of(CLI_SRV), lines(m.s().summaries()), "must");
        assertEquals(Set.of(CLI_SRV), lines(r.rec().summaries()), "recorder");
    }

    private static final String NAMED_AB = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":"
            + "[{\"s\":" + V1 + "}],\"b\":[{\"r\":" + V1 + "}]},\"counts\":[0,1]}";

    @Test
    void roles_spawnOrderDiffers_sameSummaries() {
        Run mab = mustExport(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles("ab"), "named-ab");
        Run mba = mustExport(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles("ba"), "named-ba");
        Rec rab = recorder(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles("ab"), "named-ab", 10);
        Rec rba = recorder(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles("ba"), "named-ba", 10);
        System.out.println("J2C named must " + lines(mab.s().summaries()) + " / " + lines(mba.s().summaries())
                + " recorder " + lines(rab.rec().summaries()) + " / " + lines(rba.rec().summaries()));
        for (Run x : List.of(mab, mba, rab.run(), rba.run())) {
            assertEquals(null, x.thrown(), x.failure());
        }
        assertEquals(Set.of(NAMED_AB), lines(mab.s().summaries()), "must, a then b");
        assertEquals(Set.of(NAMED_AB), lines(mba.s().summaries()), "must, b then a");
        assertEquals(Set.of(NAMED_AB), lines(rab.rec().summaries()), "recorder, a then b");
        assertEquals(Set.of(NAMED_AB), lines(rba.rec().summaries()), "recorder, b then a");
    }

    static void refused(String variant, String mustNeedle, String recNeedle) {
        Run m = mustExport(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles(variant), "refuse-" + variant);
        fails(m, mustNeedle);
        assertEquals(0, m.s().summaries().size(), "must exports nothing");
        Rec r = recorder(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles(variant), "refuse-" + variant, 3);
        fails(r.run(), recNeedle);
        assertEquals(0, r.rec().summaries().size(), "the recorder exports nothing");
    }

    @Test
    void roles_noRole_refused() {
        // b (created first: stable 2, runtime 2) has no role
        refused("norole", "process m/0 has no role; an exported program spawns every process with MpProcess.spawn",
                "process 2 has no role; an exported program spawns every process with MpProcess.spawn");
    }

    @Test
    void roles_undeclared_refused() {
        refused("undeclared", "has role b, which is not declared [a]", "has role b, which is not declared [a]");
    }

    @Test
    void roles_twice_refused() {
        refused("twice", "role a is spawned more than once", "role a is spawned more than once");
    }

    @Test
    void roles_neverSpawned_refused() {
        refused("missing", "declared role c was never spawned", "declared role c was never spawned");
    }

    @Test
    void roles_twiceHidden_refused() {
        // an invisible role spawned twice: only the declared-list check can see it
        refused("twice_hidden", "role h is spawned more than once", "role h is spawned more than once");
    }

    @Test
    void roles_neverSpawnedHidden_refused() {
        refused("missing_hidden", "declared role h was never spawned", "declared role h was never spawned");
    }

    // ------------------------------------------------------------------ round 03: review M1

    static void randomBlocked(String program, int n) {
        Run r = mpRandom(program, n);
        assertEquals(null, r.thrown(), "no error: " + r.failure());
        assertEquals(n, r.report().getBlockedIterations(), "every iteration blocked");
    }

    @Test
    void m1_daemonFinished_mainAwaitsBlocked_must() {
        counts("m1_daemon_finished_await_blocked", 0, 1);
    }

    @Test
    void m1_daemonFinished_mainAwaitsBlocked_mpRandom() {
        randomBlocked("m1_daemon_finished_await_blocked", 10);
    }

    @Test
    void m1_daemonRecvBlocked_mainAwaitsBlocked_must() {
        counts("m1_daemon_recv_await_blocked", 0, 1);
    }

    @Test
    void m1_daemonRecvBlocked_mainAwaitsBlocked_mpRandom() {
        randomBlocked("m1_daemon_recv_await_blocked", 10);
    }

    @Test
    void m1_daemonRunsToEnd_beforeMainBlocks_must() {
        Run r = counts("m1_daemon_runs_to_end", 0, 1);
        ExecutionGraph g = r.s().currentGraph();
        System.out.println("J2C m1 runs to end: " + g);
        // P = m/0 (2), D = m/1 (3): D's four events, the last a receive (no Block for D)
        assertEquals(4, g.thread(3L).size(), "D's events");
        assertTrue(g.thread(3L).get(3).label() instanceof org.mpi_sws.jmc.mp.events.MpLabel.Recv, "D ended normally");
    }

    @Test
    void m1_daemonRunsToEnd_mpRandom() {
        randomBlocked("m1_daemon_runs_to_end", 10);
    }

    private static final String P_BLOCKED = "{\"statuses\":{\"p\":\"blocked\"},\"words\":{\"p\":[]},\"counts\":[]}";

    @Test
    void m1_export_mainAwaitsBlocked_withDaemon() {
        for (String v : List.of("await_blocked_finished", "await_blocked_recv")) {
            Run m = mustExport(new org.mpi_sws.jmc.mp.programs.j2c.M2Roles(v), "m1-" + v);
            Rec r = recorder(new org.mpi_sws.jmc.mp.programs.j2c.M2Roles(v), "m1-" + v, 5);
            System.out.println("J2C m1 export " + v + " must " + lines(m.s().summaries()) + " recorder "
                    + lines(r.rec().summaries()));
            assertEquals(null, m.thrown(), v + ": " + m.failure());
            assertEquals(null, r.run().thrown(), v + ": " + r.run().failure());
            assertEquals(Set.of(P_BLOCKED), lines(m.s().summaries()), v + ", must");
            assertEquals(Set.of(P_BLOCKED), lines(r.rec().summaries()), v + ", recorder");
        }
    }

    // ------------------------------------------------------------------ round 03: review M2

    @Test
    void m2_reservedDeclaredRoles_refusedByConstructors() {
        for (String bad : List.of("main", "", "~x", "~daemon m/0")) {
            List<String> roles = List.of("a", bad);
            IllegalArgumentException em = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new MustStrategy(1L, null, roles, roles), "must refuses " + bad);
            IllegalArgumentException er = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new MpRecordingStrategy(1L, null, roles, roles), "recorder refuses " + bad);
            System.out.println("J2C m2 '" + bad + "': " + em.getMessage() + " / " + er.getMessage());
            assertTrue(em.getMessage().contains("is reserved") && er.getMessage().contains("is reserved"),
                    em.getMessage() + " / " + er.getMessage());
        }
    }

    @Test
    void m2_processNamedMain_refusedAsUndeclared() {
        Run m = mustExport(new org.mpi_sws.jmc.mp.programs.j2c.M2Roles("named_main"), "named-main");
        fails(m, "has role main, which is not declared [a]");
        Rec r = recorder(new org.mpi_sws.jmc.mp.programs.j2c.M2Roles("named_main"), "named-main", 3);
        fails(r.run(), "has role main, which is not declared [a]");
    }

    @Test
    void m2_processNamedTilde_refusedAsUndeclared() {
        Run m = mustExport(new org.mpi_sws.jmc.mp.programs.j2c.M2Roles("named_tilde"), "named-tilde");
        fails(m, "has role ~x, which is not declared [a]");
        Rec r = recorder(new org.mpi_sws.jmc.mp.programs.j2c.M2Roles("named_tilde"), "named-tilde", 3);
        fails(r.run(), "has role ~x, which is not declared [a]");
    }

    @Test
    void m2_daemonProgram_summaryHasOnlyDeclaredRoles() {
        // DaemonRoles (two daemons, roles b and a): every summary names exactly a and b
        Run m = mustExport(new DaemonRoles(true, false), "m2-daemons");
        Rec r = recorder(new DaemonRoles(true, false), "m2-daemons", 10);
        for (Summary s : m.s().summaries().keySet()) {
            String j = s.toJson(1);
            assertTrue(j.startsWith("{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":"),
                    "must: " + j);
        }
        for (Summary s : r.rec().summaries().keySet()) {
            String j = s.toJson(1);
            assertTrue(j.startsWith("{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":"),
                    "recorder: " + j);
        }
        assertEquals(1, m.s().summaries().size(), "one summary (must)");
        assertEquals(1, r.rec().summaries().size(), "one summary (recorder)");
    }

    // ------------------------------------------------------------------ round 03: review m1 and NamedRoles

    @Test
    void m1ctor_daemonByConstructor_sameAsSpawnDaemon() {
        counts("m1_ctor_daemon_recv", 1, 0);
        EventLogStrategy a = new EventLogStrategy(1L, "build/test-results/j2c-tester/ctor-log");
        EventLogStrategy b = new EventLogStrategy(1L, "build/test-results/j2c-tester/spawnd-log");
        check("ctor_log", main(new J2cPrograms("m1_ctor_daemon_recv")), new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").strategyConstructor(c -> a).numIterations(5).seed(1L)
                .reportPath("build/test-results/j2c-tester/ctor-log"), null);
        check("spawnd_log", main(new J2cPrograms("r1_daemon_recv")), new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").strategyConstructor(c -> b).numIterations(5).seed(1L)
                .reportPath("build/test-results/j2c-tester/spawnd-log"), null);
        System.out.println("J2C ctor log " + a.log());
        assertTrue(a.log().stream().anyMatch(l -> l.matches("it=0 MP_RECV_REQUEST_EVENT task=1")), "final request");
        assertEquals(String.join("\n", b.log()), String.join("\n", a.log()), "same events as spawnDaemon");
    }

    @Test
    void roles_creationOrderDiffers_sameSummaries() {
        // Codex's note: NamedRoles "createAB" creates a before b (the others create b first)
        Run m = mustExport(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles("createAB"), "named-createAB");
        Rec r = recorder(new org.mpi_sws.jmc.mp.programs.j2c.NamedRoles("createAB"), "named-createAB", 10);
        assertEquals(null, m.thrown(), m.failure());
        assertEquals(null, r.run().thrown(), r.run().failure());
        assertEquals(Set.of(NAMED_AB), lines(m.s().summaries()), "must");
        assertEquals(Set.of(NAMED_AB), lines(r.rec().summaries()), "recorder");
    }
}
