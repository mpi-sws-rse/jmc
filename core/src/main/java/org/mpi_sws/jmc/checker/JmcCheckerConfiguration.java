package org.mpi_sws.jmc.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.checker.exceptions.JmcInvalidConfigurationException;
import org.mpi_sws.jmc.runtime.JmcRuntimeConfiguration;
import org.mpi_sws.jmc.strategies.*;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

import java.time.Duration;

/**
 * Configuration for the JMC checker.
 *
 * <p>This class encapsulates the configuration parameters for running the JMC checker, including
 * the number of iterations, strategy type, debug mode, report path, seed, and timeout.
 *
 * <p>Use the {@link JmcCheckerConfiguration.Builder} to create a configuration instance.
 */
public class JmcCheckerConfiguration {

    private static final Logger LOGGER = LogManager.getLogger(JmcCheckerConfiguration.class);

    /** Number of iterations (interleavings) to explore; {@code 0} means "unbounded — use the timeout". */
    private Integer numIterations;

    /** Name of the scheduling strategy, used when {@link #strategyConstructor} is not set. */
    private String strategyType;

    /** Symbolic solver selection ({@code "off"} disables symbolic execution). */
    private String solver;

    /** Explicit strategy factory; when set it takes precedence over {@link #strategyType}. */
    private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

    /** Whether debug logging / artifacts are enabled. */
    private boolean debug;

    /** RNG seed for randomized strategies. */
    private Long seed;

    /** Budget for the estimator strategy. */
    private int budget;

    /** Target bug depth {@code d} for the PCT strategies. */
    private int bugDepth;

    /** Fair-suffix bound for the {@code fair-pct} strategy ({@code <= 0} selects automatic mode). */
    private int pctFairBound;

    /** Directory where reports and artifacts are written. */
    private String reportPath;

    /** Wall-clock timeout for the run, or {@code null} for no timeout. */
    private Duration timeout;

    /** Scheduling policy for the {@code trust} strategy family. */
    private TrustStrategy.SchedulingPolicy schedulingPolicy;

    /** Private: instances are created only through the {@link Builder} (or {@link #fromAnnotation}). */
    private JmcCheckerConfiguration() {
    }

    /**
     * Returns the number of iterations to run the checker.
     *
     * @return the number of iterations
     */
    public Integer getNumIterations() {
        return numIterations;
    }

    /**
     * Returns the path where the report will be saved.
     *
     * @return the report path as a string
     */
    public String getReportPath() {
        return reportPath;
    }

    /**
     * Returns the symbolic solver selection.
     *
     * @return the solver setting ({@code "off"} when disabled)
     */
    public String getSolver() {
        return solver;
    }

    /**
     * Returns the type of scheduling strategy to be used.
     *
     * @return the strategy type as a string
     */
    public String getStrategyType() {
        return strategyType;
    }

    /**
     * Returns the debug mode status.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean getDebug() {
        return debug;
    }

    /**
     * Returns the seed for the checker.
     *
     * @return the seed, or null if no seed is set
     */
    public Long getSeed() {
        return seed;
    }

    /**
     * Returns the estimator budget.
     *
     * @return the budget
     */
    public int getBudget() {
        return budget;
    }

    /**
     * Returns the target bug depth {@code d} used by the PCT strategies.
     *
     * @return the bug depth
     */
    public int getBugDepth() {
        return bugDepth;
    }

    /**
     * Returns the fair-suffix bound used by the {@code fair-pct} strategy.
     *
     * @return the fair bound; a value {@code <= 0} means automatic mode
     */
    public int getPctFairBound() {
        return pctFairBound;
    }

    /**
     * Sets the seed for the checker.
     *
     * @param seed the seed to set.
     */
    public void setSeed(Long seed) {
        this.seed = seed;
    }

    /**
     * Sets the symbolic solver selection.
     *
     * @param solver the solver setting to use
     */
    public void setSolver(String solver) {
        this.solver = solver;
    }

    /**
     * Sets the estimator budget.
     *
     * @param budget the budget to use
     */
    public void setBudget(int budget) {
        this.budget = budget;
    }

    /**
     * Sets the scheduling policy for the {@code trust} strategy family.
     *
     * @param schedulingPolicy the scheduling policy to use
     */
    public void setSchedulingPolicy(TrustStrategy.SchedulingPolicy schedulingPolicy) {
        this.schedulingPolicy = schedulingPolicy;
    }

