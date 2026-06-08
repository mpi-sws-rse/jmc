package org.mpi_sws.jmc.strategies.pct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link FairPCTSchedulingStrategy}.
 *
 * <p>The tests use {@code bugDepth == 1}, so the PCT prefix installs no change points and therefore
 * selects a <em>constant</em> winner (strict priority), while the fair suffix selects uniformly at
 * random. The boundary between a constant prefix and a varying suffix is what makes the
 * prefix/suffix switch observable. As in {@link PCTSchedulingStrategyTest}, a {@link
 * TestableFairPCT} subclass injects a controlled active set.
 */
public class FairPCTSchedulingStrategyTest {

    private static final String REPORT_PATH = "build/test-results/jmc-report-test";

    /** A test seam over {@link FairPCTSchedulingStrategy} with an injectable active set. */
    private static final class TestableFairPCT extends FairPCTSchedulingStrategy {
        private Set<Long> active = new HashSet<>();

        TestableFairPCT(long seed, int bugDepth, int fairBound) {
            super(seed, REPORT_PATH, bugDepth, fairBound);
        }

        @Override
        protected Set<Long> getActiveTasks() {
            return new HashSet<>(active);
        }

        void setActive(Long... ids) {
            this.active = new HashSet<>(Arrays.asList(ids));
        }

        Long pick() {
            SchedulingChoice<?> choice = nextTask();
            return choice == null ? null : choice.getTaskId();
        }
    }

    private static JmcModelCheckerReport report() {
        return new JmcModelCheckerReport(REPORT_PATH);
    }

    private static List<Long> runIteration(
            TestableFairPCT strategy, int iteration, int numPicks, Long... activeIds) {
        strategy.setActive(activeIds);
        strategy.initIteration(iteration, report());
        List<Long> winners = new ArrayList<>();
        for (int i = 0; i < numPicks; i++) {
            winners.add(strategy.pick());
        }
        return winners;
    }

    /** Asserts every element in {@code [from, to)} equals the element at {@code from}. */
    private static void assertConstant(List<Long> winners, int from, int to) {
        Long ref = winners.get(from);
        for (int i = from; i < to; i++) {
            assertEquals(ref, winners.get(i), "expected constant winner at index " + i);
        }
    }

    /** Asserts some element in {@code [from, to)} differs from {@code value}. */
    private static void assertHasVariation(List<Long> winners, int from, int to, Long value) {
        for (int i = from; i < to; i++) {
            if (!winners.get(i).equals(value)) {
                return;
            }
        }
        throw new AssertionError("expected the suffix to vary from " + value + " but it never did");
    }

    @Test
    public void constructorAllowsNonPositiveFairBoundButValidatesBugDepth() {
        // fairBound <= 0 is the (valid) automatic mode.
        new FairPCTSchedulingStrategy(0L, REPORT_PATH, 1, 0);
        new FairPCTSchedulingStrategy(0L, REPORT_PATH, 1, -5);
        // bugDepth < 1 is rejected by the superclass.
        assertThrows(
                IllegalArgumentException.class,
                () -> new FairPCTSchedulingStrategy(0L, REPORT_PATH, 0, 5));
    }

    @Test
    public void autoModeFirstIterationIsPurePct() {
        // Auto mode (fairBound = 0): on the first iteration maxStep is 0, so effectiveBound is 0 and
        // the fair switch is disabled — the whole iteration is pure PCT (a constant winner).
        TestableFairPCT strategy = new TestableFairPCT(123L, 1, 0);
        List<Long> winners = runIteration(strategy, 0, 30, 1L, 2L, 3L);
        assertConstant(winners, 0, winners.size());
        assertEquals(30, strategy.getMaxStep());
    }

    @Test
    public void explicitBoundSwitchesToFairSuffix() {
        // Explicit fairBound = 3 applies from the first iteration: picks 1..3 are PCT (constant),
        // picks 4+ are the fair (random) suffix.
        int fairBound = 3;
        TestableFairPCT strategy = new TestableFairPCT(123L, 1, fairBound);
        List<Long> winners = runIteration(strategy, 0, 40, 1L, 2L, 3L);

        assertConstant(winners, 0, fairBound);
        assertHasVariation(winners, fairBound, winners.size(), winners.get(0));
    }

    @Test
    public void autoModeSwitchesAfterLearnedBound() {
        // Auto mode: iteration 0 learns maxStep; iteration 1's effectiveBound is that snapshot, so
        // the first maxStep decisions are PCT and the rest are the fair suffix.
        TestableFairPCT strategy = new TestableFairPCT(2024L, 1, 0);

        runIteration(strategy, 0, 5, 1L, 2L, 3L);
        int learned = strategy.getMaxStep();
        assertEquals(5, learned);

        List<Long> winners = runIteration(strategy, 1, 40, 1L, 2L, 3L);
        assertConstant(winners, 0, learned);
        assertHasVariation(winners, learned, winners.size(), winners.get(0));
    }

    @Test
    public void sameSeedProducesIdenticalSchedules() {
        // Determinism / replay-by-seed, including the random fair suffix.
        TestableFairPCT a = new TestableFairPCT(777L, 1, 3);
        TestableFairPCT b = new TestableFairPCT(777L, 1, 3);

        assertEquals(
                runIteration(a, 0, 30, 1L, 2L, 3L), runIteration(b, 0, 30, 1L, 2L, 3L));
        assertEquals(
                runIteration(a, 1, 30, 1L, 2L, 3L), runIteration(b, 1, 30, 1L, 2L, 3L));
    }

    @Test
    public void autoModeRunNoLongerThanLearnedBoundStaysPurePct() {
        // In auto mode, a later run that is no longer than the learned bound never reaches the fair
        // suffix, so it remains a constant PCT winner.
        TestableFairPCT strategy = new TestableFairPCT(55L, 1, 0);
        runIteration(strategy, 0, 8, 1L, 2L, 3L); // learns maxStep = 8
        assertEquals(8, strategy.getMaxStep());

        List<Long> winners = runIteration(strategy, 1, 8, 1L, 2L, 3L); // exactly the learned bound
        assertConstant(winners, 0, winners.size());
    }
}
