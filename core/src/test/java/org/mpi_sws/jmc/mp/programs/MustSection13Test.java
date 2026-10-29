package org.mpi_sws.jmc.mp.programs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.programs.mutants.Asyn;
import org.mpi_sws.jmc.mp.programs.mutants.MainIsT1;
import org.mpi_sws.jmc.mp.programs.mutants.NestedSpawn;
import org.mpi_sws.jmc.mp.programs.mutants.RolesMisnamed;
import org.mpi_sws.jmc.mp.programs.mutants.SingleSenderFifo;
import org.mpi_sws.jmc.mp.programs.mutants.T2NoRecv;
import org.mpi_sws.jmc.mp.programs.mutants.T2Sends4;
import org.mpi_sws.jmc.mp.programs.mutants.T3RecvOnce;
import org.mpi_sws.jmc.mp.programs.mutants.T3RecvThrice;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tester tests for J0 round 01 (plan/jmc-must/log/dev/J0/round-01.prompt.md), claims C1 and C2.
 *
 * <p>Expected results are derived from Must Example 3.1 (Must.txt around line 717) before reading
 * the program: under p2p the program {@code T1: send(T3,1); send(T2,2) | T2: recv(); send(T3,3) |
 * T3: recv(); recv()} has exactly two consistent graphs (T3 reads 1 then 3, or 3 then 1); T2's only
 * receive reads T1's 2; no receive is ever left without a message, so no execution blocks.
 *
 * <p>Every {@code *_detects_*} method runs the same check against a mutant and requires it to fail;
 * the mutant's failure message is printed so the report can quote it.
 */
class MustSection13Test {

    private static final int N = 2000;
    private static final long SEED = 20261026L;
    private static final int OBS_N = 400;
    private static final long OBS_SEED = 1L;
    private static final Pattern TALLY = Pattern.compile("t3 read 1 first: (\\d+); 3 first: (\\d+)(?:; other first: (\\d+))?");

    // ---------------------------------------------------------------- C2 under strategyType("mp-random")

    private static JmcModelCheckerReport runMpRandom(Runnable p, int n, long seed) throws Exception {
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random")
                .numIterations(n)
                .seed(seed)
                .reportPath("build/test-results/j0-tester/" + p.getClass().getSimpleName())
                .build();
        return new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
    }