    /**
     * Returns the timeout duration for the checker.
     *
     * @return the timeout duration, or null if no timeout is set
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Returns the scheduling policy for the {@code trust} strategy family.
     *
     * @return the scheduling policy
     */
    public TrustStrategy.SchedulingPolicy getSchedulingPolicy() {
        return schedulingPolicy;
    }

    /**
     * Converts this configuration to a runtime configuration.
     *
     * @return a {@link JmcRuntimeConfiguration} based on this configuration
     * @throws JmcInvalidStrategyException if the strategy type is invalid or the strategy cannot be
     *                                     created
     */
    public JmcRuntimeConfiguration toRuntimeConfiguration() throws JmcInvalidStrategyException {
        SchedulingStrategy strategy;
        SchedulingStrategyConfiguration.Builder strategyConfigurationBuilder =
                new SchedulingStrategyConfiguration.Builder()
                        .seed(seed)
                        .budget(budget)
                        .solver(solver)
                        .trustSchedulingPolicy(schedulingPolicy)
                        .bugDepth(bugDepth)
                        .pctFairBound(pctFairBound);
        if (debug) {
            strategyConfigurationBuilder.debug();
            strategyConfigurationBuilder.reportPath(reportPath);
        }
        if (strategyConstructor != null) {
            strategy = strategyConstructor.create(strategyConfigurationBuilder.build());
        } else {
            strategy =
                    SchedulingStrategyFactory.createSchedulingStrategy(
                            strategyType, strategyConfigurationBuilder.build());
        }
        if (strategy == null) {
            throw new JmcInvalidStrategyException("Strategy is null");
        }
        return new JmcRuntimeConfiguration.Builder()
                .strategy(strategy)
                .debug(debug)
                .reportPath(reportPath)
                .build();
    }

    /**
     * Creates a JmcCheckerConfiguration from the given annotation.
     *
     * @param annotation the JmcCheckConfiguration annotation
     * @return a JmcCheckerConfiguration instance
     * @throws JmcCheckerException if the configuration is invalid
     */
    public static JmcCheckerConfiguration fromAnnotation(JmcCheckConfiguration annotation)
            throws JmcCheckerException {
        if (!SchedulingStrategyFactory.isValidStrategy(annotation.strategy())) {
            throw new JmcInvalidStrategyException("Invalid strategy: " + annotation.strategy());
        }
        return new Builder()
                .numIterations(annotation.numIterations())
                .strategyType(annotation.strategy())
                .solver(annotation.solver())
                .debug(annotation.debug())
                .reportPath(annotation.reportPath())
                .seed(annotation.seed())
                .budget(annotation.budget())
                .bugDepth(annotation.bugDepth())
                .pctFairBound(annotation.pctFairBound())
                .timeout(annotation.timeout())
                .schedulingPolicy(annotation.schedulingPolicy())
                .build();
    }

    /**
     * Fluent builder for {@link JmcCheckerConfiguration}.
     *
     * <p>Each setter mirrors a configuration field and returns {@code this} for chaining; {@link
     * #build()} validates and produces an immutable configuration. A fresh builder starts from the
     * defaults documented in its constructor.
     */
    public static class Builder {
        /** See {@link JmcCheckerConfiguration#numIterations}. */
        private Integer numIterations;

        /** See {@link JmcCheckerConfiguration#strategyType}. */
        private String strategyType;

        /** See {@link JmcCheckerConfiguration#solver}. */
        private String solver;

        /** See {@link JmcCheckerConfiguration#strategyConstructor}. */
        private SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor;

        /** See {@link JmcCheckerConfiguration#debug}. */
        private boolean debug;

        /** See {@link JmcCheckerConfiguration#reportPath}. */
        private String reportPath;

        /** See {@link JmcCheckerConfiguration#timeout}. */
        private Duration timeout;

        /** See {@link JmcCheckerConfiguration#seed}. */
        private Long seed;

        /** See {@link JmcCheckerConfiguration#budget}. */
        private int budget;

        /** See {@link JmcCheckerConfiguration#bugDepth}. */
        private int bugDepth;

        /** See {@link JmcCheckerConfiguration#pctFairBound}. */
        private int pctFairBound;

        /** See {@link JmcCheckerConfiguration#schedulingPolicy}. */
        private TrustStrategy.SchedulingPolicy schedulingPolicy;

