package org.mpisws.jmc.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    public void initIteration(int iteration) {
        super.initIteration(iteration);
        algoInstance.initIteration(iteration);
    }

    @Override
    public SchedulingChoice nextTask() {
        // Always add 1 to the return value the strategy expects 1-indexed tasks but we store
        // 0-indexed tasks

        // If the algorithm has a task to execute, return it
        SchedulingChoice nextTask = algoInstance.nextTask();
        if (nextTask != null) {
            return nextTask;
        }

        // Otherwise, return an active, schedule-able task based on the policy
        Set<Long> activeTasks = getActiveTasks();
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
            algoInstance.updateEvent(e);
        }
    }

    @Override
    public void resetIteration(int iteration) {
        // TODO :: For debugging
        System.out.println("[TrustStrategy debug] : resetIteration(" + iteration + ")");
        super.resetIteration(iteration);
        if (debug) {
            algoInstance.writeExecutionGraphToFile(
                    Paths.get(this.reportPath, "iteration-" + iteration + ".json").toString());
        }
        algoInstance.resetIteration();
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
