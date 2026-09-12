/**
 * Defines annotations for specific strategies used in the JMC library.
 *
 * <p>These complement the core {@code JmcCheckConfiguration#strategy()} string with strategy-specific
 * configuration. {@code JmcTrustStrategy} selects and tunes the {@code trust} strategy, and {@code
 * JmcMeasureGraphCoverage} wraps a strategy to measure execution-graph coverage; both are consumed by
 * {@code JmcDescriptorUtil} when building the checker configuration. {@code JmcEstimationStrategy} is
 * defined but not currently consumed.
 */
package org.mpi_sws.jmc.annotations.strategies;
