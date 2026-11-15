package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.strategies.must.split.ItemDriver;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;
import org.mpi_sws.jmc.strategies.must.split.TypedGraphEncoding;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * P3 tester round 01 (plan/jmc-must/P3-PROPOSAL.md rev 3 §2 A, §4.4, §4.5): under an installed split control
 * the strategy keeps no {@code GraphCanon} sets and both distinct-graph accessors throw
 * {@link IllegalStateException}, from the installation on, also when the root never reaches K; without a
 * control the counts are as before.
 *
 * <p>Fixture: {@code A3Lock:never-grant,3,1}, counting (J8 step 1 t2: 18 distinct complete, 10 distinct
 * blocked, R 1, N 5 at K 1), run in this JVM. The sets are read by reflection ({@code completeGraphs},
 * {@code blockedGraphs}, as J8Step0Probe reads private state) from inside the control's {@code outcome}
 * callback, which the strategy calls right after the point where 4ce59e76 added the canon: there the sets
 * hold the segment's graphs so far (at least 1), with P3 they stay empty. Reading them after the run would
 * not distinguish the two (every segment end replaces them with empty sets).
 *
 * <p>Derived: with the control, at every outcome callback both sets are empty and both accessors throw; after
 * the run both throw; the outcome digests, by blocked flag, have as many distinct values as the sequential
 * run's distinct graphs (18 and 10), and {@code iterations()} equals the sequential one.
 */
class P3BoundTest {

    static final String NEVER = J8Launch.J4 + "A3Lock:never-grant,3,1";

