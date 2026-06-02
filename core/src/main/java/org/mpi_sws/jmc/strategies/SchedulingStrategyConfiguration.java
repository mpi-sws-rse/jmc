package org.mpi_sws.jmc.strategies;

import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

/**
 * Configuration class for scheduling strategies.
 *
 * <p>This class encapsulates the configuration parameters for scheduling strategies, including
 * seed, trust scheduling policy, report path, and debug mode.
 *
 * <p>It provides a builder pattern for constructing instances of the configuration, allowing for
 * flexible and readable configuration of scheduling strategies.
 */
public class SchedulingStrategyConfiguration {
    /** Seed for the strategy's random number generator (may be {@code null} for a random seed). */
    private Long seed;
    /** Scheduling policy used by the {@code trust} strategy family. */
    private TrustStrategy.SchedulingPolicy trustSchedulingPolicy;
    /** Directory where the strategy writes its reports/artifacts. */
    private String reportPath;
    /** Whether debug mode is enabled for the strategy. */
    private boolean debug;
    /** Exploration budget for the estimating strategies (e.g. {@code testor}). */
    private int budget;
    /** Solver selection for symbolic execution (e.g. {@code "off"}). */
    private String solver;
    /** Target bug depth {@code d} for the PCT strategies (always &ge; 1). */
    private int bugDepth;
    /**
     * Fair-suffix bound for the {@code fair-pct} strategy: the number of priority-controlled
     * scheduling decisions before switching to a uniform-random ("fair") suffix. A value
     * {@code <= 0} selects automatic mode (switch once a run exceeds the learned step bound).
     */
    private int pctFairBound;

    /** Private constructor; instances are created through {@link Builder}. */
    private SchedulingStrategyConfiguration() {
    }

    /**
     * Returns the configured RNG seed.
     *
     * @return the seed, or {@code null} if unset
     */
    public Long getSeed() {
        return seed;
    }

    /**
     * Returns the configured solver selection.
     *
     * @return the solver selection
     */
    public String getSolver() {
        return solver;
    }

    /**
     * Returns the report output directory.
     *
     * @return the report path
     */
    public String getReportPath() {
        return reportPath;
    }

    /**
     * Returns whether debug mode is enabled.
     *
     * @return {@code true} if debug mode is enabled
     */
    public boolean getDebug() {
        return debug;
    }

    /**
     * Returns the exploration budget.
     *
     * @return the budget
     */
    public int getBudget() {
        return budget;
    }

    /**
     * Returns the trust scheduling policy.
     *
     * @return the trust scheduling policy
     */
    public TrustStrategy.SchedulingPolicy getTrustSchedulingPolicy() {
        return trustSchedulingPolicy;
    }

    /**
     * Returns the target bug depth {@code d} used by the PCT strategies.
     *
     * @return the bug depth (always &ge; 1)
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
     * Builder for {@link SchedulingStrategyConfiguration}.
     *
     * <p>All values start with defaults (no seed, {@code RANDOM} trust policy, the default report
     * path, debug off, budget 2, solver {@code "off"}, bug depth 3, fair bound 0 = auto) and can be
     * overridden fluently.
     */
    public static class Builder {
        /** The RNG seed to build with. */
        private Long seed;
        /** The trust scheduling policy to build with. */
        private TrustStrategy.SchedulingPolicy trustSchedulingPolicy;
        /** The report path to build with. */
        private String reportPath;
        /** The debug flag to build with. */
        private boolean debug;
        /** The exploration budget to build with. */
        private int budget;
        /** The solver selection to build with. */
        private String solver;
        /** The PCT bug depth to build with. */
        private int bugDepth;
        /** The fair-pct fair-suffix bound to build with ({@code <= 0} = auto). */
        private int pctFairBound;

        /** Creates a builder pre-populated with the default configuration values. */
        public Builder() {
            this.seed = null;
            this.trustSchedulingPolicy = TrustStrategy.SchedulingPolicy.RANDOM;
            this.reportPath = "build/test-results/jmc-report";
            this.debug = false;
            this.budget = 2;
            this.solver = "off";
            this.bugDepth = 3;
            this.pctFairBound = 0;
        }

        /**
         * Sets the trust scheduling policy.
         *
         * @param trustSchedulingPolicy the trust scheduling policy
         * @return this builder, for chaining
         */
        public Builder trustSchedulingPolicy(TrustStrategy.SchedulingPolicy trustSchedulingPolicy) {
            this.trustSchedulingPolicy = trustSchedulingPolicy;
            return this;
        }

        /**
         * Sets the report output directory.
         *
         * @param reportPath the report path
         * @return this builder, for chaining
         */
        public Builder reportPath(String reportPath) {
            this.reportPath = reportPath;
            return this;
        }

        /**
         * Sets the solver selection.
         *
         * @param solver the solver selection
         * @return this builder, for chaining
         */
        public Builder solver(String solver) {
            this.solver = solver;
            return this;
        }

        /**
         * Enables debug mode.
         *
         * @return this builder, for chaining
         */
        public Builder debug() {
            this.debug = true;
            return this;
        }

        /**
         * Sets the RNG seed.
         *
         * @param seed the seed
         * @return this builder, for chaining
         */
        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets the exploration budget.
         *
         * @param budget the budget (must be at least 1)
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code budget} is less than 1
         */
        public Builder budget(int budget) {
            if (budget < 1) {
                throw new IllegalArgumentException("Budget must be at least 1");
            }
            this.budget = budget;
            return this;
        }

        /**
         * Sets the target bug depth {@code d} for the PCT strategies.
         *
         * @param bugDepth the bug depth (must be at least 1)
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code bugDepth} is less than 1
         */
        public Builder bugDepth(int bugDepth) {
            if (bugDepth < 1) {
                throw new IllegalArgumentException("bugDepth must be at least 1");
            }
            this.bugDepth = bugDepth;
            return this;
        }

        /**
         * Sets the fair-suffix bound for the {@code fair-pct} strategy.
         *
         * @param pctFairBound the number of priority-controlled decisions before switching to the
         *     fair random suffix; a value {@code <= 0} selects automatic mode
         * @return this builder, for chaining
         */
        public Builder pctFairBound(int pctFairBound) {
            this.pctFairBound = pctFairBound;
            return this;
        }

        /**
         * Builds an immutable {@link SchedulingStrategyConfiguration} from the configured values.
         *
         * @return a new {@link SchedulingStrategyConfiguration} instance
         */
        public SchedulingStrategyConfiguration build() {
            SchedulingStrategyConfiguration config = new SchedulingStrategyConfiguration();
            config.seed = this.seed;
            config.trustSchedulingPolicy = this.trustSchedulingPolicy;
            config.reportPath = this.reportPath;
            config.debug = this.debug;
            config.budget = this.budget;
            config.solver = this.solver;
            config.bugDepth = this.bugDepth;
            config.pctFairBound = this.pctFairBound;
            return config;
        }
    }

    /**
     * Functional interface for constructing a {@link SchedulingStrategy} from a configuration.
     */
    @FunctionalInterface
    public interface SchedulingStrategyConstructor {
        /**
         * Creates a scheduling strategy from the given configuration.
         *
         * @param config the configuration
         * @return the constructed scheduling strategy
         */
        SchedulingStrategy create(SchedulingStrategyConfiguration config);
    }
}
