package org.mpi_sws.jmc.strategies.estimation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.util.FileUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the per-trial point estimates produced by a Monte Carlo estimation strategy, persists
 * them, and computes the final estimate across all trials.
 *
 * <p>Every estimation strategy runs many independent random trials. Each completed trial yields one
 * unbiased point estimate of the quantity of interest (for instance, the number of distinct
 * executions of the program). Because every trial is an independent, unbiased estimate of the same
 * quantity, the unbiased estimate over the whole run is the arithmetic mean of the per-trial
 * estimates. Averaging is also what reduces the variance of the estimate as the number of trials
 * grows, which is the entire point of running many Monte Carlo trials.
 *
 * <p>This class replaces the per-strategy {@code StringBuilder} that previously accumulated the
 * estimates as text: keeping the raw numeric values means the final estimate can be computed
 * directly, without re-parsing strings.
 */
public class EstimationCollector {

    private static final Logger LOGGER = LogManager.getLogger(EstimationCollector.class);

    private final List<Double> estimations = new ArrayList<>();

    /**
     * Records the point estimate of a single completed trial.
     *
     * @param estimation the estimate produced for the trial that just finished
     */
    public void record(double estimation) {
        estimations.add(estimation);
    }

    /** Returns the number of trials recorded so far. */
    public int trialCount() {
        return estimations.size();
    }

    /**
     * Computes the final estimate across all recorded trials, i.e. the arithmetic mean of the
     * per-trial point estimates.
     *
     * @return the mean of the recorded estimates, or {@link Double#NaN} if no trial was recorded
     */
    public double finalEstimate() {
        if (estimations.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (double estimation : estimations) {
            sum += estimation;
        }
        return sum / estimations.size();
    }

    /**
     * Computes the standard error of the final estimate: an indication of how much the mean would
     * be expected to vary if the whole run were repeated. It shrinks as more trials are collected.
     *
     * @return the standard error of the mean, or {@link Double#NaN} if fewer than two trials were
     *     recorded (the standard error is undefined for a single sample)
     */
    public double standardError() {
        int n = estimations.size();
        if (n < 2) {
            return Double.NaN;
        }
        double mean = finalEstimate();
        double sumSquaredDeviations = 0.0;
        for (double estimation : estimations) {
            double deviation = estimation - mean;
            sumSquaredDeviations += deviation * deviation;
        }
        // Sample variance (Bessel's correction) divided by n gives the variance of the mean.
        double sampleVariance = sumSquaredDeviations / (n - 1);
        return Math.sqrt(sampleVariance / n);
    }

    /** Returns the per-trial estimates, one per line, in the order they were recorded. */
    public String perTrialReport() {
        StringBuilder sb = new StringBuilder();
        for (double estimation : estimations) {
            sb.append(estimation).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Persists the collected estimates to two files and logs the final estimate.
     *
     * <ul>
     *   <li>{@code perTrialFileName} receives one estimate per line, exactly as before.
     *   <li>{@code finalResultFileName} receives the single final estimate computed across all
     *       trials.
     * </ul>
     *
     * The final estimate is additionally reported through {@code LOGGER.info}.
     *
     * @param reportDir directory the result files are written to
     * @param perTrialFileName name of the file holding one estimate per trial
     * @param finalResultFileName name of the file holding the final estimate
     */
    public void save(String reportDir, String perTrialFileName, String finalResultFileName) {
        final Path perTrialPath = Paths.get(reportDir, perTrialFileName);
        FileUtil.unsafeStoreToFile(perTrialPath.toString(), perTrialReport());
        LOGGER.info(
                "The aggregation of estimation per each iteration can be found in the file: {}",
                perTrialPath.toString());

        final double finalEstimate = finalEstimate();
        final double standardError = standardError();
        final Path finalPath = Paths.get(reportDir, finalResultFileName);
        StringBuilder finalReport = new StringBuilder();
        finalReport
                .append("Number of trials: ")
                .append(trialCount())
                .append(System.lineSeparator())
                .append("Final estimation (mean over trials): ")
                .append(finalEstimate)
                .append(System.lineSeparator())
                .append("Standard error of the mean: ")
                .append(standardError)
                .append(System.lineSeparator());
        FileUtil.unsafeStoreToFile(finalPath.toString(), finalReport.toString());
        LOGGER.info(
                "The final estimation result over {} trials is {} (standard error {}). "
                        + "Saved to file: {}",
                trialCount(),
                finalEstimate,
                standardError,
                finalPath.toString());
    }
}
