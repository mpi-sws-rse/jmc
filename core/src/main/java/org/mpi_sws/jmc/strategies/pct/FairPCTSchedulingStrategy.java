package org.mpi_sws.jmc.strategies.pct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;

import java.util.Set;

/**
 * A <em>fair</em> variant of {@link PCTSchedulingStrategy}: it schedules with PCT priorities for a
 * bounded prefix and then falls back to uniform-random scheduling for the remainder of the
 * iteration (a "fair execution suffix").
 *
 * <p><b>Why a fair suffix is needed.</b> Pure PCT is a strict priority scheduler, so a high-priority
 * task that busy-waits on a value produced by a lower-priority task can starve it and the run never
 * terminates. In JMC this is a real possibility: a task spinning on a plain field keeps emitting
 * {@code READ_EVENT}s and therefore stays in the active set (only lock/join/wait-notify/static-init
 * blocking removes a task). After a bounded number of priority-controlled decisions this strategy
 * switches to uniform-random selection, which is probabilistically fair and guarantees progress.
 * This mirrors Coyote's {@code FairPrioritization} and Lockstep's {@code FairPCT}. (It is a
 * tool-inspired liveness extension, not part of the original PCT paper, whose own remedy is to lower
 * the priority of non-progressing threads with small probability.)
 *
 * <p><b>The fair bound.</b> The switch point is configured by {@code fairBound}:
 *
 * <ul>
 *   <li>{@code fairBound > 0} — an <b>explicit</b> bound: switch to the fair suffix once {@code
 *       fairBound} priority-controlled decisions have been made (Coyote/Lockstep style).
 *   <li>{@code fairBound <= 0} — <b>automatic</b>: the bound for each iteration is a snapshot of the
 *       learned step bound {@code k} ({@link #getMaxStep()}) taken at the start of that iteration,
 *       i.e. the largest number of decisions seen in any <em>previous</em> run. So normal-length
 *       runs stay entirely under PCT, and only an abnormally long run — the signature of a
 *       spin-loop livelock — crosses the bound and switches to the fair suffix. The first iteration
 *       has no learned bound yet ({@code k == 0}), so it runs as pure PCT (which is also how {@code
 *       k} is discovered).
 * </ul>
 *
 * <p>The decision is driven by {@link #getCurrentStep()} (the number of genuine scheduling
 * <em>decisions</em> made so far this iteration, counted by the PCT superclass). The effective bound
 * for the current iteration is computed once in {@link #initIteration(int, JmcModelCheckerReport)}
 * and stored in {@link #effectiveBound}. Because the fair suffix does not advance the decision
 * counter, the strategy stays in the suffix for the rest of the iteration once it switches.
 *
 * <p><b>Faithfulness.</b> The pure PCT per-run probabilistic guarantee applies to the
 * priority-controlled prefix; randomizing the suffix only helps liveness and never prevents a
 * prefix-reachable bug from being found. Pure PCT (without a suffix) remains available as {@link
 * PCTSchedulingStrategy}. All randomness comes from the inherited seeded generator, so runs remain
 * reproducible from their seed.
 */
public class FairPCTSchedulingStrategy extends PCTSchedulingStrategy {

    /** Logger used to trace the switch from the PCT prefix to the fair random suffix. */
    private static final Logger LOGGER = LogManager.getLogger(FairPCTSchedulingStrategy.class);

    /**
     * The configured fair bound. A value {@code > 0} is an explicit number of priority-controlled
     * decisions before switching to the fair suffix; a value {@code <= 0} selects automatic mode
     * (the bound is derived from the learned step bound each iteration — see {@link #effectiveBound}).
     */
    private final int fairBound;

    /**
     * The fair bound actually used for the current iteration, recomputed in {@link
     * #initIteration(int, JmcModelCheckerReport)}. Equals {@link #fairBound} in explicit mode, or a
     * snapshot of {@link #getMaxStep()} (the learned bound from previous iterations) in automatic
     * mode. A value {@code <= 0} disables the switch for this iteration (pure PCT), which is what
     * happens on the first iteration in automatic mode before {@code k} is known.
     */
    private int effectiveBound;

