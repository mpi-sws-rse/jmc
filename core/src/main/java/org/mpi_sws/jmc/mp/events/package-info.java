/**
 * The event vocabulary of the Must message-passing semantics: process identifiers, message
 * markers, communication models, the send payload ({@link org.mpi_sws.jmc.mp.events.Envelope}),
 * the per-task event identity ({@link org.mpi_sws.jmc.mp.events.EventKey}), and the event labels
 * of the Must execution graph ({@link org.mpi_sws.jmc.mp.events.MpLabel}).
 *
 * <p>Mirrors {@code event_label.rs} of the TraceForge reference implementation and Definition 3.1
 * (Event) of the Must paper (Enea, Giannakopoulou, Kokologiannakis, Majumdar, OOPSLA 2024).
 *
 * <p><b>Purity contract.</b> This package is part of the pure semantic
 * core. It must not import {@code org.mpi_sws.jmc.runtime}, {@code org.mpi_sws.jmc.api} or
 * {@code org.mpi_sws.jmc.strategies}; it holds no static mutable state; it performs no I/O. Every
 * type here is an immutable value with {@code equals}/{@code hashCode} by value, because the
 * explicit-state backend stores them inside states and compares states for equality.
 *
 * <p>Nothing in this package decides anything. It describes events; it never chooses among
 * them.
 */
package org.mpi_sws.jmc.mp.events;
