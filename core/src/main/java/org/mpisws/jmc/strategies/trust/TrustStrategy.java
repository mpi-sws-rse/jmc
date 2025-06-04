package org.mpisws.jmc.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.SchedulingChoice;
import org.mpisws.jmc.strategies.TrackActiveTasksStrategy;
import org.mpisws.jmc.util.files.FileUtil;

import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class TrustStrategy extends TrackActiveTasksStrategy {

    private final Logger LOGGER = LogManager.getLogger(TrustStrategy.class);

    private final Algo algoInstance;
    private final SchedulingPolicy policy;
    private final Random random;

    private final boolean debug;
    private final String reportPath;

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
        if (debug) {
            FileUtil.unsafeEnsurePath(reportPath);
        }
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
                    case FIFO ->
                            activeScheduleAbleTasks.isEmpty()
                                    ? null
                                    : activeScheduleAbleTasks.get(0);
                    case RANDOM -> {
                        int size = activeScheduleAbleTasks.size();
                        yield size == 0 ? null : activeScheduleAbleTasks.get(random.nextInt(size));
                    }
                });
    }

    @Override
    public void updateEvent(RuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
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
        LOGGER.debug("Resetting iteration {}", iteration);
        super.resetIteration(iteration);
        if (debug) {
            algoInstance.writeExecutionGraphToFile(
                    Paths.get(this.reportPath, "iteration-complete-" + iteration + ".json")
                            .toString());
        }
    }

    public ExecutionGraph getExecutionGraph() {
        return algoInstance.getExecutionGraph();
    }

    @Override
    public void teardown() {
        super.teardown();
        algoInstance.teardown();
    }

    public enum SchedulingPolicy {
        FIFO,
        RANDOM
    }
}
