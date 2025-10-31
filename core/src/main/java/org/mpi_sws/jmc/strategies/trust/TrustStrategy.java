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

    protected final Algo algoInstance;
    private final SchedulingPolicy policy;
    private final Random random;

    private final boolean debug;
    private final String reportPath;
    private List<SchedulingChoice<?>> recordedTrace;

    public TrustStrategy() {
        this(System.nanoTime(), SchedulingPolicy.FIFO, false, "build/test-results/jmc-report");
    }

    public TrustStrategy(
            Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath) {
        super(List.of(new TrackTasks()));
        this.random = new Random(randomSeed);
        this.algoInstance = new Algo();
        this.policy = policy;
        this.debug = debug;
        this.reportPath = reportPath;
        this.recordedTrace = null;
    }

    public TrustStrategy(
            Long randomSeed, SchedulingPolicy policy, boolean debug, String reportPath, boolean hasTreeLogger) {
        super(List.of(new TrackTasks()));
        this.random = new Random(randomSeed);
        this.algoInstance = new Algo(hasTreeLogger);
        this.policy = policy;
        this.debug = debug;
        this.reportPath = reportPath;
        this.recordedTrace = null;
    }

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

    @Override
    public SchedulingChoice<?> nextTask() {
        if (recordedTrace != null) {
            // If we have a recorded trace, return the next task from it
            SchedulingChoice<?> next = recordedTrace.remove(0);
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

        // Always add 1 to the return value the strategy expects 1-indexed tasks but we store
        // 0-indexed tasks

        // Otherwise, return an active, schedule-able task based on the policy
        Set<Long> activeTasks = getActiveTasks();
        // If the algorithm has a task to execute, return it
        SchedulingChoice<?> nextTask = algoInstance.nextTask();
        if (nextTask != null) {
            if (!activeTasks.contains(nextTask.getTaskId())) {
                LOGGER.debug("Guiding trace led us to a task that is not active: {}", nextTask);
            }
            return nextTask;
        }

        List<Long> activeScheduleAbleTasks =
                algoInstance.getSchedulableTasks().stream()
                        // Adding 1 here for all further uses of the task ID
                        .map((t) -> t + 1)
                        .filter(activeTasks::contains)
                        .toList();

        // If the policy is FIFO, return the first active, schedule-able task
        return SchedulingChoice.task(
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
    }

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

    @Override
    public void resetIteration(int iteration) {
        resetIteration(iteration, true);
    }

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

    public ExecutionGraph getExecutionGraph() {
        return algoInstance.getExecutionGraph();
    }

    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        algoInstance.teardown(report);
        StringBuilder tLogger = algoInstance.getTreeLog();
        StringBuilder inConGraphLogger = algoInstance.getInconsistentGraphLog();
        StringBuilder blockedGraphLogger = algoInstance.getBlockedGraphLog();
        if (tLogger != null) {
            recordTreeLoggger(tLogger, inConGraphLogger, blockedGraphLogger);
        }
    }

    private void recordTreeLoggger(StringBuilder tLogger, StringBuilder inConGraphLogger, StringBuilder blockedGraphLogger) {
        if (inConGraphLogger != null) {
            tLogger.append(System.lineSeparator()).append("$INCONSISTENT GRAPH:").append(System.lineSeparator()).append(inConGraphLogger);
        }
        if (blockedGraphLogger != null) {
            tLogger.append(System.lineSeparator()).append("$BLOCKED GRAPH:").append(System.lineSeparator()).append(blockedGraphLogger);
        }
        String filePath = Paths.get(this.reportPath, "trust-tree-logger.txt").toString();
        LOGGER.info("Recording tree logger to {}", filePath);
        FileUtil.unsafeStoreToFile(filePath, tLogger.toString());
    }

    @Override
    public void recordTrace() throws JmcCheckerException {
        String filePath = Paths.get(this.reportPath, "replay.json").toString();
        LOGGER.info("Recording trace to {}", filePath);
        algoInstance.recordTaskSchedule(filePath);
    }

    @Override
    public void replayRecordedTrace() throws JmcCheckerException {
        recordedTrace =
                FileUtil.readTaskSchedule(Paths.get(this.reportPath, "replay.json").toString());
    }

    public enum SchedulingPolicy {
        FIFO,
        RANDOM,
        LIFO,
        // TODO : add RR
    }
}