        /**
         * Creates a builder pre-populated with the default configuration: {@code numIterations = 0},
         * {@code strategyType = "random"}, {@code schedulingPolicy = RANDOM}, {@code debug = false},
         * {@code reportPath = "build/test-results/jmc-report"}, {@code solver = "off"}, {@code seed =
         * System.nanoTime()}, {@code budget = 2}, {@code bugDepth = 3}, {@code pctFairBound = 0}, and no
         * timeout.
         */
        public Builder() {
            this.numIterations = 0;
            this.strategyType = "random";
            this.schedulingPolicy = TrustStrategy.SchedulingPolicy.RANDOM;
            this.debug = false;
            this.reportPath = "build/test-results/jmc-report";
            this.solver = "off";
            this.seed = System.nanoTime();
            this.budget = 2;
            this.bugDepth = 3;
            this.pctFairBound = 0;
            this.timeout = null;
        }

        /**
         * @param numIterations the number of iterations to explore
         * @return this builder
         */
        public Builder numIterations(Integer numIterations) {
            this.numIterations = numIterations;
            return this;
        }

        /**
         * @param strategyType the scheduling strategy name
         * @return this builder
         */
        public Builder strategyType(String strategyType) {
            this.strategyType = strategyType;
            return this;
        }

        /**
         * @param solver the symbolic solver selection
         * @return this builder
         */
        public Builder solver(String solver) {
            this.solver = solver;
            return this;
        }

        /**
         * @param strategyConstructor an explicit strategy factory (overrides the strategy name)
         * @return this builder
         */
        public Builder strategyConstructor(
                SchedulingStrategyConfiguration.SchedulingStrategyConstructor strategyConstructor) {
            this.strategyConstructor = strategyConstructor;
            return this;
        }

        /**
         * @param debug whether to enable debug logging / artifacts
         * @return this builder
         */
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * @param bugsPath the directory for reports and artifacts
         * @return this builder
         */
        public Builder reportPath(String bugsPath) {
            this.reportPath = bugsPath;
            return this;
        }

        /**
         * @param seed the RNG seed for randomized strategies
         * @return this builder
         */
        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * @param budget the estimator budget
         * @return this builder
         */
        public Builder budget(int budget) {
            this.budget = budget;
            return this;
        }

        /**
         * @param bugDepth the target bug depth for the PCT strategies
         * @return this builder
         */
        public Builder bugDepth(int bugDepth) {
            this.bugDepth = bugDepth;
            return this;
        }

        /**
         * @param pctFairBound the fair-suffix bound for {@code fair-pct} ({@code <= 0} = automatic)
         * @return this builder
         */
        public Builder pctFairBound(int pctFairBound) {
            this.pctFairBound = pctFairBound;
            return this;
        }

        /**
         * Sets the timeout from a {@link Duration}.
         *
         * @param timeout the wall-clock timeout, or {@code null} for none
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the timeout from a millisecond count.
         *
         * @param timeout the timeout in milliseconds; a negative value clears the timeout ({@code
         *     null})
         * @return this builder
         */
        public Builder timeout(long timeout) {
            if (timeout < 0L) {
                this.timeout = null;
                return this;
            }
            this.timeout = Duration.ofMillis(timeout);
            return this;
        }

        /**
         * @param schedulingPolicy the {@code trust}-family scheduling policy
         * @return this builder
         */
        public Builder schedulingPolicy(TrustStrategy.SchedulingPolicy schedulingPolicy) {
            this.schedulingPolicy = schedulingPolicy;
            return this;
        }

        /**
         * Validates the accumulated settings and builds an immutable {@link JmcCheckerConfiguration}.
         *
         * <p>Requires at least one stopping condition: it throws {@link
         * JmcInvalidConfigurationException} when {@code numIterations == 0} and no timeout is set.
         * Otherwise it logs the seed and copies every field into a new configuration.
         *
         * @return the built configuration
         * @throws JmcInvalidConfigurationException if neither {@code numIterations} nor a timeout is set
         */
        public JmcCheckerConfiguration build() throws JmcInvalidConfigurationException {
            if (numIterations == 0 && timeout == null) {
                throw new JmcInvalidConfigurationException(
                        "Either numIterations or timeout must be set");
            }
            LOGGER.info("Using seed: {}", seed);
            JmcCheckerConfiguration config = new JmcCheckerConfiguration();
            config.numIterations = numIterations;
            config.strategyType = strategyType;
            config.strategyConstructor = strategyConstructor;
            config.debug = debug;
            config.reportPath = reportPath;
            config.solver = solver;
            config.seed = seed;
            config.budget = budget;
            config.bugDepth = bugDepth;
            config.pctFairBound = pctFairBound;
            config.timeout = timeout;
            config.schedulingPolicy = schedulingPolicy;
            return config;
        }
    }
}