    /**
     * Constructs a new fair PCT scheduling strategy.
     *
     * @param seed the seed for the random number generator
     * @param reportPath the directory where the replay seed and trace are written
     * @param bugDepth the target bug depth {@code d} (must be &ge; 1); forwarded to {@link
     *     PCTSchedulingStrategy}
     * @param fairBound the fair-suffix bound: {@code > 0} for an explicit number of
     *     priority-controlled decisions before switching to the fair random suffix, or {@code <= 0}
     *     for automatic mode (derived from the learned step bound)
     * @throws IllegalArgumentException if {@code bugDepth < 1} (from the superclass)
     */
    public FairPCTSchedulingStrategy(Long seed, String reportPath, int bugDepth, int fairBound) {
        super(seed, reportPath, bugDepth);
        this.fairBound = fairBound;
        this.effectiveBound = 0;
    }

    /**
     * Initializes the strategy for a new iteration and computes this iteration's effective fair
     * bound.
     *
     * <p>Delegates to {@link PCTSchedulingStrategy#initIteration(int, JmcModelCheckerReport)}, then
     * sets {@link #effectiveBound}: the configured {@link #fairBound} when it is positive (explicit
     * mode), otherwise a snapshot of {@link #getMaxStep()} (automatic mode). Since the superclass
     * does not change the learned bound during initialization, this snapshot reflects the largest
     * number of decisions observed in previous iterations.
     *
     * @param iteration the number of the iteration
     * @param report the model checker report; the replay seed is recorded into it
     * @throws HaltExecutionException if initialization fails and the execution must halt
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report)
            throws HaltExecutionException {
        super.initIteration(iteration, report);
        effectiveBound = fairBound > 0 ? fairBound : getMaxStep();
        LOGGER.debug("Fair PCT iteration {}: effectiveBound={}", iteration, effectiveBound);
    }

    /**
     * Returns the next task to schedule.
     *
     * <p>While this iteration's decision count ({@link #getCurrentStep()}) is below the
     * {@link #effectiveBound} (or the bound is non-positive, i.e. disabled for this iteration), it
     * delegates to {@link PCTSchedulingStrategy#nextTask()} (PCT priority scheduling). Once the bound
     * is reached it selects uniformly at random among the active tasks (the fair suffix) and — because
     * the suffix does not advance the decision counter — remains in the suffix for the rest of the
     * iteration.
     *
     * @return the scheduling choice for the next task, or {@code null} if no task is active
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        if (effectiveBound > 0 && getCurrentStep() >= effectiveBound) {
            return fairRandomChoice();
        }
        return super.nextTask();
    }

    /**
     * Selects the next task uniformly at random among the active tasks, mirroring {@link
     * RandomSchedulingStrategy#nextTask()}.
     *
     * <p>Uses the inherited seeded random generator so the suffix remains reproducible from the seed.
     * A single active task is scheduled directly; otherwise one is chosen uniformly at random. The
     * choice is built via {@link #makeSchedulingChoice(Long)} so trace recording and reactive-value
     * delivery behave exactly as for the random strategy.
     *
     * @return the scheduling choice for the randomly selected task, or {@code null} if none is active
     */
    private SchedulingChoice<?> fairRandomChoice() {
        Set<Long> active = getActiveTasks();
        if (active.isEmpty()) {
            return null;
        }
        Long chosen;
        if (active.size() == 1) {
            chosen = active.iterator().next();
        } else {
            int index = random.nextInt(active.size());
            chosen = (Long) active.toArray()[index];
        }
        LOGGER.debug("Fair suffix (step {} >= bound {}): randomly scheduling task {}",
                getCurrentStep(), effectiveBound, chosen);
        return makeSchedulingChoice(chosen);
    }
}
