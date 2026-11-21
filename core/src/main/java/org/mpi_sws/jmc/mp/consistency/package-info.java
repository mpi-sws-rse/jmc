/**
 * Causality tracking for the Must message-passing semantics: vector clocks and the per-task
 * clock discipline that makes {@code VC(s') < VC(s)} coincide with {@code s' porf s}, and the
 * declarative per-model consistency predicates over execution graphs (Must paper,
 * Definitions 3.4-3.8; see {@link org.mpi_sws.jmc.mp.consistency.MpConsistency}).
 *
 * <p>Mirrors {@code vector_clock.rs} and {@code cons.rs} of the TraceForge reference
 * implementation.
 *
 * <p><b>Purity contract.</b> Pure: no runtime, API or strategy imports;
 * no static mutable state; no I/O; every type an immutable value.
 */
package org.mpi_sws.jmc.mp.consistency;
