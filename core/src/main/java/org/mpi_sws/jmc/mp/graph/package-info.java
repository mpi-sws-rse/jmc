/**
 * The Must execution graph {@code G = ⟨E, po, rf⟩} (Must paper Definition 3.2), with the extra
 * causal edges JMC's process model adds (spawn and join), and its canonical form.
 *
 * <p>Mirrors {@code exec_graph.rs} of TraceForge in what it records, not in how: this graph is a
 * value built once per run by a backend (the reference enumerator, later the Must strategy),
 * queried by the declarative consistency predicates in {@code org.mpi_sws.jmc.mp.consistency},
 * and reduced to a {@link org.mpi_sws.jmc.mp.graph.GraphCanon} so that distinct graphs can be
 * counted. It is not the incremental, revisit-carrying structure a DPOR needs.
 *
 * <p><b>Contract.</b> Pure: no runtime, API or strategy imports; no static mutable state; no I/O.
 */
package org.mpi_sws.jmc.mp.graph;
