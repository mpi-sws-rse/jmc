package org.mpi_sws.jmc.strategies.trust;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.SchedulingStrategy;
import org.mpi_sws.jmc.util.StringUtil;
import org.mpi_sws.jmc.util.FileUtil;

import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * A scheduling strategy that measures the coverage of execution graphs during the model checking
 * process.
 *
 * <p>This strategy records the coverage of execution graphs and stores them in a specified path. It
 * can also measure the coverage per iteration or at a specified frequency.
 */
public class MeasureGraphCoverageStrategy implements SchedulingStrategy {

    private static final Logger LOGGER = LogManager.getLogger(MeasureGraphCoverageStrategy.class);

    /** Rebuilds each iteration's execution graph so it can be hashed. */
    private final ExecutionGraphSimulator simulator;

    /** Hash of each distinct execution graph to the number of times it was visited. */
    private final ConcurrentHashMap<String, Integer> visitedGraphs;
    /** Hashes of the distinct coverage graphs seen so far. */
    private final Set<String> coveredGraphs;
    /** Background sampler that snapshots coverage at a fixed frequency (null in per-iteration mode). */
    private final MeasuringThread measuringThread;
    /** The coverage value (number of distinct graphs) sampled over time. */
    private final ArrayList<Integer> coverages;

    /** The wrapped strategy that actually drives exploration. */
    private final SchedulingStrategy schedulingStrategy;
    /** Configuration controlling recording mode, output path, and sampling frequency. */
    private final MeasureGraphCoverageStrategyConfig config;

    /** Wall-clock start time of the run, for the time axis of the coverage report. */
    private long timeStart;

    /**
     * Wraps a strategy with coverage measurement.
     *
     * @param schedulingStrategy the underlying strategy to measure.
     * @param config the coverage-measurement configuration.
     */
    public MeasureGraphCoverageStrategy(
            SchedulingStrategy schedulingStrategy, MeasureGraphCoverageStrategyConfig config) {
        this.schedulingStrategy = schedulingStrategy;
        this.simulator = new ExecutionGraphSimulator();
        this.coveredGraphs = new HashSet<>();
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

    /** Appends the current number of distinct coverage graphs to the coverage-over-time series. */
    private void updateCoverage() {
        int val = this.coveredGraphs.size();
        this.coverages.add(val);
    }

    /** Background thread that samples coverage every {@code measuringFrequency} until stopped. */
    private static class MeasuringThread extends Thread {
        /** The strategy whose coverage is sampled. */
        private final MeasureGraphCoverageStrategy strategy;
        /** How often to sample. */
        private final Duration measuringFrequency;

        /** Completed to signal the sampling loop to stop. */
        private final CompletableFuture<Void> future;

        /**
         * Creates a sampler for the given strategy and frequency.
         *
         * @param strategy the strategy to sample.
         * @param measuringFrequency the sampling period.
         */
        public MeasuringThread(MeasureGraphCoverageStrategy strategy, Duration measuringFrequency) {
            this.strategy = strategy;
            this.measuringFrequency = measuringFrequency;
            this.future = new CompletableFuture<>();
        }

        /** Samples coverage at the configured frequency until stopped or interrupted. */
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

        /** Signals the sampling loop to stop. */
        public void stopMeasuring() {
            future.complete(null);
        }
    }

    /**
     * Starts the sampler (on iteration 0) and resets the simulator, then delegates to the wrapped
     * strategy.
     *
     * @param iteration the iteration index.
     * @param report the model-checker report.
     * @throws HaltCheckerException if the wrapped strategy halts the checker.
     */
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

    /**
     * Forwards the event to the wrapped strategy and to the simulator (which mirrors it into a
     * graph).
     *
     * @param event the runtime event.
     * @throws HaltTaskException if the wrapped strategy halts the task.
     * @throws HaltExecutionException if the wrapped strategy halts the execution.
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event)
            throws HaltTaskException, HaltExecutionException {
        this.schedulingStrategy.updateEvent(event);
        this.simulator.updateEvent(event);
    }

    /**
     * Delegates scheduling to the wrapped strategy (coverage measurement does not change scheduling).
     *
     * @return the wrapped strategy's next choice.
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        return this.schedulingStrategy.nextTask();
    }

    /**
     * At the end of an iteration, hashes the reconstructed execution and coverage graphs to update
     * the distinct-graph counters (optionally dumping new graphs when debugging).
     *
     * @param iteration the iteration index.
     */
    @Override
    public void resetIteration(int iteration) {
        this.schedulingStrategy.resetIteration(iteration);
        ExecutionGraph executionGraph = simulator.getExecutionGraph();
        CoverageGraph coverageGraph = simulator.getCoverageGraph();
        String json = executionGraph.toJsonStringIgnoreLocation();
        String coverage = coverageGraph.toString();
        // System.out.println(coverage);
        try {
            String hash = StringUtil.sha256Hash(json);
            String hashCoverage = StringUtil.sha256Hash(coverage);
            if (!coveredGraphs.contains(hashCoverage)) {
                coveredGraphs.add(hashCoverage);
                if (config.isDebugEnabled()) {
                    FileUtil.unsafeStoreToFile(
                            Paths.get(config.getRecordPath(), coveredGraphs.size() + ".txt")
                                    .toString(),
                            coverage);
                }
            }
            if (visitedGraphs.containsKey(hash)) {
                visitedGraphs.put(hash, visitedGraphs.get(hash) + 1);
            } else {
                visitedGraphs.put(hash, 1);
                if (config.isDebugEnabled()) {
                    FileUtil.unsafeStoreToFile(
                            Paths.get(config.getRecordPath(), visitedGraphs.size() + ".json")
                                    .toString(),
                            json);
                }
            }
            if (config.isRecordPerIteration()) {
                updateCoverage();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops the sampler, tears down the wrapped strategy, and writes the coverage artifacts
     * (per-graph hash counts and the coverage-over-time series) under the configured record path.
     *
     * @param report the model-checker report.
     */
    @Override
    public void teardown(JmcModelCheckerReport report) {
        if (!config.isRecordPerIteration()) {
            measuringThread.stopMeasuring();
            try {
                measuringThread.join();
            } catch (InterruptedException e) {
                LOGGER.error("Error while waiting for measuring thread to finish", e);
                return;
            }
        }
        long timeDiff = System.currentTimeMillis() - timeStart;
        Duration d = Duration.ofMillis(timeDiff);
        simulator.reset();
        schedulingStrategy.teardown(report);
        if (config.shouldRecordGraphs()) {
            FileOutputStream fileOutputStream =
                    FileUtil.unsafeCreateFile(
                            Paths.get(config.getRecordPath(), "hash_coverage.txt").toString());
            if (fileOutputStream != null) {
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
            } else {
                LOGGER.error("Failed to create file for hash coverage");
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

        LOGGER.info("Covered graphs: {}", coveredGraphs.size());
    }
}
