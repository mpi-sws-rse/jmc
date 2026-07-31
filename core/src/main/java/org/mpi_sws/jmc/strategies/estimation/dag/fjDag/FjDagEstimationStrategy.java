package org.mpi_sws.jmc.strategies.estimation.dag.fjDag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;
import org.mpi_sws.jmc.strategies.estimation.EstimationCollector;
import org.mpi_sws.jmc.strategies.estimation.EstimationStrategy;
import org.mpi_sws.jmc.strategies.trust.Event;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGeneratorFactory;

/**
 * Scheduling strategy for the fork-join PeStor estimator ({@code fj-pestor}): the fork-join
 * specialization of Algorithm P.
 *
 * <p>It drives a {@link FjDagEstimator} with a fork-join-aware scheduler ({@link #nextTask}): it
 * first forces the main task to complete all thread creation (the fork phase), then schedules only
 * the worker threads uniformly at random (never the main task). This structure lets the estimator
 * skip charging thread creation/join events, dramatically reducing variance on fork-join programs.
 * Each iteration is one random-walk trial whose point estimate is recorded; results are saved under
 * {@code fj-pestor-*}.
 *
 * <p><b>TODO (unseeded randomness — to revisit together in future):</b> {@link #nextTask} creates a
 * fresh {@code Xoshiro256PlusPlus} generator on every call instead of drawing from the strategy's
 * configured seed, so the scheduling (and hence the walk) is not reproducible from the seed. This
 * should use a single seeded generator so runs are deterministic given a seed.
 */
public class FjDagEstimationStrategy extends RandomSchedulingStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(FjDagEstimationStrategy.class);

    /** The fork-join DAG estimator. */
    private final FjDagEstimator est;

    /** Collects each trial's point estimate and computes the final mean. */
    private final EstimationCollector estimationCollector = new EstimationCollector();

    /**
     * Creates the strategy with a default {@link FjDagEstimator}.
     *
     * @param seed the seed for the random scheduler.
     */
    public FjDagEstimationStrategy(Long seed) {
        // TODO : Fix the hard coded path
        super(seed, "build/test-results/jmc-report");
        est = new FjDagEstimator();
    }


    /**
     * Fork-join-aware scheduling: while forking is incomplete, forces the main task to run (to spawn
     * all workers); afterwards, schedules only the worker threads uniformly at random (never the main
     * task). A lone active task is scheduled directly.
     *
     * @return the next scheduling choice, or {@code null} if no task is active.
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        Set<Long> activeThreads = getActiveTasks();
        Long taskToSchedule;
        if (activeThreads.isEmpty()) {
            return null;
        }
        if (activeThreads.size() == 1) {
            taskToSchedule = (Long) activeThreads.toArray()[0];
        } else {
            if (!est.isForkComplete()) {
                if (!activeThreads.contains(1L)) {
                    LOGGER.error("Main task is not active, something went wrong!");
                    throw HaltCheckerException.error("Main task is not active, something went wrong!");
                }
                // Force scheduling the main task to complete the forking of all tasks
                taskToSchedule = 1L;
            } else {
                // At this point we have multiple active threads, and the fork is complete, we must forbid
                // scheduling the main task if it is still active
                if (activeThreads.contains(1L) && activeThreads.size() > 1) {
                    activeThreads.remove(1L);
                }
                int index = RandomGeneratorFactory.of("Xoshiro256PlusPlus").create().nextInt(activeThreads.size());
                taskToSchedule = (Long) activeThreads.toArray()[index];
            }
        }
        return makeSchedulingChoice(taskToSchedule);
    }

    /**
     * Tracks the event with the random scheduler and forwards it to the fork-join estimator.
     *
     * @param event the runtime event.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        List<Event> events = compileRuntimeEvent(event);
        est.updateEvent(events, getActiveTasks());
    }

    /**
     * Ends the current trial: records its point estimate and resets the estimator.
     *
     * @param iteration the iteration index.
     */
    @Override
    public void resetIteration(int iteration) {
        super.resetIteration(iteration);
        LOGGER.debug("Finished iteration {} with expected value: {}", iteration, est.getExpectedValue());
        recordEstimation();
        est.reset();
    }

    /** Records the current walk's estimate into the collector. */
    @Override
    public void recordEstimation() {
        estimationCollector.record(est.getExpectedValue());
    }

    /**
     * Saves the collected estimates at shutdown.
     *
     * @param report the model-checker report.
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        // TODO : Fix the hard coded path
        saveResults();
    }

    /** Writes the per-trial and final estimates under {@code fj-pestor-*}. */
    protected void saveResults() {
        estimationCollector.save(
                "build/test-results/jmc-report/",
                "fj-pestor-result.txt",
                "fj-pestor-final-result.txt");
    }

    /**
     * Returns the estimation collector (used in tests to inspect the accumulated estimates).
     *
     * @return the collector.
     */
    public EstimationCollector getEstimationCollector() {
        return estimationCollector;
    }


}
