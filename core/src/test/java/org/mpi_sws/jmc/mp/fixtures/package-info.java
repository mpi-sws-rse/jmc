/**
 * Fixtures of JMC's own unit tests for the message-passing API, the {@code must} and {@code mp-random} strategies,
 * the summary export, the online check, split runs and the launcher: small programs, each kept because a test
 * runs it.
 *
 * <ul>
 *   <li>{@code export}: programs of every export shape, accepted and refused, with their summaries;</li>
 *   <li>{@code counts}: programs whose numbers of executions under {@code must} are derived by hand;</li>
 *   <li>{@code threads}: processes beside ordinary JMC threads;</li>
 *   <li>{@code daemons}: daemon processes and declared roles;</li>
 *   <li>{@code addressing}: processes addressed before they start, and {@code assume};</li>
 *   <li>{@code apps}: two applications (a key-value store and a lock service) whose variants exercise
 *       export, the online check and split runs;</li>
 *   <li>{@code mutants}, {@code faults}, {@code roles}, {@code seq}: programs that break one rule each, runtime
 *       faults, role declarations, and event sequences.</li>
 * </ul>
 *
 * <p>These are not the benchmark programs. The programs the conformance check is evaluated on, each with its
 * specification and implementation variants, are in the daavar repository, {@code benchmarks/}
 * (<a href="https://github.com/joulook/daavar/tree/main/benchmarks">daavar/benchmarks</a>); {@code apps} keeps
 * only the versions of {@code a2-store} and {@code a3-lock} that these tests run.
 */
package org.mpi_sws.jmc.mp.fixtures;
