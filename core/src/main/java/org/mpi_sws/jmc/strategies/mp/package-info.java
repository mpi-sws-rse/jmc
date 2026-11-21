/**
 * Strategies that answer the events of the message-passing API
 * ({@code org.mpi_sws.jmc.api.mp}): they own the pending-message buffers and the per-task clocks,
 * and resolve every choice the API asks about.
 *
 * <p>This package holds {@link org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy}
 * ({@code "mp-random"}), which samples one of the allowed choices, and
 * {@link org.mpi_sws.jmc.strategies.mp.MpRecordingStrategy}. The exhaustive Must strategy lives
 * in {@code org.mpi_sws.jmc.strategies.must}. These strategies use the pure semantics in
 * {@code org.mpi_sws.jmc.mp.semantics}, which computes the set of allowed choices; the strategy
 * only picks an element of that set.
 */
package org.mpi_sws.jmc.strategies.mp;
