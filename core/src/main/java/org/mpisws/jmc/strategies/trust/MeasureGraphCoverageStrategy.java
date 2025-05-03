package org.mpisws.jmc.strategies.trust;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.runtime.*;
import org.mpisws.jmc.strategies.SchedulingStrategy;
import org.mpisws.jmc.util.StringUtil;
import org.mpisws.jmc.util.files.FileUtil;

import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class MeasureGraphCoverageStrategy implements SchedulingStrategy {

    private static final Logger LOGGER = LogManager.getLogger(MeasureGraphCoverageStrategy.class);

    private final ExecutionGraphSimulator simulator;

    private final ConcurrentHashMap<String, Integer> visitedGraphs;
    private final MeasuringThread measuringThread;
    private final ArrayList<Integer> coverages;

    private final SchedulingStrategy schedulingStrategy;
    private final MeasureGraphCoverageStrategyConfig config;

    private long timeStart;

    public MeasureGraphCoverageStrategy(
            SchedulingStrategy schedulingStrategy, MeasureGraphCoverageStrategyConfig config) {
        this.schedulingStrategy = schedulingStrategy;
        this.simulator = new ExecutionGraphSimulator();
        this.visitedGraphs = new ConcurrentHashMap<>();
        this.coverages = new ArrayList<>();
        this.config = config;
        if (config.isRecordPerIteration()) {
            this.measuringThread = null;
        } else {
            this.measuringThread = new MeasuringThread(this, config.getMeasuringFrequency());
        }

        FileUtil.unsafeEnsurePath(config.getRecordPath());
    }

    private void updateCoverage() {
        int val = this.visitedGraphs.size();
        this.coverages.add(val);
    }

    private static class MeasuringThread extends Thread {
        private final MeasureGraphCoverageStrategy strategy;
        private final Duration measuringFrequency;

        private final CompletableFuture<Void> future;

        public MeasuringThread(MeasureGraphCoverageStrategy strategy, Duration measuringFrequency) {
            this.strategy = strategy;
            this.measuringFrequency = measuringFrequency;
            this.future = new CompletableFuture<>();
        }

        @Override
        public void run() {
            while (!future.isDone()) {
                try {
                    Thread.sleep(measuringFrequency.toMillis());
                    strategy.updateCoverage();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        public void stopMeasuring() {
            future.complete(null);
        }
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report)
            throws HaltCheckerException {
        if (iteration == 0) {
            this.timeStart = System.currentTimeMillis();
            if (!config.isRecordPerIteration()) {
                this.measuringThread.start();
            }
        }
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
        this.schedulingStrategy.resetIteration(iteration);
        ExecutionGraph executionGraph = simulator.getExecutionGraph();
        if (this.schedulingStrategy instanceof TrustStrategy t) {
            ExecutionGraph that = t.getExecutionGraph();
            if(!executionGraph.equals(that)) {
                LOGGER.error("Execution graph mismatch: {} vs {}", executionGraph, that);
            }
        }
        String json = executionGraph.toJsonStringIgnoreLocation();
        try {
            String hash = StringUtil.sha256Hash(json);
            if (visitedGraphs.containsKey(hash)) {
                visitedGraphs.put(hash, visitedGraphs.get(hash) + 1);
            } else {
                visitedGraphs.put(hash, 1);
                if (config.isDebugEnabled()) {
                    FileUtil.unsafeStoreToFile(
                            Paths.get(config.getRecordPath(), visitedGraphs.size() + ".json").toString(), json);
                }
            }
            if (config.isRecordPerIteration()) {
                updateCoverage();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void teardown() {
        if (!config.isRecordPerIteration()) {
            measuringThread.stopMeasuring();
            try {
                measuringThread.join();
            } catch (InterruptedException e) {
                LOGGER.error("Error while waiting for measuring thread to finish", e);
                return;
            }
        }
        Long timeDiff = System.currentTimeMillis() - timeStart;
        Duration d = Duration.ofMillis(timeDiff);
        simulator.reset();
        schedulingStrategy.teardown();
        if (config.shouldRecordGraphs()) {
            FileOutputStream fileOutputStream =
                    FileUtil.unsafeCreateFile(
                            Paths.get(config.getRecordPath(), "hash_coverage.txt").toString());
            for (HashMap.Entry<String, Integer> entry : visitedGraphs.entrySet()) {
                StringBuilder sb = new StringBuilder();
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                try {
                    fileOutputStream.write(sb.toString().getBytes());
                } catch (Exception e) {
                    LOGGER.error("Error while writing to file", e);
                }
            }
            try {
                fileOutputStream.close();
            } catch (Exception e) {
                LOGGER.error("Error while closing file output stream", e);
            }
        }
        Gson gson = new Gson();
        JsonArray jsonArray = new JsonArray();
        for (int coverage : coverages) {
            jsonArray.add(coverage);
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("time", d.toMillis());
        jsonObject.add("coverage", jsonArray);
        String json = gson.toJson(jsonObject);
        FileUtil.unsafeStoreToFile(
                Paths.get(config.getRecordPath(), "coverage.json").toString(), json);
    }
}
