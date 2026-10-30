package org.mpi_sws.jmc.strategies.must.split;

import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphCanon;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * A typed byte encoding of values, labels and graph outcomes that does not depend on the JVM (no
 * {@code hashCode}, {@code toString} or unordered traversal), used for outcome digests and for the
 * replay-state fingerprint of a split run.
 *
 * <p><b>Grammar.</b> All text is UTF-8. {@code frame(b) = <decimal byte length of b> ":" b}. A
 * composite with tag {@code t} and fields {@code f1 … fn} is {@code frame(t) frame(n) frame(f1) …
 * frame(fn)}. Values:
 *
 * <ul>
 *   <li>{@code I} a Java {@code Integer} (decimal), {@code J} a {@code Long} (decimal), {@code Z} a
 *       {@code Boolean} ({@code true}/{@code false}), {@code S} a {@code String}, which must be
 *       well-formed UTF-16 (an unpaired surrogate is refused);</li>
 *   <li>{@code E} an enum constant: its declaring class name, its constant name;</li>
 *   <li>{@code R} a record: its class name, then its components in declaration order, each
 *       encoded recursively;</li>
 *   <li>{@code L} a {@link List} of any implementation class: its size, then its elements;</li>
 *   <li>{@code T} a {@link Tid}: the task's creation-path name.</li>
 * </ul>
 *
 * <p>Anything else ({@code null}, other numeric wrappers, arrays, sets, optionals, non-record
 * classes) is refused with an {@link IllegalArgumentException}. Labels: {@code send(model, dst
 * name, value)}, {@code recv(model, blocking)}, {@code nondet(optionCount, chosen)}, {@code
 * block()}; an {@code Error} label is refused. An event is {@code K(name, index)}.
 *
 * <p>For example, the label {@code send(P2P, m/0, Deposit(m/1, 100))} of a record {@code
 * x.Deposit(Tid from, int amount)} encodes as {@code 4:send1:33:P2P3:m/0} followed by the frame
 * of {@code 1:R1:39:x.Deposit11:1:T1:13:m/111:1:I1:13:100}.
 *
 * <p>The outcome encoding ({@link #outcome}) encodes exactly what {@link GraphCanon} compares —
 * per thread the labels in program order, the reads-from pairs, the receives reading {@code ⊥}, the
 * spawn/join edges — with threads named by creation path and sorted by that name, and every other
 * collection in the order of its encoded bytes, so two graphs of one program have equal encodings
 * exactly when their canonical forms are equal.
 */
public final class TypedGraphEncoding {

    private TypedGraphEncoding() {}

    /**
     * Frames one field.
     *
     * @param b the field's text
     * @return {@code <byte length>:b}
     */
    static String frame(String b) {
        return b.getBytes(StandardCharsets.UTF_8).length + ":" + b;
    }

    /**
     * A composite.
     *
     * @param tag the tag
     * @param fields the already encoded fields
     * @return the composite's encoding
     */
    static String composite(String tag, List<String> fields) {
        StringBuilder sb = new StringBuilder(frame(tag)).append(frame(Integer.toString(fields.size())));
        for (String f : fields) {
            sb.append(frame(f));
        }
        return sb.toString();
    }

    /** {@link #composite(String, List)} with the fields given inline. */
    static String composite(String tag, String... fields) {
        return composite(tag, List.of(fields));
    }

    /**
     * Encodes a payload value.
     *
     * @param v the value
     * @param names stable task id to creation-path name, for {@code Tid}s
     * @return its encoding
     * @throws IllegalArgumentException if the value, or a value inside it, is of a refused class
     */
    public static String value(Object v, LongFunction<String> names) {
        if (v == null) {
            throw new IllegalArgumentException("split mode refuses a null payload value");
        }
        if (v instanceof Integer i) {
            return composite("I", Integer.toString(i));
        }
        if (v instanceof Long l) {
            return composite("J", Long.toString(l));
        }
        if (v instanceof Boolean b) {
            return composite("Z", Boolean.toString(b));
        }
        if (v instanceof String s) {
            requireWellFormed(s);
            return composite("S", s);
        }
        if (v instanceof Enum<?> e) {
            return composite("E", e.getDeclaringClass().getName(), e.name());
        }
        if (v instanceof Tid t) {
            return composite("T", name(t.raw(), names));
        }
        if (v instanceof Record r) {
            RecordComponent[] cs = r.getClass().getRecordComponents();
            List<String> fields = new ArrayList<>(cs.length + 1);
            fields.add(r.getClass().getName());
            for (RecordComponent c : cs) {
                Object x;
                try {
                    c.getAccessor().setAccessible(true);
                    x = c.getAccessor().invoke(r);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalArgumentException("cannot read component " + c.getName() + " of "
                            + r.getClass().getName() + ": " + e, e);
                }
                fields.add(value(x, names));
            }
            return composite("R", fields);
        }
        if (v instanceof List<?> l) {
            List<String> fields = new ArrayList<>(l.size() + 1);
            fields.add(Integer.toString(l.size()));
            for (Object x : l) {
                fields.add(value(x, names));
            }
            return composite("L", fields);
        }
        throw new IllegalArgumentException("split mode refuses a payload value of " + v.getClass().getName()
                + " (supported: Integer, Long, Boolean, String, enum, record, List, Tid)");
    }

    /**
     * Encodes a label.
     *
     * @param label the label
     * @param names stable task id to creation-path name
     * @return its encoding
     * @throws IllegalArgumentException for an {@code Error} label or a refused payload value
     */
    public static String label(MpLabel label, LongFunction<String> names) {
        if (label instanceof MpLabel.Send s) {
            return composite("send", s.model().name(), name(s.dst().raw(), names), value(s.value(), names));
        }
        if (label instanceof MpLabel.Recv r) {
            return composite("recv", r.model().name(), Boolean.toString(r.blocking()));
        }
        if (label instanceof MpLabel.Nondet n) {
            return composite("nondet", Integer.toString(n.optionCount()), Integer.toString(n.chosen()));
        }
        if (label instanceof MpLabel.Block) {
            return composite("block");
        }
        throw new IllegalArgumentException("split mode refuses the label " + label);
    }

    /**
     * Encodes an event as {@code K(name, index)}.
     *
     * @param key the event
     * @param names stable task id to creation-path name
     * @return its encoding
     */
    public static String key(EventKey key, LongFunction<String> names) {
        return composite("K", name(key.tid(), names), Long.toString(key.idx()));
    }

    /**
     * Encodes a graph outcome: exactly the components of {@link GraphCanon}.
     *
     * @param canon the canonical form
     * @param names stable task id to creation-path name
     * @return the encoding {@code graph(threads(…), rf(…), bot(…), hb(…))}
     * @throws IllegalArgumentException if a label or value is refused
     */
    public static String outcome(GraphCanon canon, LongFunction<String> names) {
        // Threads in the order of their creation-path names (as UTF-8 bytes), not of their ids.
        java.util.TreeMap<String, String> threadByName = new java.util.TreeMap<>(TypedGraphEncoding::compareBytes);
        for (Map.Entry<Long, List<MpLabel>> e : canon.labels().entrySet()) {
            String threadName = name(e.getKey(), names);
            List<String> fields = new ArrayList<>();
            fields.add(threadName);
            for (MpLabel l : e.getValue()) {
                fields.add(label(l, names));
            }
            threadByName.put(threadName, composite("thread", fields));
        }
        List<String> threads = new ArrayList<>(threadByName.values());
        List<String> rf = new ArrayList<>();
        for (Map.Entry<EventKey, EventKey> e : canon.rf().entrySet()) {
            rf.add(composite("pair", key(e.getKey(), names), key(e.getValue(), names)));
        }
        List<String> bot = new ArrayList<>();
        for (EventKey k : canon.bottoms()) {
            bot.add(key(k, names));
        }
        List<String> hb = new ArrayList<>();
        for (ExecutionGraph.Edge e : canon.hb()) {
            hb.add(composite("edge", key(e.from(), names), key(e.to(), names)));
        }
        return composite("graph", composite("threads", threads), composite("rf", sorted(rf)),
                composite("bot", sorted(bot)), composite("hb", sorted(hb)));
    }

    /**
     * The hex SHA-256 of a text's UTF-8 bytes.
     *
     * @param text the text
     * @return 64 lower-case hex digits
     */
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** The encodings in the order of their bytes (independent of task ids). */
    private static List<String> sorted(List<String> encodings) {
        List<String> out = new ArrayList<>(encodings);
        out.sort(TypedGraphEncoding::compareBytes);
        return Collections.unmodifiableList(out);
    }

    /** Compares two texts by their UTF-8 bytes. */
    private static int compareBytes(String a, String b) {
        return java.util.Arrays.compare(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Refuses a string that is not well-formed UTF-16: its UTF-8 bytes would replace an unpaired
     * surrogate and so merge unequal strings.
     *
     * @param s the string
     * @throws IllegalArgumentException if {@code s} has an unpaired surrogate
     */
    static void requireWellFormed(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                i++;
            } else if (Character.isSurrogate(c)) {
                throw new IllegalArgumentException("split mode refuses a string that is not well-formed UTF-16"
                        + " (unpaired surrogate at index " + i + ")");
            }
        }
    }

    /** A task's creation-path name; an id without a name is refused. */
    private static String name(long id, LongFunction<String> names) {
        String n = names.apply(id);
        if (n == null) {
            throw new IllegalArgumentException("task id " + id + " has no creation-path name");
        }
        return n;
    }
}
