/**
 * JMC's stateless model-checking strategy: a Java implementation of the TruSt dynamic partial-order
 * reduction (DPOR) algorithm.
 *
 * <p>The package models a program run as an {@link org.mpi_sws.jmc.strategies.trust.ExecutionGraph}
 * of {@link org.mpi_sws.jmc.strategies.trust.Event}s and explores every consistent graph by pushing
 * forward and backward revisits onto an {@link
 * org.mpi_sws.jmc.strategies.trust.ExplorationStack} and re-executing the program once per graph.
 * {@link org.mpi_sws.jmc.strategies.trust.Algo} is the algorithm driver and {@link
 * org.mpi_sws.jmc.strategies.trust.TrustStrategy} adapts it to the scheduler's {@code
 * SchedulingStrategy} contract.
 *
 * <p>Based on the paper "Truly Stateless, Optimal Dynamic Partial Order Reduction" by Michalis
 * Kokologiannakis, Iason Marmanis, Vladimir Gladstein, and Viktor Vafeiadis.
 */
package org.mpi_sws.jmc.strategies.trust;