    private static int[] tally(Runnable p) {
        Matcher m = TALLY.matcher(p.toString());
        assertTrue(m.matches(), "program toString not in the expected shape: " + p);
        // round 02: the third counter (F6 fix) is optional so the two-counter mutants still parse; -1 = absent
        return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
            m.group(3) == null ? -1 : Integer.parseInt(m.group(3))};
    }

    /** C2 with the plain mp-random strategy and the program's own tally. */
    static void checkC2(Runnable p) throws Exception {
        JmcModelCheckerReport report = runMpRandom(p, N, SEED);
        assertNull(report.getErrorMessage(), "the run recorded an error");
        assertEquals(N, report.getTotalIterations(), "iterations run");
        assertEquals(0, report.getBlockedIterations(), "blocked iterations (expected none: every receive has a message)");
        int[] t = tally(p);
        System.out.println("C2 " + p.getClass().getSimpleName() + ": total=" + report.getTotalIterations()
                + " blocked=" + report.getBlockedIterations() + " " + p);
        if (t[2] >= 0) {
            assertEquals(0, t[2], "iterations in which T3 read neither 1 nor 3 first: " + p);
        }
        assertEquals(N, t[0] + t[1] + Math.max(t[2], 0), "iterations in which T3 completed both receives");
        assertTrue(t[0] > 0, "T3 read 1 first in some iteration: " + p);
        assertTrue(t[1] > 0, "T3 read 3 first in some iteration: " + p);
    }

    @Test
    void c2_mpRandom_mustSection13() throws Exception {
        checkC2(new MustSection13());
    }

    @Test
    void c2_mpRandom_detects_T3RecvThrice() {
        mutant("C2", () -> {
            try {
                checkC2(new T3RecvThrice());
            } catch (AssertionFailedError e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Test
    void c2_mpRandom_detects_SingleSenderFifo() {
        mutant("C2", () -> {
            try {
                checkC2(new SingleSenderFifo());
            } catch (AssertionFailedError e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Test
    void c2_mpRandom_detects_T2Sends4Tally3() {
        mutant("C2", () -> {
            try {
                checkC2(new org.mpi_sws.jmc.mp.programs.mutants.T2Sends4Tally3());
            } catch (AssertionFailedError e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- C1 (+ C2) per iteration, observed

    private static List<List<String>> observe(Runnable p, int n, long seed) throws Exception {
        ObservingMpRandomStrategy[] s = new ObservingMpRandomStrategy[1];
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyConstructor(c -> s[0] = new ObservingMpRandomStrategy(c.getSeed(), c.getReportPath()))
                .numIterations(n)
                .seed(seed)
                .reportPath("build/test-results/j0-tester/obs-" + p.getClass().getSimpleName())
                .build();
        JmcModelCheckerReport report =
                new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
        assertNull(report.getErrorMessage(), "the run recorded an error");
        assertEquals(n, s[0].iterations.size(), "iterations observed");
        return s[0].iterations;
    }

    private static final String RECV = "RECV(model=P2P,blocking=true)";

    private static String send(long dst, String role, int v) {
        return "SEND(dst=" + dst + "/" + role + ",model=P2P,v=Msg[v=" + v + "])";
    }

    private static String deliver(int v) {
        return "DELIVER(v=Msg[v=" + v + "])";
    }

    private static long dstOf(String sendEntry) {
        Matcher m = Pattern.compile("^SEND\\(dst=(\\d+)/").matcher(sendEntry);
        if (!m.find()) {
            fail("not a send: " + sendEntry);
        }
        return Long.parseLong(m.group(1));
    }

    /**
     * Checks one iteration against the derived shape and returns T3's first value (1 or 3).
     *
     * <p>Role coverage (reviewer m1, J0 round 03): only the <em>destination</em> roles {@code t2}
     * and {@code t3} are observable here (they travel in the send's {@code dst} {@code Tid}); no
     * runtime event carries a role, so the sender-only role {@code t1} is not seen by this check.
     * It is checked statically on the compiled class by {@link MustSection13RolesTest}.
     */
    static int checkShape(List<String> it) {
        String ctx = " in iteration " + it;
        Map<Long, List<String>> byTask = new LinkedHashMap<>();
        int firstMainJoin = -1;
        Map<Long, Integer> startAt = new LinkedHashMap<>();
        for (int i = 0; i < it.size(); i++) {
            String e = it.get(i);
            int colon = e.indexOf(':');
            long task = Long.parseLong(e.substring(0, colon));
            String what = e.substring(colon + 1);
            byTask.computeIfAbsent(task, k -> new ArrayList<>()).add(what);
            if (task == 1L && what.equals("JOIN_REQUEST_EVENT") && firstMainJoin < 0) {
                firstMainJoin = i;
            }
            if (what.startsWith("START")) {
                startAt.put(task, i);
            }
            if (what.equals("BLOCK") || what.equals("BOTTOM")) {
                fail("unexpected " + what + " for task " + task + ctx);
            }
        }
        // Main: invisible; starts, joins three times, finishes. J2b round 03 (rework R1): MpThread's
        // constructor also raises one TASK_CREATED_EVENT on main's thread per process, each before that
        // process's START; they are counted and then set aside.
        List<String> main = new ArrayList<>(byTask.remove(1L));
        assertEquals(3, main.stream().filter(w -> w.equals("TASK_CREATED_EVENT")).count(),
                "one TASK_CREATED_EVENT per process" + ctx);
        main.removeIf(w -> w.equals("TASK_CREATED_EVENT"));
        assertEquals(List.of("START(by=1)", "JOIN_REQUEST_EVENT", "JOIN_COMPLETE_EVENT", "JOIN_REQUEST_EVENT",
                "JOIN_COMPLETE_EVENT", "JOIN_REQUEST_EVENT", "JOIN_COMPLETE_EVENT", "FINISH_EVENT"), main,
                "main's events" + ctx);
        // Exactly three processes, each spawned by main before main's first await.
        assertEquals(3, byTask.size(), "number of spawned processes" + ctx);
        Map<Long, List<String>> words = new LinkedHashMap<>();
        for (Map.Entry<Long, List<String>> en : byTask.entrySet()) {
            List<String> w = en.getValue();
            assertEquals("START(by=1)", w.get(0), "task " + en.getKey() + " first event (spawned by main)" + ctx);
            assertEquals("FINISH_EVENT", w.get(w.size() - 1), "task " + en.getKey() + " last event" + ctx);
            assertTrue(startAt.get(en.getKey()) < firstMainJoin,
                    "task " + en.getKey() + " spawned before main's first await" + ctx);
            words.put(en.getKey(), w.subList(1, w.size() - 1));
        }
        // T1: the process whose first event is the send of 1.
        Long t1 = null;
        for (Map.Entry<Long, List<String>> en : words.entrySet()) {
            if (!en.getValue().isEmpty() && en.getValue().get(0).startsWith("SEND(")
                    && en.getValue().get(0).endsWith("v=Msg[v=1])")) {
                t1 = en.getKey();
            }
        }
        if (t1 == null) {
            fail("no process starts with a send of 1" + ctx);
        }
        List<String> w1 = words.get(t1);
        assertEquals(2, w1.size(), "T1's events" + ctx);
        long x = dstOf(w1.get(0));
        long y = dstOf(w1.get(1));
        assertEquals(List.of(send(x, "t3", 1), send(y, "t2", 2)), w1, "T1's events" + ctx);
        assertTrue(words.containsKey(x) && words.containsKey(y) && x != y && x != t1 && y != t1,
                "T1's destinations are the two other processes" + ctx);
        assertEquals(List.of(RECV, deliver(2), send(x, "t3", 3)), words.get(y), "T2's events" + ctx);
        List<String> w3 = words.get(x);
        List<String> oneThree = List.of(RECV, deliver(1), RECV, deliver(3));
        List<String> threeOne = List.of(RECV, deliver(3), RECV, deliver(1));
        assertTrue(w3.equals(oneThree) || w3.equals(threeOne),
                "T3's events are one of the two p2p graphs: " + w3 + ctx);
        return w3.equals(oneThree) ? 1 : 3;
    }

    /** C1 (shape of every iteration) and C2 (no block; both orders) on the observed iterations. */
    static void checkC1(Runnable p) throws Exception {
        List<List<String>> its = observe(p, OBS_N, OBS_SEED);
        int one = 0;
        int three = 0;
        for (List<String> it : its) {
            if (checkShape(it) == 1) {
                one++;
            } else {
                three++;
            }
        }
        System.out.println("C1 " + p.getClass().getSimpleName() + ": " + its.size() + " iterations, T3 1-first="
                + one + " 3-first=" + three + "; first iteration: " + its.get(0));
        assertTrue(one > 0 && three > 0, "both graphs observed: 1-first=" + one + " 3-first=" + three);
    }

    private static void mutant(String claim, Supplier<Void> check) {
        AssertionFailedError e = assertThrows(AssertionFailedError.class, check::get,
                "the " + claim + " check must fail on the mutant");
        String msg = e.getMessage();
        System.out.println("MUTANT VERDICT (" + claim + "): "
                + (msg.length() > 1500 ? msg.substring(0, 1500) + " ...[truncated]" : msg));
    }

    private static Supplier<Void> c1(Runnable p) {
        return () -> {
            try {
                checkC1(p);
            } catch (AssertionFailedError e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        };
    }

    @Test
    void c1_observed_mustSection13() throws Exception {
        checkC1(new MustSection13());
    }

    @Test
    void c1_detects_T3RecvOnce() {
        mutant("C1", c1(new T3RecvOnce()));
    }

    @Test
    void c1_detects_T3RecvThrice() {
        mutant("C1", c1(new T3RecvThrice()));
    }

    @Test
    void c1_detects_T2NoRecv() {
        mutant("C1", c1(new T2NoRecv()));
    }

    @Test
    void c1_detects_T2Sends4() {
        mutant("C1", c1(new T2Sends4()));
    }

    @Test
    void c1_detects_Asyn() {
        mutant("C1", c1(new Asyn()));
    }

    @Test
    void c1_detects_RolesMisnamed() {
        mutant("C1", c1(new RolesMisnamed()));
    }

    @Test
    void c1_detects_SingleSenderFifo() {
        mutant("C1", c1(new SingleSenderFifo()));
    }

    @Test
    void c1_detects_NestedSpawn() {
        mutant("C1", c1(new NestedSpawn()));
    }

    @Test
    void c1_detects_MainIsT1() {
        mutant("C1", c1(new MainIsT1()));
    }
}
