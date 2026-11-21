package org.mpi_sws.jmc.mp.consistency;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The declarative consistency predicates of Must over a finished execution graph, as defined in
 * the Must paper (Enea et al., OOPSLA 2024): Definitions 3.3 (well-formedness), 3.4
 * ({@code asyn}), 3.5 ({@code p2p}), 3.6 ({@code cd}), 3.7 ({@code mbox}) and 3.8
 * (a graph is consistent iff it is well-formed and consistent under every model it uses).
 *
 * <p>This is the <em>specification</em> side of the correspondence between declarative and
 * operational semantics: the operational delivery rules in
 * {@code org.mpi_sws.jmc.mp.semantics} must produce exactly the graphs this class accepts. A test
 * enumerator can therefore run every graph it produces through {@link #check} as a self-check,
 * and a Must exploration uses it as {@code consistent(G)} in Algorithm 1, line 16 of the paper.
 *
 * <p><b>How clauses (b) and (c) are checked.</b> Definitions 3.5–3.7 all have the shape
 * "(b) {@code [US]; so|dst; rf ∩ mval} is empty and (c) {@code po; rf⁻¹; (so|dst; rf ∩ mval)} is
 * irreflexive", differing only in {@code so}. Both clauses say the same thing about a receive
 * {@code r} reading {@code s}: <em>every matching send {@code s'} to the same destination that
 * was still available at {@code r} — unread, or read by a {@code po}-later receive — must not be
 * {@code so}-before {@code s}</em>; (b) is the unread case, (c) the read-later case. So the
 * check computes those <b>forced pairs</b> {@code (s, s')} once ({@link #forcedPairs}) and then:
 * <ul>
 *   <li>for {@code p2p} and {@code cd}, where {@code so} is given ({@code po} between same-sender
 *       sends; {@code porf} between sends), rejects any forced pair with {@code s' so s};</li>
 *   <li>for {@code mbox}, where Definition 3.7 asks for <em>some</em> total {@code so} — read, as
 *       the TraceForge reference implementation does, as one containing {@code porf}
 *       ({@code cons.rs}:
 *       {@code send_before = (porf ∪ induced_send_before)⁺}, "porf ≤ send_before") — such an
 *       {@code so} exists iff the relation {@code porf|mbox ∪ forced} is acyclic: an acyclic
 *       relation extends to a total order satisfying (b) and (c), and any such total order
 *       contains every forced pair, so a cycle in the relation is a cycle in {@code so}.
 *       The check is therefore acyclicity ({@link #checkMbox}). Note that a single global
 *       {@code so} couples destinations through {@code porf}; this is why per-destination
 *       reasoning is not enough here.</li>
 * </ul>
 *
 * <p>All receives to one destination are that thread's receives, hence {@code po}-ordered; "read
 * later than {@code r}" is therefore {@code po}-later, as {@code cons.rs} also assumes ("no
 * concurrent receives").
 *
 * <p>Pure and stateless. Returns the first violation found, naming the clause, so a failing
 * check says <em>which</em> definition the operational rule broke.
 */
public final class MpConsistency {

    private MpConsistency() {}

    /**
     * A violated clause of the consistency definitions.
     *
     * @param clause the definition and clause, e.g. {@code "Def. 3.5 (b)"}
     * @param detail the events involved
     */
    public record Violation(String clause, String detail) {
        @Override
        public String toString() {
            return clause + ": " + detail;
        }
    }

    /**
     * A forced ordering between two sends: because a receive read {@code s} while {@code s'} was
     * available and matching, no admissible {@code so} has {@code s'} before {@code s}. For a
     * total {@code so} (mbox) that is "{@code s} before {@code s'}"; for the partial given orders
     * of p2p and cd it is only {@code ¬(s' so s)} — the two sends may be {@code so}-unordered,
     * which is how the second graph of Example 3.1 in the Must paper is p2p-consistent.
     *
     * @param s the send read
     * @param other the send {@code s'} that was available but not read
     * @param recv the receive that read {@code s}
     * @param otherUnread whether {@code s'} is unread (clause (b)) or read by a later receive
     *     (clause (c))
     */
    public record Forced(EventKey s, EventKey other, EventKey recv, boolean otherUnread) {}

    /**
     * Definition 3.8: well-formed and consistent under every model the graph uses.
     *
     * @param g the graph
     * @return the first violation, or empty if consistent
     */
    public static Optional<Violation> check(ExecutionGraph g) {
        Optional<Violation> v = wellFormedness(g);
        if (v.isPresent()) {
            return v;
        }
        for (CommunicationModel m : modelsUsed(g)) {
            v = checkModel(g, m);
            if (v.isPresent()) {
                return v;
            }
        }
        return Optional.empty();
    }

    /**
     * Checks well-formedness (Definition 3.3): every receive reads a send or {@code ⊥}, only
     * non-blocking receives read {@code ⊥}, each send is read at most once, a read send has the
     * receive's thread as destination, the same model and a value the receive accepts, and
     * {@code porf} has no cycles.
     *
     * @param g the graph
     * @return the first violated clause, or empty
     */
    public static Optional<Violation> wellFormedness(ExecutionGraph g) {
        Map<EventKey, EventKey> readers = new HashMap<>();
        for (GraphEvent r : g.receives()) {
            MpLabel.Recv label = (MpLabel.Recv) r.label();
            Optional<EventKey> s = g.rf(r.key());
            boolean bottom = g.readsBottom(r.key());
            if (s.isEmpty() && !bottom) {
                return Optional.of(new Violation(
                        "Def. 3.2(3) (rf total)", r.key() + " reads neither a send nor ⊥"));
            }
            if (bottom && label.blocking()) {
                return Optional.of(new Violation(
                        "Def. 3.3 (only non-blocking receives read ⊥)", r.key() + " is blocking"));
            }
            if (s.isPresent()) {
                EventKey sk = s.get();
                EventKey prior = readers.putIfAbsent(sk, r.key());
                if (prior != null) {
                    return Optional.of(new Violation(
                            "Def. 3.3 (sends are read at most once)",
                            sk + " read by both " + prior + " and " + r.key()));
                }
                MpLabel.Send send = (MpLabel.Send) g.event(sk).label();
                if (send.dst().raw() != r.key().tid()) {
                    return Optional.of(new Violation(
                            "Def. 3.3 (rf destination)",
                            r.key() + " reads " + sk + " sent to " + send.dst()));
                }
                if (send.model() != label.model()) {
                    return Optional.of(new Violation(
                            "Def. 3.3 (rf model)",
                            r.key() + " (" + label.model() + ") reads " + sk + " (" + send.model() + ")"));
                }
                if (!r.accepts(send.value())) {
                    return Optional.of(new Violation(
                            "Def. 3.3 (rf value ∈ vals)",
                            r.key() + " reads " + sk + " whose value it does not accept"));
                }
            }
        }
        if (!g.porfAcyclic()) {
            return Optional.of(new Violation("Def. 3.3 (no causal cycles)", "porf is not irreflexive"));
        }
        return Optional.empty();
    }

    /**
     * Consistency under one model, over that model's events (Definitions 3.4–3.7).
     *
     * @param g the graph (assumed well-formed)
     * @param model the model
     * @return the first violation, or empty
     */
    public static Optional<Violation> checkModel(ExecutionGraph g, CommunicationModel model) {
        return switch (model) {
            case ASYN -> Optional.empty(); // Def. 3.4: well-formedness only
            case P2P -> checkGivenOrder(g, model, "Def. 3.5", MpConsistency::soP2p);
            case CD -> checkGivenOrder(g, model, "Def. 3.6", MpConsistency::soCd);
            case MBOX -> checkMbox(g);
        };
    }

    /**
     * The forced pairs of a model: for each receive {@code r} of that model reading {@code s},
     * every send {@code s'} of the model to {@code r}'s thread that {@code r} accepts and that was
     * available at {@code r} (unread, or read by a {@code po}-later receive).
     *
     * @param g the graph
     * @param model the model
     * @return the forced pairs, in insertion order of {@code r} then of {@code s'}
     */
    public static List<Forced> forcedPairs(ExecutionGraph g, CommunicationModel model) {
        List<Forced> out = new ArrayList<>();
        List<GraphEvent> sends = sendsOf(g, model);
        for (GraphEvent r : g.receives()) {
            if (((MpLabel.Recv) r.label()).model() != model) {
                continue;
            }
            Optional<EventKey> read = g.rf(r.key());
            if (read.isEmpty()) {
                continue; // a ⊥ read constrains nothing
            }
            for (GraphEvent sp : sends) {
                MpLabel.Send sl = (MpLabel.Send) sp.label();
                if (sp.key().equals(read.get()) || sl.dst().raw() != r.key().tid()) {
                    continue;
                }
                if (!r.accepts(sl.value())) {
                    continue; // ∩ mval
                }
                Optional<EventKey> reader = g.readerOf(sp.key());
                boolean unread = reader.isEmpty();
                boolean readLater = reader.isPresent() && g.poBefore(r.key(), reader.get());
                if (unread || readLater) {
                    out.add(new Forced(read.get(), sp.key(), r.key(), unread));
                }
            }
        }
        return out;
    }

    // ---- p2p / cd: so is given ----

    /** A given send-order relation {@code so} between sends of one model. */
    private interface So {
        boolean before(ExecutionGraph g, EventKey s1, EventKey s2);
    }

    /** {@code so ≜ [S^p2p]; po; [S^p2p]}, restricted to the same destination. */
    private static boolean soP2p(ExecutionGraph g, EventKey s1, EventKey s2) {
        return g.poBefore(s1, s2) && sameDst(g, s1, s2);
    }

    /** {@code so ≜ [S^cd]; porf; [S^cd]}, restricted to the same destination. */
    private static boolean soCd(ExecutionGraph g, EventKey s1, EventKey s2) {
        return g.porfBefore(s1, s2) && sameDst(g, s1, s2);
    }

    /**
     * Checks clauses (b) and (c) of a model whose {@code so} is given: no forced pair
     * {@code (s, s')} may have {@code s' so s}.
     *
     * @param g the graph
     * @param model the model
     * @param def the definition name used in the violation, e.g. {@code "Def. 3.5"}
     * @param so the model's send order
     * @return the first violation, or empty
     */
    private static Optional<Violation> checkGivenOrder(
            ExecutionGraph g, CommunicationModel model, String def, So so) {
        for (Forced f : forcedPairs(g, model)) {
            if (so.before(g, f.other(), f.s())) {
                String clause = f.otherUnread() ? " (b)" : " (c)";
                return Optional.of(new Violation(
                        def + clause,
                        f.recv() + " reads " + f.s() + " while " + f.other() + " (so-earlier, matching, "
                                + (f.otherUnread() ? "unread" : "read by a po-later receive") + ")"));
            }
        }
        return Optional.empty();
    }

    // ---- mbox: so is chosen; check acyclicity of porf|mbox ∪ forced ----

    /**
     * Checks the mailbox model: builds the relation {@code porf|mbox ∪ forced} over the mailbox
     * sends and reports a violation iff it has a cycle (see the class comment).
     *
     * @param g the graph
     * @return the first violation, or empty
     */
    private static Optional<Violation> checkMbox(ExecutionGraph g) {
        List<GraphEvent> sends = sendsOf(g, CommunicationModel.MBOX);
        Map<EventKey, List<EventKey>> succ = new HashMap<>();
        for (GraphEvent a : sends) {
            for (GraphEvent b : sends) {
                if (!a.equals(b) && g.porfBefore(a.key(), b.key())) {
                    succ.computeIfAbsent(a.key(), k -> new ArrayList<>()).add(b.key());
                }
            }
        }
        for (Forced f : forcedPairs(g, CommunicationModel.MBOX)) {
            succ.computeIfAbsent(f.s(), k -> new ArrayList<>()).add(f.other());
        }
        // Cycle detection by DFS; done and onPath are shared across start nodes.
        Set<EventKey> done = new HashSet<>();
        Set<EventKey> onPath = new HashSet<>();
        for (GraphEvent start : sends) {
            Optional<List<EventKey>> cycle = findCycle(start.key(), succ, done, onPath);
            if (cycle.isPresent()) {
                return Optional.of(new Violation(
                        "Def. 3.7 (no total so ⊇ porf satisfies (b) and (c))",
                        "forced send order has a cycle: " + cycle.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * Depth-first search for a cycle reachable from {@code start}, using an explicit stack
     * (to avoid recursion depth limits) of nodes and next-successor positions.
     *
     * @param start the node to search from
     * @param succ the successor lists of the relation
     * @param done nodes fully explored by earlier searches (shared across calls, updated here)
     * @param onPath nodes on the current DFS path (shared across calls, empty between calls)
     * @return a cycle as a list that starts and ends with the same event, or empty if none
     */
    private static Optional<List<EventKey>> findCycle(
            EventKey start, Map<EventKey, List<EventKey>> succ, Set<EventKey> done, Set<EventKey> onPath) {
        if (done.contains(start)) {
            return Optional.empty();
        }
        Deque<EventKey> path = new ArrayDeque<>();
        Deque<Integer> pos = new ArrayDeque<>();
        path.push(start);
        pos.push(0);
        onPath.add(start);
        while (!path.isEmpty()) {
            EventKey node = path.peek();
            int i = pos.pop();
            List<EventKey> next = succ.getOrDefault(node, List.of());
            if (i < next.size()) {
                pos.push(i + 1);
                EventKey n = next.get(i);
                if (onPath.contains(n)) {
                    // path is the whole DFS stack; the cycle is the suffix from n's occurrence
                    List<EventKey> stack = new ArrayList<>(path);
                    Collections.reverse(stack);
                    List<EventKey> cycle = new ArrayList<>(stack.subList(stack.indexOf(n), stack.size()));
                    cycle.add(n);
                    return Optional.of(cycle);
                }
                if (!done.contains(n)) {
                    path.push(n);
                    pos.push(0);
                    onPath.add(n);
                }
            } else {
                path.pop();
                onPath.remove(node);
                done.add(node);
            }
        }
        return Optional.empty();
    }

    // ---- helpers ----

    /** Whether the two sends have the same destination process. */
    private static boolean sameDst(ExecutionGraph g, EventKey s1, EventKey s2) {
        return ((MpLabel.Send) g.event(s1).label()).dst()
                .equals(((MpLabel.Send) g.event(s2).label()).dst());
    }

    /** The sends of the graph that use the given model, in the graph's send order. */
    private static List<GraphEvent> sendsOf(ExecutionGraph g, CommunicationModel model) {
        List<GraphEvent> out = new ArrayList<>();
        for (GraphEvent e : g.sends()) {
            if (((MpLabel.Send) e.label()).model() == model) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * The models the graph uses, in enum order.
     *
     * @param g the graph
     * @return the set of models of its sends and receives
     */
    public static Set<CommunicationModel> modelsUsed(ExecutionGraph g) {
        EnumSet<CommunicationModel> out = EnumSet.noneOf(CommunicationModel.class);
        for (GraphEvent e : g.eventsInInsertionOrder()) {
            if (e.label() instanceof MpLabel.Send s) {
                out.add(s.model());
            } else if (e.label() instanceof MpLabel.Recv r) {
                out.add(r.model());
            }
        }
        return out;
    }
}
