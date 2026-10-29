package org.mpi_sws.jmc.mp.summary;

import org.mpi_sws.jmc.mp.events.Tid;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The canonical JSON encoding of values in a summary file ("Values" in the summary format
 * specification), compared by bytes against the encoding produced by the Rust (TraceForge) side:
 *
 * <ul>
 *   <li>{@link Byte}, {@link Short}, {@link Integer}, {@link Long} as JSON integers;
 *       {@link Boolean} as {@code true}/{@code false}; {@link String} as a JSON string; an
 *       {@link Enum} constant as the JSON string of its declared name;</li>
 *   <li>a {@link Tid} in a payload as the JSON string of its role name, looked up through the
 *       function given at construction (the strategy knows which task carries which role);</li>
 *   <li>a Java {@code record} as {@code {"t":"<tag>","f":[v1,v2,...]}}, the tag from its
 *       {@link Tag} annotation and the fields in declaration order; a record without a
 *       {@link Tag} is refused;</li>
 *   <li>a {@link List} as a JSON array in order; a {@link Set} as a JSON array sorted by the
 *       UTF-8 bytes of the encoded elements — two elements that encode identically (such as the
 *       {@code Integer} 1 and the {@code Long} 1) are refused, since the encoding could not say
 *       they were distinct;</li>
 *   <li>refused ({@link IllegalArgumentException}): {@code null} (⊥ is the caller's business),
 *       floating point, characters, arrays (compared by identity), {@link Map} (the format does not
 *       fix the encoding of an entry in format version 1), and any other object.</li>
 * </ul>
 *
 * <p>Strings must be valid Unicode (an unpaired surrogate, which no Rust string can hold, is
 * refused) and are escaped as {@code serde_json} escapes them — {@code \"}, {@code \\},
 * {@code \b \f \n \r \t}, every other control character as a backslash-u escape with four
 * hex digits, everything else verbatim — so that the bytes agree with the Rust side's encoder.
 * No whitespace is emitted.
 */
public final class MpValueEncoder {

    /** Maps a process identifier found inside a payload to its role name. */
    private final Function<Tid, String> roleOf;

    /**
     * Creates an encoder.
     *
     * @param roleOf the role name of a process identifier found inside a payload; must return a
     *     non-empty name for every {@link Tid} that can occur, or throw
     */
    public MpValueEncoder(Function<Tid, String> roleOf) {
        this.roleOf = roleOf;
    }

    /**
     * Encodes a value.
     *
     * @param value the value; see the class comment for what is accepted
     * @return the canonical JSON text
     * @throws IllegalArgumentException if the value or something inside it is refused
     */
    public String encode(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    /**
     * Appends the encoding of {@code v}, recursing into records, lists and sets.
     *
     * @param sb the output
     * @param v the value
     * @throws IllegalArgumentException if {@code v} or a part of it is refused
     */
    private void write(StringBuilder sb, Object v) {
        if (v == null) {
            throw new IllegalArgumentException("null is not a value (only ⊥ is encoded as null, by the exporter)");
        }
        if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long) {
            sb.append(((Number) v).longValue());
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Enum<?> e) {
            quote(sb, e.name());
        } else if (v instanceof Tid t) {
            String role = roleOf.apply(t);
            if (role == null || role.isEmpty()) {
                throw new IllegalArgumentException("no role name for process " + t + " in a payload");
            }
            quote(sb, role);
        } else if (v.getClass().isRecord()) {
            writeRecord(sb, v);
        } else if (v instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else if (v instanceof Set<?> set) {
            List<String> encoded = new ArrayList<>(set.size());
            for (Object o : set) {
                encoded.add(encode(o));
            }
            encoded.sort(MpValueEncoder::compareBytes);
            for (int i = 1; i < encoded.size(); i++) {
                if (encoded.get(i).equals(encoded.get(i - 1))) {
                    throw new IllegalArgumentException(
                            "two elements of a set encode identically: " + encoded.get(i));
                }
            }
            sb.append('[').append(String.join(",", encoded)).append(']');
        } else if (v instanceof Map<?, ?>) {
            throw new IllegalArgumentException(
                    "maps are not encodable in format version 1 (the entry encoding is not fixed)");
        } else if (v instanceof Float || v instanceof Double) {
            throw new IllegalArgumentException("floating-point values are refused by the format: " + v);
        } else if (v.getClass().isArray()) {
            throw new IllegalArgumentException("arrays are compared by identity and are refused: " + v.getClass());
        } else {
            throw new IllegalArgumentException(
                    "not encodable: " + v.getClass().getName() + " (use a @Tag record, a List, a Set, an"
                            + " integer, a boolean, a String, an enum or a Tid)");
        }
    }

    /**
     * Appends a record as {@code {"t":"<tag>","f":[...]}}, with the components in declaration
     * order.
     *
     * @param sb the output
     * @param v a record instance
     * @throws IllegalArgumentException if the record class has no {@link Tag} or a component
     *     cannot be read or encoded
     */
    private void writeRecord(StringBuilder sb, Object v) {
        Class<?> c = v.getClass();
        Tag tag = c.getAnnotation(Tag.class);
        if (tag == null) {
            throw new IllegalArgumentException(
                    "record " + c.getName() + " has no @Tag; the format needs a tag declared identically"
                            + " in both programs, not the class name");
        }
        sb.append("{\"t\":");
        quote(sb, tag.value());
        sb.append(",\"f\":[");
        boolean first = true;
        for (RecordComponent rc : c.getRecordComponents()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            Method acc = rc.getAccessor();
            try {
                acc.setAccessible(true);
                write(sb, acc.invoke(v));
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
                throw new IllegalArgumentException(
                        "cannot read component " + rc.getName() + " of " + c.getName() + ": " + e, e);
            }
        }
        sb.append("]}");
    }

    /**
     * Appends {@code s} as a JSON string literal with {@code serde_json}'s escaping.
     *
     * @param sb the output
     * @param s the string
     */
    public static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                // A supplementary character is two chars; emit the pair verbatim (it becomes
                // UTF-8 when the text is written out).
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    sb.append(ch).append(s.charAt(i + 1));
                    i++;
                    continue;
                }
                throw new IllegalArgumentException(
                        "string is not valid Unicode (unpaired high surrogate at " + i + "): not encodable");
            }
            if (Character.isLowSurrogate(ch)) {
                throw new IllegalArgumentException(
                        "string is not valid Unicode (unpaired low surrogate at " + i + "): not encodable");
            }
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default ->
                {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }

    /**
     * Compares two encoded values by their UTF-8 bytes, unsigned, lexicographically — the order
     * the format prescribes for the elements of a set.
     *
     * @param a an encoded value
     * @param b an encoded value
     * @return negative, zero or positive
     */
    public static int compareBytes(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int c = Byte.compareUnsigned(x[i], y[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(x.length, y.length);
    }

    /**
     * Sorts role names the way the format orders them: ASCII, by the bytes.
     *
     * @param roles the names
     * @return a new sorted list
     */
    public static List<String> sortedRoles(Collection<String> roles) {
        List<String> out = new ArrayList<>(roles);
        out.sort(MpValueEncoder::compareBytes);
        return out;
    }
}
