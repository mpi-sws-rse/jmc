/**
 * The operational delivery rules of the Must message-passing semantics — the trusted core shared
 * by every exploration backend.
 *
 * <p>{@link org.mpi_sws.jmc.mp.semantics.Buffers} holds the pending messages of every model in
 * their canonical forms; {@link org.mpi_sws.jmc.mp.semantics.Delivery#deliverable} computes,
 * from a buffer, a destination and a guard, the <em>set</em> of messages a receive there may read.
 * These two give the enabled transitions of the explicit-state backend and the candidate set
 * that scheduling strategies running inside the JMC runtime choose from.
 *
 * <p><b>Design rule.</b> This package computes what is <em>possible</em> — a
 * set. It never chooses an element. A backend chooses. No method here returns "the one message to
 * deliver"; a method that did would violate the design, because the choice (and hence the
 * exploration order) must stay with the backend.
 *
 * <p><b>Purity contract.</b> Pure: no {@code runtime}, {@code api} or
 * {@code strategies} imports; no static mutable state; no I/O; every type an immutable value.
 * This lets the explicit-state engine and plain JUnit tests run these rules with no agent and no
 * runtime, and lets a strategy inside the JMC runtime call the very same functions.
 */
package org.mpi_sws.jmc.mp.semantics;
