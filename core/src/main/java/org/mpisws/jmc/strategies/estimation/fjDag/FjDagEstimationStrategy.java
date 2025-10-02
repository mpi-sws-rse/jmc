package org.mpisws.jmc.strategies.estimation.fjDag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.HaltCheckerException;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.estimation.EstimationStrategy;
import org.mpisws.jmc.strategies.trust.Event;
import org.mpisws.jmc.util.FileUtil;

import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class FjDagEstimationStrategy extends RandomSchedulingStrategy implements EstimationStrategy {

    private final Logger LOGGER = LogManager.getLogger(FjDagEstimationStrategy.class);

    private final FjDagEstimator est;

    private final StringBuilder estimatorCollector = new StringBuilder();

    public FjDagEstimationStrategy(Long seed) {
        // TODO : Fix the hard coded path
        super(seed, "build/test-results/jmc-report");
        est = new FjDagEstimator();
    }


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
                int index = random.nextInt(activeThreads.size());
                taskToSchedule = (Long) activeThreads.toArray()[index];
            }
        }
        return makeSchedulingChoice(taskToSchedule);
    }

    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        List<Event> events = compileRuntimeEvent(event);
        est.updateEvent(events, getActiveTasks());
    }

    @Override
    public void resetIteration(int iteration) {
        super.resetIteration(iteration);
        LOGGER.debug("Finished iteration {} with expected value: {}", iteration, est.getExpectedValue());
        estimatorCollector.append(est.getExpectedValue()).append(System.lineSeparator());
        est.reset();
    }

    @Override
    public void teardown() {
        super.teardown();
        // TODO : Fix the hard coded path
        saveResults();
    }

    protected void saveResults() {
        FileUtil.unsafeStoreToFile(
                Paths.get("build/test-results/jmc-report/", "FjDagEstimateResult.txt").toString(), estimatorCollector.toString());
    }

    public StringBuilder getEstimatorCollector() {
        return estimatorCollector;
    }


}
