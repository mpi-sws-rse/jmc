package org.mpi_sws.jmc.mp.summary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads a specification file into a {@link SetIndex}. This is the specification side of JMC's
 * online conformance mode: the file lists the compact summaries ({@link Summary}) of the
 * specification program, one JSON object per line after a header line.
 *
 * <p>The header must have {@code format} 1 and {@code encoding} 1, {@code enumeration}
 * {@code "complete"} (a file whose enumeration is not complete is never accepted as a
 * specification) and a {@code visible} list equal to the program's visible roles. The keys
 * {@code program}, {@code size}, {@code tool} and {@code model} are kept for printing and not
 * compared. Each further line gives {@code statuses} (exactly the visible roles, each {@code done},
 * {@code blocked} or {@code err}), {@code words} (per visible role, a list of {@code {"s":V}},
 * {@code {"r":V}} or {@code {"r":null}}) and {@code counts} (non-negative integers, exactly
 * {@code Σ_t |w_t| · (T − 1)} of them, where {@code w_t} is the word of role {@code t} and
 * {@code T} the number of visible roles). Lines may come in any order; a line whose summary is
 * held already is a duplicate and is held once.
 *
 * <p>Values are re-rendered canonically from the parsed JSON so that they compare byte for byte
 * with JMC's own encoding ({@link MpValueEncoder}). Integers are kept as written; a fraction, an
 * exponent, a value outside signed 64 bits and {@code -0} are refused. Booleans, strings (with the
 * encoder's escaping) and arrays (in order) are rendered as they are. Objects are allowed only as
 * tagged records and variants, exactly {@code {"t":"<tag>","f":[...]}} in that key order; anything
 * else is refused, so that a non-canonical rendering of a record fails loudly instead of silently
 * missing or matching a signature. A count for an event of role {@code t} against another role
 * {@code t'} is refused above {@code |w_t'|}, because it counts a prefix of that word. Blank lines
 * are skipped. The keys {@code n}, {@code seed}, {@code iterations} and any unknown key are
 * ignored, though every number in the file must still be an integer literal. A value therefore
 * compares equal to JMC's iff its bytes are the canonical encoding, which both writers must
 * produce.
 *
 * <p>Parsing is strict JSON. A lexical pass refuses raw control characters inside strings and
 * keywords not spelled {@code true}/{@code false}/{@code null}. Gson's {@link JsonReader} in
 * non-lenient mode then refuses unquoted or single-quoted strings, comments and trailing content.
 * The tree is built in this class so that a duplicate key inside an object is refused rather than
 * resolved last-wins.
 */
public final class SpecificationFile {

    private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]*)");

    /**
     * A loaded specification.
     *
     * @param path the file
     * @param program the header's {@code program}
     * @param size the header's {@code size}
     * @param tool the header's {@code tool}
     * @param model the header's {@code model}
     * @param visible the header's visible roles, sorted
     * @param lines summary lines read (the header excluded)
     * @param duplicates lines whose summary was held already
     * @param index the index holding the file's distinct summaries
     */
    public record Loaded(
            Path path,
            String program,
            String size,
            String tool,
            String model,
            List<String> visible,
            int lines,
            int duplicates,
            SetIndex index) {}

    private SpecificationFile() {}

    /**
     * Loads a file.
     *
     * @param path the file
     * @param visibleRoles the implementation program's visible roles
     * @return the loaded specification
     * @throws IllegalArgumentException if the file is refused; the message says why and names
     *     the line
     * @throws IOException if the file cannot be read
     */
    public static Loaded load(Path path, Collection<String> visibleRoles) throws IOException {
        List<String> text = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (text.isEmpty() || text.get(0).isBlank()) {
            throw new IllegalArgumentException("specification " + path + " has no header line");
        }
        JsonObject header = object(parse(text.get(0), 1), 1, "the header");
        requireInt(header, "format", 1, 1);
        requireInt(header, "encoding", 1, 1);
        String enumeration = string(header, "enumeration", 1);
        if (!enumeration.equals("complete")) {
            throw new IllegalArgumentException("specification " + path + " has enumeration \"" + enumeration
                    + "\"; a file whose enumeration is not complete is never accepted as a specification");
        }
        List<String> visible = new ArrayList<>();
        for (JsonElement e : array(header.get("visible"), 1, "visible")) {
            visible.add(stringValue(e, 1, "a visible role"));
        }
        List<String> expected = MpValueEncoder.sortedRoles(visibleRoles);
        if (!visible.equals(expected)) {
            throw new IllegalArgumentException("specification " + path + " has visible roles " + visible
                    + ", the program " + expected + "; both sides must declare the same visible roles");
        }
        String program = header.has("program") ? string(header, "program", 1) : "";
        String size = header.has("size") ? string(header, "size", 1) : "";
        String tool = header.has("tool") ? string(header, "tool", 1) : "";
        String model = header.has("model") ? string(header, "model", 1) : "";

        SetIndex index = new SetIndex();
        int lines = 0;
        int duplicates = 0;
        for (int i = 1; i < text.size(); i++) {
            String line = text.get(i);
            if (line.isBlank()) {
                continue;
            }
            int no = i + 1;
            lines++;
            Summary s = summary(object(parse(line, no), no, "a summary line"), visible, no);
            if (index.insert(s) instanceof SetIndex.Inserted.Duplicate) {
                duplicates++;
            }
        }
        return new Loaded(path, program, size, tool, model, visible, lines, duplicates, index);
    }

    /**
     * Parses one summary line (no header) against the visible roles.
     *
     * @param line the JSON line
     * @param visible the visible roles, sorted
     * @return the summary
     * @throws IllegalArgumentException if the line is refused
     */
    public static Summary parseLine(String line, List<String> visible) {
        return summary(object(parse(line, 0), 0, "a summary line"), visible, 0);
    }

    private static Summary summary(JsonObject o, List<String> visible, int no) {
        JsonObject statusesJson = object(o.get("statuses"), no, "statuses");
        JsonObject wordsJson = object(o.get("words"), no, "words");
        requireKeys(statusesJson, visible, no, "statuses");
        requireKeys(wordsJson, visible, no, "words");
        Map<String, String> statuses = new LinkedHashMap<>();
        Map<String, List<String>> words = new LinkedHashMap<>();
        int positions = 0;
        for (String r : visible) {
            String st = stringValue(statusesJson.get(r), no, "the status of " + r);
            if (!(st.equals("done") || st.equals("blocked") || st.equals("err"))) {
                throw refuse(no, "status of " + r + " is \"" + st + "\", not done, blocked or err");
            }
            statuses.put(r, st);
            List<String> word = new ArrayList<>();
            for (JsonElement e : array(wordsJson.get(r), no, "the word of " + r)) {
                word.add(observation(e, no, r));
            }
            words.put(r, word);
            positions += word.size();
        }
        List<Integer> counts = new ArrayList<>();
        for (JsonElement e : array(o.get("counts"), no, "counts")) {
            String lit = integerLiteral(e, no, "a count");
            long c;
            try {
                c = Long.parseLong(lit);
            } catch (NumberFormatException ex) {
                throw refuse(no, "count " + lit + " is outside signed 64 bits");
            }
            if (c < 0 || c > Integer.MAX_VALUE) {
                throw refuse(no, "count " + lit + " is not a non-negative integer a thread can reach");
            }
            counts.add((int) c);
        }
        int expected = positions * (visible.size() - 1);
        if (counts.size() != expected) {
            throw refuse(no, "counts has " + counts.size() + " entries; " + positions + " visible events over "
                    + visible.size() + " roles need " + expected);
        }
        // Event-major layout: for each event of role t, one count per other role t', in role order.
        // A count is the number of t' events that precede the event, so it is bounded by the
        // length of the word of t'.
        int i = 0;
        for (String t : visible) {
            for (int e = 0; e < words.get(t).size(); e++) {
                for (String other : visible) {
                    if (other.equals(t)) {
                        continue;
                    }
                    int c = counts.get(i++);
                    if (c > words.get(other).size()) {
                        throw refuse(no, "count " + c + " for event " + e + " of " + t + " exceeds the "
                                + words.get(other).size() + " visible events of " + other);
                    }
                }
            }
        }
        return new Summary(visible, statuses, words, counts);
    }

    private static String observation(JsonElement e, int no, String role) {
        if (!e.isJsonObject() || e.getAsJsonObject().size() != 1) {
            throw refuse(no, "an observation of " + role + " is not {\"s\":V}, {\"r\":V} or {\"r\":null}: " + e);
        }
        JsonObject ob = e.getAsJsonObject();
        if (ob.has("s")) {
            JsonElement v = ob.get("s");
            if (v.isJsonNull()) {
                throw refuse(no, "a send of " + role + " has value null; only a receive reads ⊥");
            }
            return "{\"s\":" + canonical(v, no) + "}";
        }
        if (ob.has("r")) {
            JsonElement v = ob.get("r");
            return v.isJsonNull() ? "{\"r\":null}" : "{\"r\":" + canonical(v, no) + "}";
        }
        throw refuse(no, "an observation of " + role + " has key " + ob.keySet() + ", not s or r");
    }

    /**
     * The canonical rendering of a parsed value: no whitespace, the encoder's string escaping and
     * keys in written order.
     *
     * @param v the parsed value
     * @param no the line number for messages
     * @return the rendering
     */
    static String canonical(JsonElement v, int no) {
        StringBuilder sb = new StringBuilder();
        render(sb, v, no);
        return sb.toString();
    }

    private static void render(StringBuilder sb, JsonElement v, int no) {
        if (v.isJsonNull()) {
            throw refuse(no, "null inside a value; only a receive's ⊥ is null");
        }
        if (v.isJsonPrimitive()) {
            JsonPrimitive p = v.getAsJsonPrimitive();
            if (p.isBoolean()) {
                sb.append(p.getAsBoolean() ? "true" : "false");
            } else if (p.isString()) {
                MpValueEncoder.quote(sb, p.getAsString());
            } else {
                String lit = integerLiteral(p, no, "a number");
                try {
                    Long.parseLong(lit);
                } catch (NumberFormatException ex) {
                    throw refuse(no, "integer " + lit + " is outside signed 64 bits");
                }
                sb.append(lit);
            }
            return;
        }
        if (v.isJsonArray()) {
            sb.append('[');
            boolean first = true;
            for (JsonElement e : v.getAsJsonArray()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                render(sb, e, no);
            }
            sb.append(']');
            return;
        }
        JsonObject o = v.getAsJsonObject();
        List<String> keys = new ArrayList<>(o.keySet());
        if (!keys.equals(List.of("t", "f")) || !o.get("t").isJsonPrimitive()
                || !o.get("t").getAsJsonPrimitive().isString() || !o.get("f").isJsonArray()) {
            throw refuse(no, "an object value must be a tagged record {\"t\":\"<tag>\",\"f\":[...]} in that key"
                    + " order (maps are not in format version 1): " + o);
        }
        sb.append("{\"t\":");
        MpValueEncoder.quote(sb, o.get("t").getAsString());
        sb.append(",\"f\":");
        render(sb, o.get("f"), no);
        sb.append('}');
    }

    private static String integerLiteral(JsonElement e, int no, String what) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw refuse(no, what + " is not a number: " + e);
        }
        String lit = e.getAsJsonPrimitive().getAsNumber().toString();
        if (!INTEGER.matcher(lit).matches()) {
            throw refuse(no, what + " is not an integer literal (the format refuses floating-point values): " + lit);
        }
        if (lit.equals("-0")) {
            throw refuse(no, what + " is -0, which is not the canonical encoding of 0");
        }
        return lit;
    }

    private static JsonElement parse(String line, int no) {
        lex(line, no);
        try (JsonReader reader = new JsonReader(new StringReader(line))) {
            reader.setLenient(false);
            JsonElement e = read(reader, no);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw refuse(no, "trailing content after the JSON value");
            }
            return e;
        } catch (MalformedJsonException | IllegalStateException | NumberFormatException e) {
            throw refuse(no, "not strict JSON: " + e.getMessage());
        } catch (IOException e) {
            throw refuse(no, "cannot read: " + e.getMessage());
        }
    }

    /**
     * Refuses the JSON lexical violations that Gson's non-lenient reader still accepts: a raw
     * control character (below U+0020) inside a string literal, which JSON requires to be escaped;
     * a keyword not spelled exactly {@code true}, {@code false} or {@code null}; a control
     * character other than a tab between tokens; and an escape that is not one of JSON's eight
     * single-character escapes or {@code \\uXXXX} (Gson also takes {@code \\'}). The check covers
     * the whole line, object names and ignored fields included, and runs before the reader sees it.
     *
     * @param line the JSON line
     * @param no the line number for messages
     * @throws IllegalArgumentException if the line violates one of these restrictions
     */
    private static void lex(String line, int no) {
        boolean inString = false;
        int wordStart = -1;
        for (int i = 0; i <= line.length(); i++) {
            char c = i < line.length() ? line.charAt(i) : ' ';
            if (inString) {
                if (c < 0x20) {
                    throw refuse(no, "raw control character U+" + String.format("%04X", (int) c)
                            + " inside a string; JSON requires an escape");
                }
                if (c == '\\') {
                    // Only JSON's escapes are valid: Gson's strict reader also takes \'.
                    i++;
                    char e = i < line.length() ? line.charAt(i) : ' ';
                    if ("\"\\/bfnrt".indexOf(e) < 0 && e != 'u' || i >= line.length()) {
                        throw refuse(no, "invalid escape \\" + (i < line.length() ? String.valueOf(e) : "<end>")
                                + " inside a string; JSON allows \\\" \\\\ \\/ \\b \\f \\n \\r \\t \\uXXXX");
                    }
                    if (e == 'u') {
                        for (int k = 1; k <= 4; k++) {
                            if (i + k >= line.length() || Character.digit(line.charAt(i + k), 16) < 0) {
                                throw refuse(no, "invalid escape \\u without four hexadecimal digits inside a string");
                            }
                        }
                        i += 4;
                    }
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            boolean letter = Character.isLetter(c);
            if (letter) {
                if (wordStart < 0) {
                    wordStart = i;
                }
                continue;
            }
            if (wordStart >= 0) {
                String word = line.substring(wordStart, i);
                if (wordStart > 0 && Character.isDigit(line.charAt(wordStart - 1))) {
                    // 3e0, 1E3: a float's exponent; report it as a non-integer number.
                    throw refuse(no, "number ending in \"" + word + "\" is not an integer literal (the format"
                            + " refuses floating-point values)");
                }
                if (!(word.equals("true") || word.equals("false") || word.equals("null"))) {
                    throw refuse(no, "\"" + word + "\" is not a JSON keyword (true, false and null, lowercase)"
                            + " and not a quoted string");
                }
                wordStart = -1;
            }
            if (c == '"') {
                inString = true;
            } else if (c < 0x20 && c != '\t' && i < line.length()) {
                // A tab is JSON whitespace between tokens; other control characters are not.
                throw refuse(no, "raw control character U+" + String.format("%04X", (int) c) + " outside a string");
            }
        }
        if (inString) {
            throw refuse(no, "unterminated string");
        }
    }

    /** Builds the tree from a strict reader; duplicate keys and non-integer numbers are refused here. */
    private static JsonElement read(JsonReader reader, int no) throws IOException {
        JsonToken t = reader.peek();
        if (t == JsonToken.BEGIN_ARRAY) {
            JsonArray a = new JsonArray();
            reader.beginArray();
            while (reader.hasNext()) {
                a.add(read(reader, no));
            }
            reader.endArray();
            return a;
        }
        if (t == JsonToken.BEGIN_OBJECT) {
            JsonObject o = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (o.has(key)) {
                    throw refuse(no, "duplicate key \"" + key + "\" in an object");
                }
                o.add(key, read(reader, no));
            }
            reader.endObject();
            return o;
        }
        if (t == JsonToken.STRING) {
            return new JsonPrimitive(reader.nextString());
        }
        if (t == JsonToken.NUMBER) {
            String lit = reader.nextString();
            if (!INTEGER.matcher(lit).matches()) {
                throw refuse(no, "number " + lit + " is not an integer literal (the format refuses"
                        + " floating-point values)");
            }
            if (lit.equals("-0")) {
                throw refuse(no, "number -0 is not the canonical encoding of 0");
            }
            try {
                return new JsonPrimitive(Long.parseLong(lit));
            } catch (NumberFormatException e) {
                throw refuse(no, "integer " + lit + " is outside signed 64 bits");
            }
        }
        if (t == JsonToken.BOOLEAN) {
            return new JsonPrimitive(reader.nextBoolean());
        }
        if (t == JsonToken.NULL) {
            reader.nextNull();
            return JsonNull.INSTANCE;
        }
        throw refuse(no, "unexpected token " + t);
    }

    private static JsonObject object(JsonElement e, int no, String what) {
        if (e == null || !e.isJsonObject()) {
            throw refuse(no, what + " is not a JSON object" + (e == null ? " (missing)" : ": " + e));
        }
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonElement e, int no, String what) {
        if (e == null || !e.isJsonArray()) {
            throw refuse(no, what + " is not a JSON array" + (e == null ? " (missing)" : ": " + e));
        }
        return e.getAsJsonArray();
    }

    private static String string(JsonObject o, String key, int no) {
        return stringValue(o.get(key), no, key);
    }

    private static String stringValue(JsonElement e, int no, String what) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw refuse(no, what + " is not a JSON string" + (e == null ? " (missing)" : ": " + e));
        }
        return e.getAsString();
    }

    private static void requireInt(JsonObject o, String key, int expected, int no) {
        JsonElement e = o.get(key);
        if (e == null) {
            throw refuse(no, key + " is missing");
        }
        String lit = integerLiteral(e, no, key);
        if (!lit.equals(Integer.toString(expected))) {
            throw refuse(no, key + " is " + lit + "; this reader knows " + key + " " + expected);
        }
    }

    private static void requireKeys(JsonObject o, List<String> visible, int no, String what) {
        List<String> keys = new ArrayList<>(o.keySet());
        List<String> sorted = MpValueEncoder.sortedRoles(keys);
        if (!sorted.equals(visible) || keys.size() != visible.size()) {
            throw refuse(no, what + " names roles " + keys + ", the visible roles are " + visible);
        }
    }

    private static IllegalArgumentException refuse(int no, String why) {
        return new IllegalArgumentException((no > 0 ? "line " + no + ": " : "") + why);
    }
}
