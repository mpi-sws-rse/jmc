package org.mpi_sws.jmc.mp.graph;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * The canonical form of an execution graph — the value two runs must agree on to have produced
 * "the same graph": Must's {@code G₁ ≈ G₂} (Section 4 of the paper, before Theorem 4.1: equal on
 * all components up to insertion order), restricted to the graphs of <em>one</em> program. Must's
 * receive label carries {@code vals}; this form drops it, which is exact because a program is a
 * deterministic function of its trace, so two runs agreeing on labels and {@code rf} executed
 * the same receives with the same predicates. Counting distinct values of this type counts the
 * distinct execution graphs of a program.
 *
 * <p>Identity is: per thread, the sequence of labels (a {@link MpLabel.Nondet} records the value
 * taken, so a different choice is a different graph); the reads-from map; the set of receives
 * reading {@code ⊥}; and the spawn/join edges. It excludes the insertion order and every receive
 * predicate ({@link GraphEvent}).
 *
 * @param labels per thread id, the labels in program order
 * @param rf receive → send
 * @param bottoms receives reading {@code ⊥}
 * @param hb spawn/join edges
 */
public record GraphCanon(
        SortedMap<Long, List<MpLabel>> labels,
        SortedMap<EventKey, EventKey> rf,
        SortedSet<EventKey> bottoms,
        SortedSet<ExecutionGraph.Edge> hb) {}
