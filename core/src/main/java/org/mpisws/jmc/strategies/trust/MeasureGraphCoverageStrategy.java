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

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class MeasureGraphCoverageStrategy implements SchedulingStrategy {

    private static final Logger LOGGER =
            LogManager.getLogger(MeasureGraphCoverageStrategy.class);

    private final ExecutionGraphSimulator simulator;

    private final ConcurrentHashMap<String, Integer> visitedGraphs;
    private final MeasuringThread measuringThread;

    private final SchedulingStrategy schedulingStrategy;

    private final boolean debug;
    private final String recordPath;

    private long timeStart;

    public MeasureGraphCoverageStrategy(
            SchedulingStrategy schedulingStrategy,
            boolean debug,
            String recordPath,
            Duration measuringFrequency) {
        this.schedulingStrategy = schedulingStrategy;
        this.simulator = new ExecutionGraphSimulator();
        this.visitedGraphs = new ConcurrentHashMap<>();
        this.measuringThread =
                new MeasuringThread(this, measuringFrequency);
        this.recordPath = recordPath;
        this.debug = debug;

        if (recordPath != null && !recordPath.isEmpty()) {
            FileUtil.unsafeEnsurePath(recordPath);
        }
    }

    private static class MeasuringThread extends Thread {
        private final MeasureGraphCoverageStrategy strategy;
        private final Duration measuringFrequency;

        private final List<Integer> coverages;
        private final CompletableFuture<Void> future;

        public MeasuringThread(
                MeasureGraphCoverageStrategy strategy, Duration measuringFrequency) {
            this.strategy = strategy;
            this.measuringFrequency = measuringFrequency;
            this.coverages = new ArrayList<>();
            this.future = new CompletableFuture<>();
        }

        @Override
        public void run() {
            while (!future.isDone()) {
                try {
                    Thread.sleep(measuringFrequency.toMillis());
                    coverages.add(strategy.visitedGraphs.size());
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        public void stopMeasuring() {
            future.complete(null);
        }

        public List<Integer> getCoverages() {
            return coverages;
        }
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report)
            throws HaltCheckerException {
        if (iteration == 0) {
            this.timeStart = System.currentTimeMillis();
            this.measuringThread.start();
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
        String json = executionGraph.toJsonStringIgnoreLocation();
        try {
            String hash = StringUtil.sha256Hash(json);
            if (visitedGraphs.containsKey(hash)) {
                visitedGraphs.put(hash, visitedGraphs.get(hash) + 1);
            } else {
                visitedGraphs.put(hash, 1);
            }
            if (debug) {
                FileUtil.unsafeStoreToFile(Paths.get(recordPath, hash + ".json").toString(), json);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void teardown() {
        measuringThread.stopMeasuring();
        try {
            measuringThread.join();
        } catch (InterruptedException e) {
            LOGGER.error("Error while waiting for measuring thread to finish", e);
            return;
        }
        Long timeDiff = System.currentTimeMillis() - timeStart;
        Duration d = Duration.ofMillis(timeDiff);
        simulator.reset();
        schedulingStrategy.teardown();
        if (recordPath != null && !recordPath.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (HashMap.Entry<String, Integer> entry : visitedGraphs.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            FileUtil.unsafeStoreToFile(
                    Paths.get(recordPath, "hash_coverage.txt").toString(), sb.toString());
            Gson gson = new Gson();
            List<Integer> coverages = measuringThread.getCoverages();
            JsonArray jsonArray = new JsonArray();
            for (int coverage : coverages) {
                jsonArray.add(coverage);
            }
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("time", d.toMillis());
            jsonObject.add("coverage", jsonArray);
            String json = gson.toJson(jsonObject);
            FileUtil.unsafeStoreToFile(Paths.get(recordPath, "coverage.json").toString(), json);
        }
    }
}
