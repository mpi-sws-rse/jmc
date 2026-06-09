package org.mpi_sws.jmc.strategies.pct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;
import org.mpi_sws.jmc.strategies.ReplayableSchedulingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A priority-based randomized scheduling strategy implementing PCT (Probabilistic Concurrency
 * Testing).
 *
 * <p>PCT comes from <em>"A Randomized Scheduler with Probabilistic Guarantees of Finding Bugs"</em>
 * (Burckhardt, Kothari, Musuvathi, Nagarakatte, ASPLOS 2010). For a program with at most {@code n}
 * tasks and {@code k} scheduling steps it finds a bug of <em>depth</em> {@code d} with probability
 * at least {@code 1 / (n * k^(d-1))} per iteration. The strategy realizes the algorithm with three
 * ingredients:
 *
 * <ol>
 *   <li><b>Random initial priorities.</b> Every task receives a random priority the first time it
 *       becomes runnable; at each step the runnable task of <em>maximal</em> priority runs.
 *   <li><b>{@code d - 1} priority change points.</b> Before each iteration, {@code d - 1} step
 *       indices are drawn uniformly from {@code [1, k]}. When the step counter reaches a change
 *       point, the task that would run is <em>demoted</em> below all others, inducing a preemption
 *       at that step.
 *   <li><b>Schedule the maximal-priority runnable task</b> at every step.
 * </ol>
 *
 * <h2>Integration with JMC</h2>
 *
 * <p>This class extends {@link RandomSchedulingStrategy} (and therefore {@link
 * org.mpi_sws.jmc.strategies.tracker.TrackActiveTasksStrategy}) so that the set of runnable
 * ("active") tasks is computed by the trackers exactly as for the random strategy. PCT only changes
 * the <em>selection</em> made in {@link #nextTask()}: instead of picking a uniformly random active
 * task it picks the active task of maximal priority. Everything else — the seeded {@code ExtRandom},
 * reactive-random-value handling in {@code updateEvent}, trace recording via {@link
 * #makeSchedulingChoice(Long)}, and seed-based replay ({@link ReplayableSchedulingStrategy}) — is
 * inherited unchanged. No change to {@code JmcRuntime}, the {@code Scheduler}, {@code TaskManager},
 * or any tracker is required.
 *
 * <h2>Optimizations adopted from other PCT tools</h2>
 *
 * <ul>
 *   <li><b>Learned {@code k}.</b> The step bound {@code k} ({@link #maxStep}) is not supplied by the
 *       user; it is learned as the largest number of scheduling <em>decisions</em> observed across
 *       iterations. Because JMC reuses the same strategy instance across iterations, this is simply
 *       a field that survives {@link #initIteration(int, JmcModelCheckerReport)}.
 *   <li><b>Decision-point counting (sequential-execution optimization).</b> Steps where only one
 *       task is runnable are not counted, so change points fall only on genuine scheduling
 *       decisions.
 *   <li><b>Lazy priority assignment.</b> Priorities are assigned on demand when a task first appears
 *       in the active set, so dynamically created tasks are handled and {@code n} need not be known
 *       in advance.
 *   <li><b>O(1) demotion.</b> A demotion assigns an ever-decreasing priority value, guaranteeing the
 *       demoted task sits below all currently-known tasks without reordering a list.
 * </ul>
 *
 * <p>The first iteration runs with {@link #maxStep} {@code == 0}, so no change points are installed:
 * it is depth-1 PCT (random priorities only) and simultaneously the run during which {@code k} is
 * discovered.
 *
 * <p><b>Determinism / replay.</b> All PCT randomness (initial priorities and change-point indices)
 * is drawn from the inherited seeded {@code ExtRandom}, so a run is fully reproducible from its seed
 * — the same property the random strategy relies on.
 *
 * <p><b>Liveness.</b> This is <em>pure</em> PCT and is therefore intentionally unfair: a
 * high-priority task spinning on a value produced by a low-priority task can livelock. Use {@code
 * FairPCTSchedulingStrategy} when a fair execution suffix is needed.
 */
public class PCTSchedulingStrategy extends RandomSchedulingStrategy {

    /** Logger used to trace priority assignments, change points, and demotions. */
    private static final Logger LOGGER = LogManager.getLogger(PCTSchedulingStrategy.class);

    /**
     * Number of priority change points per iteration, equal to {@code d - 1} for a target bug depth
     * {@code d}. Zero means no change points (pure depth-1 PCT).
     */
    private final int numSwitchPoints;

    /**
     * Priority assigned to each task seen so far this iteration (higher value = higher priority).
     * Initial priorities are random values in {@code [0, 1)}; demotions are negative values handed
     * out by {@link #nextDemotedPriority}, so a demoted task always ranks below every task with an
     * initial priority. Tasks are added lazily by {@link #assignPriorities(Set)}.
     */
    private final Map<Long, Double> priorities;

    /**
     * The set of decision-step indices (in {@code [1, maxStep]}) at which a demotion occurs. Sampled
     * once per iteration by {@link #prepareChangePoints()}.
     */
    private final Set<Integer> changePoints;

    /** Number of scheduling <em>decisions</em> made in the current iteration (steps with &gt; 1 active task). */
    private int currentStep;

    /**
     * The largest {@link #currentStep} observed across all iterations so far — the learned step
     * bound {@code k}. Persists across {@link #initIteration(int, JmcModelCheckerReport)} so that
     * change points can be sampled from a realistic range.
     */
    private int maxStep;

    /**
     * Strictly decreasing counter that supplies the next demotion priority. Starts at {@code 0.0};
     * each demotion pre-decrements it (so values are {@code -1.0, -2.0, ...}), guaranteeing every
     * demoted task ranks below all tasks holding an initial priority in {@code [0, 1)}.
     */
    private double nextDemotedPriority;

    /**
     * Constructs a new PCT scheduling strategy.
     *
     * @param seed the seed for the random number generator
     * @param reportPath the directory where the replay seed and trace are written
     * @param bugDepth the target bug depth {@code d} (must be &ge; 1); the number of priority change
     *     points is {@code d - 1}
     */
    public PCTSchedulingStrategy(Long seed, String reportPath, int bugDepth) {
        super(seed, reportPath);
        if (bugDepth < 1) {
            throw new IllegalArgumentException("bugDepth must be >= 1, but was " + bugDepth);
        }
        this.numSwitchPoints = bugDepth - 1;
        this.priorities = new HashMap<>();
        this.changePoints = new HashSet<>();
        this.currentStep = 0;
        this.maxStep = 0;
        this.nextDemotedPriority = 0.0;
    }

    /**
     * Initializes the strategy for a new iteration.
     *
     * <p>Delegates to {@link RandomSchedulingStrategy#initIteration(int, JmcModelCheckerReport)}
     * (which records the seed and resets the random-value map and trace), then resets the
     * per-iteration PCT state and samples this iteration's change points from the learned step bound
     * {@link #maxStep}. {@link #maxStep} itself is preserved across iterations.
     *
     * @param iteration the number of the iteration
     * @param report the model checker report; the replay seed is recorded into it
     * @throws HaltExecutionException if initialization fails and the execution must halt
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report)
            throws HaltExecutionException {
        super.initIteration(iteration, report);
        priorities.clear();
        changePoints.clear();
        currentStep = 0;
        nextDemotedPriority = 0.0;
        prepareChangePoints();
        LOGGER.debug(
                "PCT iteration {}: maxStep={}, changePoints={}", iteration, maxStep, changePoints);
    }

    /**
     * Returns the next task to schedule: the active task of maximal priority.
     *
     * <p>The procedure is:
     *
     * <ol>
     *   <li>If no task is active, return {@code null} (the scheduler will retry or detect a
     *       deadlock).
     *   <li>If exactly one task is active, schedule it without counting a step (sequential-execution
     *       optimization).
     *   <li>Assign random initial priorities to any newly-seen active tasks, then count this
     *       decision and update the learned bound {@link #maxStep}.
     *   <li>If the current step is a change point, demote the maximal-priority active task and
     *       re-select, inducing a preemption at this step.
     *   <li>Return the maximal-priority active task via {@link #makeSchedulingChoice(Long)} (which
     *       records the choice and attaches any pending reactive value).
     * </ol>
     *
     * @return the scheduling choice for the maximal-priority active task, or {@code null} if none is
     *     active
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        Set<Long> active = getActiveTasks();
        if (active.isEmpty()) {
            return null;
        }
        if (active.size() == 1) {
            return makeSchedulingChoice(active.iterator().next());
        }

        assignPriorities(active);

        // Count this scheduling decision and learn the step bound k (see recordDecision).
        recordDecision();

        Long next = highestPriority(active);
        if (changePoints.contains(currentStep)) {
            LOGGER.debug("Change point at step {}: demoting task {}", currentStep, next);
            demote(next);
            next = highestPriority(active);
        }
        return makeSchedulingChoice(next);
    }

    /**
     * Assigns a random initial priority in {@code [0, 1)} to every active task not yet seen this
     * iteration.
     *
     * <p>New tasks are processed in ascending id order so the sequence of random draws is
     * deterministic for a given seed regardless of the (unordered) iteration order of the active
     * set, preserving reproducibility.
     *
     * @param active the current set of active (runnable) tasks
     */
    private void assignPriorities(Set<Long> active) {
        List<Long> newTasks = new ArrayList<>();
        for (Long task : active) {
            if (!priorities.containsKey(task)) {
                newTasks.add(task);
            }
        }
        if (newTasks.isEmpty()) {
            return;
        }
        Collections.sort(newTasks);
        for (Long task : newTasks) {
            double priority = random.nextDouble();
            priorities.put(task, priority);
            LOGGER.debug("Assigned initial priority {} to task {}", priority, task);
        }
    }

    /**
     * Returns the active task with the maximal priority.
     *
     * <p>Every active task is guaranteed to have a priority (assigned by {@link
     * #assignPriorities(Set)} before this is called). Ties — which are effectively impossible with
     * random {@code double} priorities — are broken deterministically in favor of the smaller task
     * id so selection is reproducible.
     *
     * @param active the current set of active (runnable) tasks
     * @return the active task of maximal priority
     */
    private Long highestPriority(Set<Long> active) {
        Long best = null;
        double bestPriority = 0.0;
        for (Long task : active) {
            double priority = priorities.get(task);
            if (best == null
                    || priority > bestPriority
                    || (priority == bestPriority && task < best)) {
                best = task;
                bestPriority = priority;
            }
        }
        return best;
    }

    /**
     * Demotes the given task below all tasks holding an initial priority by assigning it the next
     * (strictly decreasing) demotion priority.
     *
     * @param task the task to demote
     */
    private void demote(Long task) {
        nextDemotedPriority -= 1.0;
        priorities.put(task, nextDemotedPriority);
    }

    /**
     * Records that a genuine scheduling decision (more than one runnable task) was made and learns
     * the step bound {@code k}.
     *
     * <p>Increments the current-iteration decision counter and raises {@link #maxStep} if this
     * iteration has now produced more decisions than any previous one. Because {@link #maxStep} is
     * preserved across {@link #initIteration(int, JmcModelCheckerReport)}, this is how the bound
     * {@code k} used to sample change points is <em>learned</em> over iterations (no user-supplied
     * {@code k} is required). Steps with a single runnable task never reach this method, so {@code k}
     * counts only real decision points (the sequential-execution optimization).
     */
    private void recordDecision() {
        currentStep += 1;
        if (currentStep > maxStep) {
            maxStep = currentStep;
        }
    }

    /**
     * Samples this iteration's priority change points.
     *
     * <p>Draws {@code min(numSwitchPoints, maxStep)} distinct step indices uniformly from {@code [1,
     * maxStep]} using the inherited seeded random generator. Does nothing when there are no change
     * points to place (depth-1 PCT) or when {@code maxStep} is not yet known (the first iteration),
     * leaving {@link #changePoints} empty.
     */
    private void prepareChangePoints() {
        if (numSwitchPoints <= 0 || maxStep <= 0) {
            return;
        }
        int count = Math.min(numSwitchPoints, maxStep);
        List<Integer> candidates = new ArrayList<>(maxStep);
        for (int step = 1; step <= maxStep; step++) {
            candidates.add(step);
        }
        for (int i = 0; i < count; i++) {
            int index = random.nextInt(candidates.size());
            changePoints.add(candidates.remove(index));
        }
    }

    /**
     * Returns the learned step bound {@code k}.
     *
     * <p>Package-private; exposed for unit testing.
     *
     * @return the largest number of scheduling decisions observed across iterations
     */
    int getMaxStep() {
        return maxStep;
    }

    /**
     * Returns the number of scheduling decisions made in the current iteration.
     *
     * <p>Package-private; exposed for unit testing.
     *
     * @return the current decision-step count
     */
    int getCurrentStep() {
        return currentStep;
    }

    /**
     * Returns a copy of the change points sampled for the current iteration.
     *
     * <p>Package-private; exposed for unit testing.
     *
     * @return the set of change-point step indices
     */
    Set<Integer> getChangePoints() {
        return new HashSet<>(changePoints);
    }
}
