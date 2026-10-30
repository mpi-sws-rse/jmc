package org.mpi_sws.jmc.strategies.must.split;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * The replay-state fingerprint of a split run: the hex SHA-256 of a versioned text describing what
 * a JVM that replays the root must reproduce exactly before it may explore items — the program and
 * configuration, the root's outcomes in order, the exploration stack at the boundary, and the
 * process-name table. Guards are not part of it (they cannot be compared; the program's
 * determinism contract covers them).
 *
 * <p>The text, in {@link TypedGraphEncoding}'s grammar, is {@code fp(version, program, K, R,
 * transcript(o…), stack(entry…), names(n…), nextId)}:
 *
 * <ul>
 *   <li>{@code o = outcome(complete|blocked, outcome digest)}, one per root iteration in order;</li>
 *   <li>the stack top first; an entry is {@code Forward(g, task, label, send, hb(…))}, {@code
 *       Nondet(g, task, optionCount, chosen, hb(…))} or {@code Backward(g)}, with {@code send} the
 *       event read or {@code bot()} for {@code ⊥};</li>
 *   <li>a graph is {@code g(node…, nextStamp)}, nodes in stamp order, each {@code node(key, label,
 *       stamp, rf or none(), bottom, hb(…), prefix(…))} with sorted event sets;</li>
 *   <li>{@code n = name(creation-path name, stable id)}, sorted by name.</li>
 * </ul>
 */
public final class StackFingerprint {

    /** The version of the text's layout; changes whenever the layout does. */
    public static final String VERSION = "j8-fp-2";

    private StackFingerprint() {}

    /**
     * Computes the fingerprint.
     *
     * @param program the program and configuration identity (class, arguments, model)
     * @param k the split threshold
     * @param view the strategy's state at the boundary
     * @param transcript the root's outcomes in order
     * @return the hex SHA-256 of the fingerprint text
     * @throws IllegalArgumentException if a label or value is refused by the typed encoding
     */
    public static String of(String program, int k, MustStrategy.SplitView view, List<ItemResult.Outcome> transcript) {
        return TypedGraphEncoding.sha256(text(program, k, view, transcript));
    }

    /**
     * The fingerprint text (before hashing), for diagnostics and tests.
     *
     * @param program the program and configuration identity
     * @param k the split threshold
     * @param view the strategy's state at the boundary
     * @param transcript the root's outcomes in order
     * @return the text
     */
    public static String text(String program, int k, MustStrategy.SplitView view,
                              List<ItemResult.Outcome> transcript) {
        Map<Long, String> byId = new java.util.TreeMap<>();
        for (Map.Entry<String, Long> e : view.names().entrySet()) {
            byId.put(e.getValue(), e.getKey());
        }
        LongFunction<String> names = byId::get;
        List<String> outcomes = new ArrayList<>();
        for (ItemResult.Outcome o : transcript) {
            outcomes.add(TypedGraphEncoding.composite("outcome", o.blocked() ? "blocked" : "complete", o.digest()));
        }
        List<String> entries = new ArrayList<>();
        for (MustStrategy.EntryView e : view.stackTopFirst()) {
            entries.add(entry(e, names));
        }
        List<String> table = new ArrayList<>();
        for (Map.Entry<String, Long> e : view.names().entrySet()) {
            table.add(TypedGraphEncoding.composite("name", e.getKey(), Long.toString(e.getValue())));
        }
        return TypedGraphEncoding.composite("fp", VERSION, program, Integer.toString(k),
                Integer.toString(view.finishedIterations()), TypedGraphEncoding.composite("transcript", outcomes),
                TypedGraphEncoding.composite("stack", entries), TypedGraphEncoding.composite("names", table),
                Long.toString(view.nextId()));
    }

    private static String entry(MustStrategy.EntryView e, LongFunction<String> names) {
        String g = graph(e.g(), names);
        switch (e.variant()) {
            case "Forward":
                return TypedGraphEncoding.composite("Forward", g, Long.toString(e.task()),
                        TypedGraphEncoding.label(e.label(), names),
                        e.send() == null ? TypedGraphEncoding.composite("bot")
                                : TypedGraphEncoding.key(e.send(), names),
                        keys("hb", e.hbIn(), names));
            case "Nondet":
                return TypedGraphEncoding.composite("Nondet", g, Long.toString(e.task()),
                        Integer.toString(e.optionCount()), Integer.toString(e.chosen()), keys("hb", e.hbIn(), names));
            case "Backward":
                return TypedGraphEncoding.composite("Backward", g);
            default:
                throw new IllegalStateException("unknown stack entry variant " + e.variant());
        }
    }

    private static String graph(MustStrategy.GraphView g, LongFunction<String> names) {
        List<String> fields = new ArrayList<>();
        for (MustStrategy.NodeView n : g.nodesByStamp()) {
            fields.add(TypedGraphEncoding.composite("node", TypedGraphEncoding.key(n.key(), names),
                    TypedGraphEncoding.label(n.label(), names), Integer.toString(n.stamp()),
                    n.rf() == null ? TypedGraphEncoding.composite("none") : TypedGraphEncoding.key(n.rf(), names),
                    Boolean.toString(n.bottom()), keys("hb", n.hbIn(), names), keys("prefix", n.prefix(), names)));
        }
        fields.add(Integer.toString(g.nextStamp()));
        return TypedGraphEncoding.composite("g", fields);
    }

    private static String keys(String tag, Iterable<EventKey> keys, LongFunction<String> names) {
        List<String> fields = new ArrayList<>();
        for (EventKey k : keys) {
            fields.add(TypedGraphEncoding.key(k, names));
        }
        return TypedGraphEncoding.composite(tag, fields);
    }
}
