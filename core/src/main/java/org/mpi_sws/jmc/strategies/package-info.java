/**
 * Defines the scheduling strategies used by JMC to explore a program's interleavings.
 *
 * <p>A {@code SchedulingStrategy} decides which runnable task to schedule next and how runtime events
 * are interpreted; {@code ReplayableSchedulingStrategy} adds trace record/replay. This package holds
 * the basics: the {@code SchedulingStrategy}/{@code ReplayableSchedulingStrategy} interfaces, the
 * default {@code RandomSchedulingStrategy}, the {@code SchedulingStrategyFactory} and
 * {@code SchedulingStrategyConfiguration} that build a strategy by name, the {@code ValueTracker}
 * helper, and the related exceptions ({@code JmcInvalidStrategyException},
 * {@code JmcReplayUnsupported}). The {@code tracker} sub-package computes the runnable-task set that
 * strategies pick from; the more advanced {@code pct}, {@code estimation}, and {@code trust}
 * sub-packages build on these basics.
 */
package org.mpi_sws.jmc.strategies;