    /** Runs a strategy over a program in this JVM, as the coordinator's root does. */
    static void check(MustStrategy s, Runnable program, String name) {
        MpThread.setStaleWaitMillis(ItemDriver.SPLIT_STALE_WAIT_MILLIS);
        try {
            new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("must").numIterations(0).seed(1L)
                    .reportPath("build/test-results/jmc-report").strategyConstructor(x -> s)
                    .timeout(Duration.ofSeconds(300)).build())
                    .check(new JmcFunctionalTestTarget(name, () -> MpProcess.runMain(program)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            MpThread.setStaleWaitMillis(MpThread.DEFAULT_STALE_WAIT_MILLIS);
        }
    }

    static int setSize(MustStrategy s, String field) {
        try {
            Field f = MustStrategy.class.getDeclaredField(field);
            f.setAccessible(true);
            return ((Set<?>) f.get(s)).size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A control that explores every item in this JVM (N − 1 … 0) and probes the strategy at each outcome. */
    static final class Probe implements MustStrategy.SplitControl {
        final int k;
        MustStrategy s;
        int outcomes;
        int maxSets;
        int accessorAnswered;
        int n = -1;
        int next;
        final Set<String> complete = new HashSet<>();
        final Set<String> blocked = new HashSet<>();

        Probe(int k) {
            this.k = k;
        }

        @Override
        public int splitThreshold() {
            return k;
        }

        @Override
        public void outcome(boolean b, GraphCanon canon, LongFunction<String> names) {
            outcomes++;
            maxSets = Math.max(maxSets, setSize(s, "completeGraphs") + setSize(s, "blockedGraphs"));
            try {
                s.distinctCompleteGraphs();
                accessorAnswered++;
            } catch (IllegalStateException e) {
                // expected under P3
            }
            try {
                s.distinctBlockedGraphs();
                accessorAnswered++;
            } catch (IllegalStateException e) {
                // expected under P3
            }
            (b ? blocked : complete).add(TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(canon, names)));
        }

        @Override
        public boolean atBoundary(MustStrategy.SplitView v) {
            n = v.stackTopFirst().size();
            next = n - 1;
            return true;
        }

        @Override
        public void segmentDone(MustStrategy.Segment segment) {}

        @Override
        public OptionalInt nextItem() {
            return next >= 0 ? OptionalInt.of(next--) : OptionalInt.empty();
        }
    }

    record Seq(int complete, int blocked, int iterations) {}

    static Seq sequential() throws Exception {
        MustStrategy s = new MustStrategy(1L, "build/test-results/jmc-report");
        check(s, J8Step0Probe.instantiate(NEVER), NEVER);
        return new Seq(s.distinctCompleteGraphs(), s.distinctBlockedGraphs(), s.iterations());
    }

    static void underControl(int k, boolean expectSplit) throws Exception {
        Seq seq = sequential();
        assertEquals(new Seq(18, 10, seq.iterations()), seq, "sequential counts (J8 step 1 t2)");
        MustStrategy s = new MustStrategy(1L, "build/test-results/jmc-report");
        Probe p = new Probe(k);
        p.s = s;
        s.setSplitControl(p);
        assertThrows(IllegalStateException.class, s::distinctCompleteGraphs, "from the installation on");
        assertThrows(IllegalStateException.class, s::distinctBlockedGraphs, "from the installation on");
        check(s, J8Step0Probe.instantiate(NEVER), NEVER);
        System.out.println("P3 bound K " + k + ": n " + p.n + ", outcomes " + p.outcomes + ", max set sizes "
                + p.maxSets + ", accessor answers " + p.accessorAnswered + ", digests " + p.complete.size() + "/"
                + p.blocked.size() + ", iterations " + s.iterations() + " (sequential " + seq + ")");
        assertEquals(expectSplit, p.n >= 0, "split reached: " + p.n);
        assertTrue(s.exhausted());
        assertEquals(seq.iterations(), p.outcomes, "one outcome per iteration");
        assertEquals(0, p.maxSets, "no canon kept at any iteration");
        assertEquals(0, p.accessorAnswered, "both accessors throw during the run");
        assertThrows(IllegalStateException.class, s::distinctCompleteGraphs);
        assertThrows(IllegalStateException.class, s::distinctBlockedGraphs);
        assertEquals(0, setSize(s, "completeGraphs") + setSize(s, "blockedGraphs"));
        assertEquals(seq.iterations(), s.iterations());
        assertEquals(seq.complete(), p.complete.size(), "distinct complete by digest");
        assertEquals(seq.blocked(), p.blocked.size(), "distinct blocked by digest");
    }

    /** Change: K 1 (R 1, N 5), every item explored in this JVM. */
    @Test
    void splitControl_K1_canonSetsStayEmpty_accessorsThrow() throws Exception {
        underControl(1, true);
    }

    /** Change: K 1 000 000, the root exhausts the stack without reaching it (no split, no item). */
    @Test
    void splitControl_rootNeverReachesK_canonSetsStayEmpty_accessorsThrow() throws Exception {
        underControl(1_000_000, false);
    }

    /**
     * Guard: the coordinator with a K the root never reaches returns the sequential counts (its root strategy
     * has the control installed; the counts are the digest union of the root's transcript). No worker starts:
     * the worker command is a path that does not exist.
     */
    @Test
    void coordinator_rootNeverReachesK_countsEqualSequential() throws Exception {
        Seq seq = sequential();
        SplitCoordinator.Result r = SplitCoordinator.run(new SplitCoordinator.Config(NEVER,
                J8Step0Probe.instantiate(NEVER), null, null, 1_000_000, 2, 300, P3Commit.freshDir("nosplit"),
                (x, id) -> List.of("/nonexistent/p3-no-worker-starts")));
        System.out.println("P3 no split: " + r.split() + " " + r.distinctComplete() + "/" + r.distinctBlocked() + " ran "
                + r.ran() + " failure " + r.failure());
        assertNull(r.failure());
        assertFalse(r.split());
        assertEquals(18, r.distinctComplete());
        assertEquals(10, r.distinctBlocked());
        assertEquals(seq.iterations(), r.ran());
    }

    /** Guard: without a control the accessors count and the sets hold the canons (18 and 10). */
    @Test
    void noControl_countsAsBefore() throws Exception {
        MustStrategy s = new MustStrategy(1L, "build/test-results/jmc-report");
        check(s, J8Step0Probe.instantiate(NEVER), NEVER);
        assertEquals(18, s.distinctCompleteGraphs());
        assertEquals(10, s.distinctBlockedGraphs());
        assertEquals(18, setSize(s, "completeGraphs"));
        assertEquals(10, setSize(s, "blockedGraphs"));
    }
}
