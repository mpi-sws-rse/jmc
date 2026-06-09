package org.mpi_sws.jmc.strategies.pct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link PCTSchedulingStrategy} that exercise the PCT logic in isolation.
 *
 * <p>The tests drive the strategy directly: a {@link TestablePCT} subclass overrides {@link
 * PCTSchedulingStrategy#getActiveTasks()} to inject a controlled active set, so {@link
 * PCTSchedulingStrategy#nextTask()} can be invoked without the runtime/tracker machinery. The
 * package-private accessors {@code getMaxStep()}, {@code getCurrentStep()} and {@code
 * getChangePoints()} (visible because this test lives in the same package) are used for white-box
 * assertions.
 */
public class PCTSchedulingStrategyTest {

    private static final String REPORT_PATH = "build/test-results/jmc-report-test";

    /** A test seam over {@link PCTSchedulingStrategy} with an injectable active set. */
    private static final class TestablePCT extends PCTSchedulingStrategy {
        private Set<Long> active = new HashSet<>();

        TestablePCT(long seed, int bugDepth) {
            super(seed, REPORT_PATH, bugDepth);
        }

        @Override
        protected Set<Long> getActiveTasks() {
            return new HashSet<>(active);
        }

        void setActive(Long... ids) {
            this.active = new HashSet<>(Arrays.asList(ids));
        }

        /** Invokes {@link #nextTask()} and returns the chosen task id (or {@code null}). */
        Long pick() {
            SchedulingChoice<?> choice = nextTask();
            return choice == null ? null : choice.getTaskId();
        }
    }

    private static JmcModelCheckerReport report() {
        return new JmcModelCheckerReport(REPORT_PATH);
    }

    /** Runs {@code numPicks} decisions over the given (constant) active set and returns the winners. */
    private static List<Long> runIteration(
            TestablePCT strategy, int iteration, int numPicks, Long... activeIds) {
        strategy.setActive(activeIds);
        strategy.initIteration(iteration, report());
        List<Long> winners = new ArrayList<>();
        for (int i = 0; i < numPicks; i++) {
            winners.add(strategy.pick());
        }
        return winners;
    }

    @Test
    public void constructorRejectsBugDepthBelowOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PCTSchedulingStrategy(0L, REPORT_PATH, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PCTSchedulingStrategy(0L, REPORT_PATH, -3));
    }

    @Test
    public void emptyActiveSetReturnsNull() {
        TestablePCT strategy = new TestablePCT(1L, 1);
        strategy.setActive();
        strategy.initIteration(0, report());
        assertEquals(null, strategy.pick());
    }

    @Test
    public void singleActiveTaskIsReturnedWithoutCountingAStep() {
        TestablePCT strategy = new TestablePCT(1L, 2);
        strategy.setActive(7L);
        strategy.initIteration(0, report());

        assertEquals(7L, strategy.pick());
        // A single-enabled step is the sequential-execution optimization: no decision is counted.
        assertEquals(0, strategy.getCurrentStep());

        // Once two tasks are enabled, the next call counts a decision.
        strategy.setActive(7L, 8L);
        strategy.pick();
        assertEquals(1, strategy.getCurrentStep());
    }

    @Test
    public void selectionIsStableWithoutChangePoints() {
        // bugDepth = 1 => no change points ever => strict priority => the same task always wins.
        TestablePCT strategy = new TestablePCT(42L, 1);
        List<Long> winners = runIteration(strategy, 0, 8, 1L, 2L, 3L);
        Long first = winners.get(0);
        for (Long w : winners) {
            assertEquals(first, w);
        }
        // depth-1 PCT installs no change points regardless of the learned bound.
        assertTrue(strategy.getChangePoints().isEmpty());
    }

    @Test
    public void initialPrioritiesAreRandomizedAcrossSeeds() {
        // With random initial priorities, the winner among {1,2} should vary across seeds.
        Set<Long> winners = new HashSet<>();
        for (long seed = 0; seed < 100; seed++) {
            TestablePCT strategy = new TestablePCT(seed, 1);
            strategy.setActive(1L, 2L);
            strategy.initIteration(0, report());
            winners.add(strategy.pick());
        }
        assertTrue(winners.contains(1L), "task 1 never won across 100 seeds");
        assertTrue(winners.contains(2L), "task 2 never won across 100 seeds");
    }

    @Test
    public void sameSeedProducesIdenticalSchedules() {
        // Determinism / replay-by-seed: identical seed + identical active sets => identical winners.
        TestablePCT a = new TestablePCT(12345L, 2);
        TestablePCT b = new TestablePCT(12345L, 2);

        List<Long> winnersA0 = runIteration(a, 0, 6, 1L, 2L, 3L);
        List<Long> winnersB0 = runIteration(b, 0, 6, 1L, 2L, 3L);
        assertEquals(winnersA0, winnersB0);

        List<Long> winnersA1 = runIteration(a, 1, 6, 1L, 2L, 3L);
        List<Long> winnersB1 = runIteration(b, 1, 6, 1L, 2L, 3L);
        assertEquals(winnersA1, winnersB1);
    }

    @Test
    public void learnsStepBoundAcrossIterationsAndNeverDecreases() {
        TestablePCT strategy = new TestablePCT(7L, 1);

        // First iteration: nothing learned yet, no change points.
        strategy.setActive(1L, 2L);
        strategy.initIteration(0, report());
        assertEquals(0, strategy.getMaxStep());
        for (int i = 0; i < 3; i++) {
            strategy.pick();
        }
        assertEquals(3, strategy.getCurrentStep());
        assertEquals(3, strategy.getMaxStep());

        // Second iteration: currentStep resets, maxStep is preserved, then grows with a longer run.
        strategy.initIteration(1, report());
        assertEquals(0, strategy.getCurrentStep());
        assertEquals(3, strategy.getMaxStep());
        for (int i = 0; i < 5; i++) {
            strategy.pick();
        }
        assertEquals(5, strategy.getMaxStep());

        // Third iteration: a shorter run must not lower the learned bound.
        strategy.initIteration(2, report());
        for (int i = 0; i < 2; i++) {
            strategy.pick();
        }
        assertEquals(5, strategy.getMaxStep());
    }

    @Test
    public void changePointsAreSampledWithinTheLearnedBound() {
        // bugDepth = 2 => numSwitchPoints = 1.
        TestablePCT strategy = new TestablePCT(99L, 2);

        // Iteration 0 learns maxStep; no change points yet.
        runIteration(strategy, 0, 6, 1L, 2L);
        assertTrue(strategy.getChangePoints().isEmpty());
        assertEquals(6, strategy.getMaxStep());

        // Iteration 1: exactly one change point, drawn from [1, maxStep].
        strategy.setActive(1L, 2L);
        strategy.initIteration(1, report());
        Set<Integer> points = strategy.getChangePoints();
        assertEquals(1, points.size());
        int point = points.iterator().next();
        assertTrue(point >= 1 && point <= 6, "change point " + point + " out of [1,6]");
    }

    @Test
    public void demotionHappensExactlyAtTheChangePoint() {
        // bugDepth = 2 => one change point. The winner must flip exactly at that step (or, if the
        // change point is step 1, there is no earlier winner to flip from).
        TestablePCT strategy = new TestablePCT(2024L, 2);

        // Learn maxStep = 6 with a change-point-free first iteration (constant winner).
        List<Long> firstWinners = runIteration(strategy, 0, 6, 1L, 2L);
        Long w0 = firstWinners.get(0);
        for (Long w : firstWinners) {
            assertEquals(w0, w, "iteration 0 must have a constant winner (no change points)");
        }

        // Second iteration with a single change point.
        List<Long> winners = runIteration(strategy, 1, 6, 1L, 2L);
        Set<Integer> points = strategy.getChangePoints();
        assertEquals(1, points.size());
        int changePoint = points.iterator().next();

        // Collect the 1-based steps at which the winner changed from the previous step.
        List<Integer> flips = new ArrayList<>();
        for (int i = 1; i < winners.size(); i++) {
            if (!winners.get(i).equals(winners.get(i - 1))) {
                flips.add(i + 1); // step index is 1-based
            }
        }

        assertTrue(flips.size() <= 1, "a single change point can cause at most one winner flip");
        if (flips.size() == 1) {
            assertEquals(changePoint, flips.get(0).intValue(),
                    "the winner flip must occur exactly at the change point");
        } else {
            // No observable flip => the change point demoted the leader on the very first step.
            assertEquals(1, changePoint, "no flip observed, so the change point must be step 1");
        }
    }

    @Test
    public void depthOneNeverInstallsChangePoints() {
        TestablePCT strategy = new TestablePCT(5L, 1);
        // Learn a non-zero bound, then confirm subsequent iterations still have no change points.
        runIteration(strategy, 0, 5, 1L, 2L);
        assertFalse(strategy.getMaxStep() == 0);
        strategy.setActive(1L, 2L);
        strategy.initIteration(1, report());
        assertTrue(strategy.getChangePoints().isEmpty());
    }
}
