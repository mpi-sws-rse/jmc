package org.mpi_sws.jmc.checker;

import org.mpi_sws.jmc.util.FileUtil;

import java.util.HashMap;

/**
 * Accumulates the outcome of a JMC model-checking run.
 *
 * <p>A report is a thin, string-keyed value bag plus an output directory. {@link JmcModelChecker}
 * creates one per {@code check} run and populates it as the run proceeds (iteration counts, timing,
 * error info); scheduling strategies also write to it (e.g. {@code blockedIterations}, {@code
 * replaySeed}). The typed accessors below are conveniences over the underlying {@link #setParam} /
 * {@link #getParam} map. The JUnit descriptors read it back (e.g. to check {@code @JmcExpectExecutions}).
 */
public class JmcModelCheckerReport {

    /** Backing store mapping report keys to values. */
    private final HashMap<String, Object> reportData;

    /** Directory into which reports and artifacts are written. */
    private final String reportPath;

    /**
     * Constructs a new report writing to the given directory.
     *
     * @param reportPath the directory for reports and artifacts
     */
    public JmcModelCheckerReport(String reportPath) {
        this.reportPath = reportPath;
        this.reportData = new HashMap<>();
    }

    /** Ensures the {@link #reportPath} directory exists (called at the start of a {@code check} run). */
    public void setupReportPath() {
        FileUtil.unsafeEnsurePath(reportPath);
    }

    /**
     * @return the report output directory
     */
    public String getReportPath() {
        return reportPath;
    }

    /**
     * Records the run's total elapsed time.
     *
     * @param totalTimeMillis the elapsed time value
     */
    public void setTotalTimeMillis(Long totalTimeMillis) {
        setParam("totalTimeMillis", totalTimeMillis);
    }

    /**
     * @return the recorded total elapsed time, or {@code null} if unset
     */
    public Long getTotalTimeMillis() {
        return getParam("totalTimeMillis");
    }

    /**
     * Records the iteration at which an error occurred ({@code -1} for a run-level failure).
     *
     * @param errorIteration the failing iteration index
     */
    public void setErrorIteration(Integer errorIteration) {
        setParam("errorIteration", errorIteration);
    }

    /**
     * @return the recorded error iteration, or {@code null} if none
     */
    public Integer getErrorIteration() {
        return getParam("errorIteration");
    }

    /**
     * Records a human-readable error message for the run.
     *
     * @param errorMessage the error message
     */
    public void setErrorMessage(String errorMessage) {
        setParam("errorMessage", errorMessage);
    }

    /**
     * @return the recorded error message, or {@code null} if none
     */
    public String getErrorMessage() {
        return getParam("errorMessage");
    }

    /**
     * Records the seed needed to reproduce (replay) the recorded schedule; written by the strategy.
     *
     * @param replaySeed the replay seed
     */
    public void setReplaySeed(Long replaySeed) {
        setParam("replaySeed", replaySeed);
    }

    /**
     * @return the recorded replay seed, or {@code null} if none
     */
    public Long getReplaySeed() {
        return getParam("replaySeed");
    }

    /**
     * Records how many iterations the run explored.
     *
     * @param totalIterations the total iteration count
     */
    public void setTotalIterations(Integer totalIterations) {
        setParam("totalIterations", totalIterations);
    }

    /**
     * @return the total iteration count, or {@code 0} if unset
     */
    public Integer getTotalIterations() {
        Object o = getParam("totalIterations");
        if (o == null) {
            return 0;
        }
        return (Integer) o;
    }

    /**
     * Records how many iterations were blocked (did not complete); written by the strategy.
     *
     * @param blockedIterations the blocked iteration count
     */
    public void setBlockedIterations(Integer blockedIterations) {
        setParam("blockedIterations", blockedIterations);
    }

    /**
     * @return the blocked iteration count, or {@code 0} if unset (so {@code total − blocked} is always
     *     well-defined)
     */
    public Integer getBlockedIterations() {
        Object o = getParam("blockedIterations");
        if (o == null) {
            return 0;
        }
        return (Integer) o;
    }

    /**
     * Stores an arbitrary key/value pair in the report — the primitive backing all typed setters.
     *
     * @param key the report key
     * @param value the value to store
     */
    public void setParam(String key, Object value) {
        this.reportData.put(key, value);
    }

    /**
     * Retrieves a report value, cast (unchecked) to the caller's expected type.
     *
     * @param key the report key
     * @param <T> the expected value type
     * @return the stored value, or {@code null} if the key is absent
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key) {
        if (!reportData.containsKey(key)) {
            return null;
        }
        return (T) reportData.get(key);
    }
}
