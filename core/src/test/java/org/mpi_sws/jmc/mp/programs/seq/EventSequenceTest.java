package org.mpi_sws.jmc.mp.programs.seq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * Tester tests for J1 round 04, item 1 (event sequence): the events a strategy sees for a program
 * spawning through {@code MpProcess} (over {@code MpThread}) against the same program through a
 * private copy over {@code JmcThread} ({@link JmcThreadProcess}), same seed. Expected: identical
 * without a failure; with a failure in a process, {@code JmcThread} adds a {@code HALT_EVENT} from
 * the dying thread, and (my derivation) {@code MpThread}'s rethrow at the joiner ends main early, so
 * the {@code MpThread} log is a prefix of the {@code JmcThread} log without its HALT events.
 */
class EventSequenceTest {

    private static final long SEED = 20261027L;

    record Result(List<String> log, List<String> threads, Throwable thrown) {}

    static Result run(Runnable p, int n, long seed) throws Exception {
        EventLogStrategy s = new EventLogStrategy(seed, "build/test-results/j1-tester/seq/" + p.getClass().getSimpleName());
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random")
                .strategyConstructor(c -> s)
                .numIterations(n)
                .seed(seed)
                .reportPath("build/test-results/j1-tester/seq/" + p.getClass().getSimpleName())
                .build();
        Throwable thrown = null;
        try {
            new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
        } catch (Throwable t) {
            thrown = t;
        }
        List<String> log = s.log();
        System.out.println("SEQ " + p.getClass().getSimpleName() + " n=" + n + " events=" + log.size()
                + " thrown=" + (thrown == null ? "none" : thrown.getClass().getSimpleName() + ": " + thrown.getMessage()));
        return new Result(log, s.threads(), thrown);
    }

    private static void dump(String name, Result r) {
        List<String> th = r.threads();
        for (int i = 0; i < r.log().size(); i++) {
            System.out.println("LOG " + name + " " + i + " " + r.log().get(i) + "   [" + th.get(i) + "]");
        }
    }

    @Test
    void seq_noFailure_identical() throws Exception {
        Result mp = run(new SeqMpOk(), 300, SEED);
        Result jt = run(new SeqJtOk(), 300, SEED);
        assertEquals(null, mp.thrown(), "MpThread run threw");
        assertEquals(null, jt.thrown(), "JmcThread run threw");
        assertTrue(mp.log().size() > 300 * 10, "events recorded: " + mp.log().size());
        for (int i = 0; i < 40; i++) {
            System.out.println("LOG SeqMpOk " + i + " " + mp.log().get(i));
        }
        // J2b round 03 (rework R1): MpThread's constructor raises one TASK_CREATED_EVENT on the creator's
        // thread (main, task=1), with no yield, so the schedule is unchanged. Derived: three per
        // iteration (t3, t2, t1), and the sequences are equal once they are removed.
        long created = mp.log().stream().filter(l -> l.contains(" TASK_CREATED_EVENT ")).count();
        assertEquals(300 * 3, created, "one TASK_CREATED_EVENT per MpThread creation");
        assertTrue(mp.log().stream().filter(l -> l.contains(" TASK_CREATED_EVENT "))
                .allMatch(l -> l.matches("it=\\d+ TASK_CREATED_EVENT task=1")), "raised on main's thread, as main");
        assertTrue(jt.log().stream().noneMatch(l -> l.contains("TASK_CREATED_EVENT")), "JmcThread raises none");
        assertEquals(String.join("\n", jt.log()), String.join("\n", withoutCreated(mp.log())),
                "event sequences equal except for TASK_CREATED_EVENT");
    }

    static List<String> withoutCreated(List<String> log) {
        List<String> out = new ArrayList<>(log);
        out.removeIf(l -> l.contains(" TASK_CREATED_EVENT "));
        return out;
    }

    @Test
    void seq_noFailure_detects_differentProgram() throws Exception {
        // the comparison is sensitive: the failing variant's sequence differs from the clean one
        Result ok = run(new SeqMpOk(), 1, SEED);
        Result fail = run(new SeqMpFail(), 1, SEED);
        assertNotEquals(String.join("\n", ok.log()), String.join("\n", fail.log()));
    }

    @Test
    void seq_failure_onlyHaltAndEarlyEnd() throws Exception {
        Result mp = run(new SeqMpFail(), 1, SEED);
        Result jt = run(new SeqJtFail(), 1, SEED);
        dump("MpThread", mp);
        dump("JmcThread", jt);
        assertTrue(mp.thrown() != null, "MpThread: the run fails");
        assertEquals(null, jt.thrown(), "JmcThread: the failure is swallowed and the run completes (F1)");
        assertTrue(mp.log().stream().noneMatch(l -> l.contains("HALT_EVENT")), "MpThread emits no HALT_EVENT");
        List<String> halts = jt.log().stream().filter(l -> l.contains("HALT_EVENT")).toList();
        System.out.println("JmcThread HALT events: " + halts);
        assertEquals(1, halts.size(), "JmcThread emits exactly one HALT_EVENT");
        assertTrue(halts.get(0).contains("task=3"), "from the dying t2 (task 3): " + halts.get(0));
        List<String> jtNoHalt = new ArrayList<>(jt.log());
        jtNoHalt.removeIf(l -> l.contains("HALT_EVENT"));
        // round 04 fix of my own first version: the MpThread log ends with its RESET marker, which is
        // a boundary I record, not an event; the prefix is compared without it
        List<String> mpEvents = withoutCreated(mp.log());
        assertEquals("it=0 RESET", mpEvents.remove(mpEvents.size() - 1), "MpThread log ends with RESET");
        int k = mpEvents.size();
        assertTrue(jtNoHalt.size() >= k, "JmcThread log at least as long");
        assertEquals(String.join("\n", jtNoHalt.subList(0, k)), String.join("\n", mpEvents),
                "MpThread log == prefix of JmcThread log without HALT");
        List<String> tail = jtNoHalt.subList(k, jtNoHalt.size());
        System.out.println("JmcThread-only tail after the common prefix: " + tail);
        // J2b round 02 (owner's ruling Q1) replaces round 04's "main stops after JOIN_COMPLETE of t2":
        // t2's failure is latched at its FINISH_EVENT and handed to main on main's next event, so the
        // MpThread log ends at main's first event after "FINISH_EVENT task=3" (that event is logged,
        // then refused), whatever the schedule; the run fails with the process failure.
        assertTrue(String.valueOf(mp.thrown().getMessage())
                .contains("process 3 failed: java.lang.AssertionError: t2 fails"),
                "the process failure is the error: " + mp.thrown());
        int finish = mpEvents.indexOf("it=0 FINISH_EVENT task=3");
        assertTrue(finish >= 0, "t2's FINISH_EVENT is in the MpThread log");
        List<String> after = mpEvents.subList(finish + 1, k);
        List<String> mainAfter = after.stream().filter(l -> l.matches(".* task=1( .*)?")).toList();
        assertEquals(1, mainAfter.size(), "exactly one event of main after t2's FINISH: " + after);
        assertEquals(mpEvents.get(k - 1), mainAfter.get(0), "and it is the last event of the MpThread log");
    }
}
