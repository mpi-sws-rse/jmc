/**
 * The Must strategy ({@code "must"}): the optimal dynamic partial-order reduction for
 * message-passing programs of Enea, Giannakopoulou, Kokologiannakis and Majumdar (OOPSLA 2024),
 * Algorithm 1, running over JMC's runtime and following the design of the Rust implementation in
 * TraceForge ({@code must.rs}).
 *
 * <p>Supported: the p2p and asyn communication models, blocking and non-blocking receives, and
 * nondeterministic choices. When summaries are exported, the program must also have the shape
 * "main spawns every process first, awaits them all, and performs no message-passing event".
 *
 * <p>{@link org.mpi_sws.jmc.strategies.must.MustStrategy} is the strategy; the package-private
 * {@code Graph} is its mutable working execution graph.
 */
package org.mpi_sws.jmc.strategies.must;
