package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.*;
import org.mpisws.jmc.strategies.SchedulingStrategy;
import org.mpisws.jmc.util.files.FileUtil;

import java.nio.file.Paths;
import java.util.HashMap;

public class MeasureGraphCoverageStrategy implements SchedulingStrategy {

    private final ExecutionGraphSimulator simulator;

    private final HashMap<Integer, Integer> visitedGraphs;

    private final SchedulingStrategy schedulingStrategy;

    private final boolean record;
    private final String recordPath;

    public MeasureGraphCoverageStrategy(SchedulingStrategy schedulingStrategy, boolean record, String recordPath) {
        this.schedulingStrategy = schedulingStrategy;
        this.simulator = new ExecutionGraphSimulator();
        this.visitedGraphs = new HashMap<>();
        this.record = record;
        this.recordPath = recordPath;

        if (record) {
            FileUtil.unsafeEnsurePath(recordPath);
        }
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) throws HaltCheckerException {
        this.simulator.reset();
        this.schedulingStrategy.initIteration(iteration, report);
    }

    @Override
    public void updateEvent(RuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        this.schedulingStrategy.updateEvent(event);
        this.simulator.updateEvent(event);
    }

    @Override
    public SchedulingChoice<?> nextTask() {
        return this.schedulingStrategy.nextTask();
    }

    @Override
    public void resetIteration(int iteration) {
        ExecutionGraph executionGraph = simulator.getExecutionGraph();
        int hash = executionGraph.hashCode();
        if (visitedGraphs.containsKey(hash)) {
            visitedGraphs.put(hash, visitedGraphs.get(hash) + 1);
        } else {
            visitedGraphs.put(hash, 1);
        }

        if (record) {
            String json = executionGraph.toJsonString();
            FileUtil.unsafeStoreToFile(
                    Paths.get(recordPath, hash + ".json").toString(), json);
        }
    }

    @Override
    public void teardown() {
        simulator.reset();
        schedulingStrategy.teardown();
        if (record) {
            StringBuilder sb = new StringBuilder();
            for (HashMap.Entry<Integer, Integer> entry : visitedGraphs.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            FileUtil.unsafeStoreToFile(
                    Paths.get(recordPath, "coverage.txt").toString(), sb.toString());
        }
    }
}
