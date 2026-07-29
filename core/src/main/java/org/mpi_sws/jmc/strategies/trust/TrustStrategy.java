package org.mpi_sws.jmc.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.ReplayableSchedulingStrategy;
import org.mpi_sws.jmc.strategies.tracker.TrackActiveTasksStrategy;
import org.mpi_sws.jmc.strategies.tracker.TrackTasks;
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A wrapper around the {@link Algo} algorithm that implements a scheduling strategy based on trust.
 * The class implements the {@link ReplayableSchedulingStrategy} and {@link
 * org.mpi_sws.jmc.strategies.SchedulingStrategy} and uses the {@link TrackActiveTasksStrategy} to
 * track active tasks during the execution.
 */
public class TrustStrategy extends TrackActiveTasksStrategy
        implements ReplayableSchedulingStrategy {

    private final Logger LOGGER = LogManager.getLogger(TrustStrategy.class);

    /** The TruSt algorithm driver this strategy adapts to the scheduler contract. */
    protected final Algo algoInstance;
    /** The fallback policy used to pick among schedulable active tasks when not guiding. */
    private final SchedulingPolicy policy;
    /** RNG used by the {@code RANDOM} fallback policy. */
    private final Random random;

    /** When true, dumps execution graphs and consistency checks each iteration for debugging. */
    private final boolean debug;
    /** Directory where replay traces and debug artifacts are written. */
    private final String reportPath;
    /** The schedule currently being replayed, or {@code null} when exploring normally. */
    private List<SchedulingChoice<?>> recordedTrace;

    /** Creates a strategy with a time-based seed, FIFO policy, no debug, and the default report path. */
    public TrustStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    /**
     * Creates a strategy with the given seed, policy, debug flag, and report path (no solver).
     *
     * @param randomSeed seed for the {@code RANDOM} fallback policy.
     * @param policy the fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for replay traces and debug output.
     */
    public TrustStrategy(
            Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(List.of(new TrackTasks()));
        this.random = new Random(randomSeed);
        this.algoInstance = new Algo(false, "off");
        this.policy = policy;
        this.debug = debug;
        this.reportPath = reportPath;
        this.recordedTrace = null;
    }

    /**
     * As {@link #TrustStrategy(Long, SchedulingPolicy, boolean, String)}, additionally selecting the
     * SMT solver that enables the concolic (ConDpor) extension.
     *
     * @param randomSeed seed for the {@code RANDOM} fallback policy.
     * @param policy the fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for replay traces and debug output.
     * @param solver the solver selection ({@code "off"} disables symbolic exploration).
     */
    public TrustStrategy(
            Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath, String solver) {
        super(List.of(new TrackTasks()));
        this.random = new Random(randomSeed);
        this.algoInstance = new Algo(false, solver);
        this.policy = policy;
        this.debug = debug;
        this.reportPath = reportPath;
        this.recordedTrace = null;
    }

    /**
     * As {@link #TrustStrategy(Long, SchedulingPolicy, boolean, String, String)}, additionally
     * enabling the exploration-tree logger.
     *
     * @param randomSeed seed for the {@code RANDOM} fallback policy.
     * @param policy the fallback scheduling policy.
     * @param debug whether to emit debug artifacts.
     * @param reportPath directory for replay traces and debug output.
     * @param hasTreeLogger whether to record the exploration tree.
     * @param solver the solver selection ({@code "off"} disables symbolic exploration).
     */
    public TrustStrategy(
            Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath, boolean hasTreeLogger, String solver) {
        super(List.of(new TrackTasks()));
        this.random = new Random(randomSeed);
        this.algoInstance = new Algo(hasTreeLogger, solver);
        this.policy = policy;
        this.debug = debug;
        this.reportPath = reportPath;
        this.recordedTrace = null;
    }

    /**
     * Prepares for an iteration: resets the active-task tracking and asks the algorithm to build the
     * guiding schedule for the next pending revisit (a no-op on iteration 0).
     *
     * @param iteration the iteration index.
     * @param report the model-checker report.
     */
    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) {
        super.initIteration(iteration, report);
        algoInstance.initIteration(iteration, report);
        if (debug) {
            algoInstance.writeExecutionGraphToFile(
                    Paths.get(this.reportPath, "iteration-guiding-" + iteration + ".json")
                            .toString());
        }
    }

    /**
     * Returns the next scheduling choice, trying three sources in order: the recorded trace being
     * replayed, the algorithm's guiding schedule, and finally a schedulable active task chosen by
     * the fallback {@link #policy}. Task ids are converted from Trust's 0-indexed scheme to the
     * runtime's 1-indexed scheme here.
     *
     * @return the next scheduling choice, or {@code null} if no task can be scheduled.
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        if (recordedTrace != null) {
            // If we have a recorded trace, return the next task from it
            SchedulingChoice<?> next = recordedTrace.remove(0);
            // Update it's value based on value tracker in the algo
            if (next != null) {
                algoInstance.updateExternalValue(next);
            }
            LOGGER.debug("Returning recorded task: {}", next);
            if (next.isEnd()) {
                // If we are at the end event only the main thread (1) needs to be active and
                // continue.
                // For sanity, we check that the set of active tasks contains only the main thread.
                Set<Long> activeTasks = getActiveTasks();
                if (activeTasks.size() != 1 || !activeTasks.contains(1L)) {
                    LOGGER.error(
                            "End of trace reached but active tasks are not as expected: {}",
                            activeTasks);
                    throw new RuntimeException(
                            "End of trace reached but active tasks are not as expected: "
                                    + activeTasks);
                }
                return SchedulingChoice.task(1L); // Return task ID 1 for end of trace
            }
            return next;
        }

        // Always add 1 to the return value the strategy expects 1-indexed tasks, but we store
        // 0-indexed tasks

        // Otherwise, return an active, schedule-able task based on the policy
        Set<Long> activeTasks = getActiveTasks();
        // If the algorithm has a task to execute, return it
        SchedulingChoice<?> nextTask = algoInstance.nextTask();
        if (nextTask != null) {
            if (!activeTasks.contains(nextTask.getTaskId())) {
                LOGGER.debug("Guiding trace led us to a task={} that is not" +
                        " in active={}", nextTask, activeTasks);
            }
            // Update it's value based on value tracker in the algo
            algoInstance.updateExternalValue(nextTask);
            return nextTask;
        }

        List<Long> activeScheduleAbleTasks =
                algoInstance.getSchedulableTasks().stream()
                        // Adding 1 here for all further uses of the task ID
                        .map((t) -> t + 1)
                        .filter(activeTasks::contains)
                        .toList();

        // If the policy is FIFO, return the first active, schedule-able task
        SchedulingChoice<?> next = SchedulingChoice.task(
                switch (policy) {
                    case FIFO -> activeScheduleAbleTasks.isEmpty()
                            ? null
                            : activeScheduleAbleTasks.get(0);
                    case LIFO -> activeScheduleAbleTasks.isEmpty()
                            ? null
                            : activeScheduleAbleTasks.get(activeScheduleAbleTasks.size() - 1);
                    case RANDOM -> {
                        int size = activeScheduleAbleTasks.size();
                        yield size == 0 ? null : activeScheduleAbleTasks.get(random.nextInt(size));
                    }
                });

        // Update it's value based on value tracker in the algo
        if (next != null) {
            algoInstance.updateExternalValue(next);
        }
        return next;
    }

    /**
     * Updates the active-task trackers and feeds the event to the algorithm (translated via {@link
     * EventFactory}). While replaying a recorded trace, the algorithm is not updated.
     *
     * @param event the runtime event.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event)
            throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        if (recordedTrace != null && !recordedTrace.isEmpty()) {
            // If we are replaying a recorded trace, we do not update the algorithm with new events
            LOGGER.debug("Skipping event update during trace replay: {}", event);
            return;
        }
        List<Event> trustEvents = EventFactory.fromRuntimeEvent(event);
        for (Event e : trustEvents) {
            LOGGER.debug("Received event: {}", e);
            try {
                algoInstance.updateEvent(e);
            } catch (Exception ex) {
                LOGGER.error("Failed to update event: {}", e, ex);
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Resets per-iteration state, verifying consistency of the explored graph when debugging.
     *
     * @param iteration the iteration index.
     */
    @Override
    public void resetIteration(int iteration) {
        resetIteration(iteration, true);
    }

    /**
     * Resets per-iteration state.
     *
     * @param iteration the iteration index.
     * @param checkConsistency when true (and debug is on), asserts the explored graph is consistent.
     */
    protected void resetIteration(int iteration, boolean checkConsistency) {
        LOGGER.debug("Resetting iteration {} with clearGraph={}", iteration, checkConsistency);
        super.resetIteration(iteration);
        if (debug) {
            algoInstance.logStackState();
            if (checkConsistency && !algoInstance.getExecutionGraph().checkExtensiveConsistency()) {
                throw HaltCheckerException.error("Explored an inconsistent execution graph");
            }
            algoInstance.writeExecutionGraphToFile(
                    Paths.get(this.reportPath, "iteration-complete-" + iteration + ".json")
                            .toString());
        }
    }

    /**
     * Returns the algorithm's current execution graph (used in tests and debugging).
     *
     * @return the current execution graph.
     */
    public ExecutionGraph getExecutionGraph() {
        return algoInstance.getExecutionGraph();
    }

    /**
     * Tears down the strategy at the end of a run and, if the tree logger is enabled, writes the
     * exploration-tree report.
     *
     * @param report the model-checker report.
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        algoInstance.teardown(report);
        StringBuilder tLogger = algoInstance.getTreeLog();
        StringBuilder inConGraphLogger = algoInstance.getInconsistentGraphLog();
        StringBuilder blockedGraphLogger = algoInstance.getBlockedGraphLog();
        StringBuilder leafSizeLogger = algoInstance.getLeafSizeLog();
        if (tLogger != null) {
            recordTreeLoggger(tLogger, inConGraphLogger, blockedGraphLogger, leafSizeLogger);
        }
    }

    /**
     * Appends the inconsistent-graph, blocked-graph, and leaf-size sections to the tree log and
     * writes it to {@code trust-tree-logger.txt} under the report path.
     *
     * @param tLogger the main tree log.
     * @param inConGraphLogger the inconsistent-graph log (may be {@code null}).
     * @param blockedGraphLogger the blocked-graph log (may be {@code null}).
     * @param leafSizeLogger the leaf-size log (may be {@code null}).
     */
    private void recordTreeLoggger(StringBuilder tLogger, StringBuilder inConGraphLogger, StringBuilder blockedGraphLogger, StringBuilder leafSizeLogger) {
        if (inConGraphLogger != null) {
            tLogger.append(System.lineSeparator()).append("$INCONSISTENT GRAPH:").append(System.lineSeparator()).append(inConGraphLogger);
        }
        if (blockedGraphLogger != null) {
            tLogger.append(System.lineSeparator()).append("$BLOCKED GRAPH:").append(System.lineSeparator()).append(blockedGraphLogger);
        }
        if (leafSizeLogger != null) {
            tLogger.append(System.lineSeparator()).append("$LEAF SIZE LOG:").append(System.lineSeparator()).append(leafSizeLogger);
        }
        String filePath = Paths.get(this.reportPath, "trust-tree-logger.txt").toString();
        LOGGER.info("Recording tree logger to {}", filePath);
        FileUtil.unsafeStoreToFile(filePath, tLogger.toString());
    }

    /**
     * Serializes the current consistent execution graph's schedule to {@code replay.json}; called by
     * the model checker when a violation is found so the interleaving can be reproduced.
     *
     * @throws JmcCheckerException if the schedule cannot be written.
     */
    @Override
    public void recordTrace() throws JmcCheckerException {
        String filePath = Paths.get(this.reportPath, "replay.json").toString();
        LOGGER.info("Recording trace to {}", filePath);
        algoInstance.recordTaskSchedule(filePath);
    }

    /**
     * Loads {@code replay.json} into {@link #recordedTrace} so the next iteration replays it.
     *
     * @throws JmcCheckerException if the schedule cannot be read.
     */
    @Override
    public void replayRecordedTrace() throws JmcCheckerException {
        recordedTrace =
                FileUtil.readTaskSchedule(Paths.get(this.reportPath, "replay.json").toString());
    }

    /** The fallback policy for choosing among schedulable active tasks when not guiding. */
    public enum SchedulingPolicy {
        /** Pick the first (lowest-id) schedulable active task. */
        FIFO,
        /** Pick a uniformly random schedulable active task. */
        RANDOM,
        /** Pick the last (highest-id) schedulable active task. */
        LIFO,
        // TODO : add RR
    }
}
