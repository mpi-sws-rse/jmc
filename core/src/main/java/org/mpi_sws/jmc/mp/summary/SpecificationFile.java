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
import java.nio.charset.CodingErrorAction;
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
 * specification), a {@code visible} list equal to the program's visible roles, and a {@code model}
 * equal to the program's. A header with {@code "minor":1} carries {@code roles}, which must
 * equal the program's roles (its visible roles and the invisible roles a visible payload may name); a header
 * without {@code minor} is minor 0, read as before, its roles not compared ({@link Loaded#rolesCompared}
 * is false, and the caller warns). Any other {@code minor} is refused, an explicit {@code "minor":0} included. The
 * keys {@code program}, {@code size} and {@code tool} are required strings, kept for printing and not compared. Each
 * further line gives
 * {@code statuses} (exactly the visible roles, each {@code done},
 * {@code blocked} or {@code err}), {@code words} (per visible role, a list of {@code {"s":V}},
 * {@code {"r":V}} or {@code {"r":null}}) and {@code counts} (non-negative integers, exactly
 * {@code Σ_t |w_t| · (T − 1)} of them, where {@code w_t} is the word of role {@code t} and
 * {@code T} the number of visible roles). The format allows a word of up to 2^32-1 events (TraceForge's
 * 32-bit counts); this reader holds a word in a Java list, so its practical limit is 2^31-1 observations,
 * and it refuses a count above {@code Integer.MAX_VALUE}. Lines may come in any order; a line whose summary is
 * held already is refused, naming both lines.
 *
 * <p>The reader accepts a file only in the form both writers (JMC's and TraceForge's) emit, so that a file one
 * tool's reader accepts the other's accepts too. Lines end at {@code \n} or {@code \r\n}; an empty line is refused.
 * The header holds only the known keys, each once, compact, in the writers' order ({@code format}, {@code minor},
 * {@code tool}, {@code program}, {@code size}, {@code visible}, {@code roles}, {@code model}, {@code enumeration},
 * {@code seed}, {@code iterations}, the online fields, {@code encoding}); {@code roles} occurs exactly when
 * {@code minor} does; {@code seed} and {@code iterations} only in a {@code sampled} file (so never in a
 * specification); the three online fields together, as integers. A summary line is its normal form
 * ({@link Summary#normalForm()}), optionally followed by {@code n} as its last member, compact; an unknown key,
 * whitespace, another member order or another spelling of a value (an escape where the encoder writes the
 * character) is refused.
 *
 * <p>Values are re-rendered canonically from the parsed JSON so that they compare byte for byte
 * with JMC's own encoding ({@link MpValueEncoder}). Integers are kept as written; a fraction, an
 * exponent, a value outside signed 64 bits and {@code -0} are refused. Booleans, strings (with the
 * encoder's escaping) and arrays (in order) are rendered as they are. Objects are allowed only as
 * tagged records and variants, exactly {@code {"t":"<tag>","f":[...]}} in that key order; anything
 * else is refused, so that a non-canonical rendering of a record fails loudly instead of silently
 * missing or matching a signature. A count for an event of role {@code t} against another role
 * {@code t'} is refused above {@code |w_t'|}, because it counts a prefix of that word. The value
 * of {@code n} is information only, though every number in the file must still be an integer literal. A value therefore
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
     * @param roles the header's roles (minor 1), sorted; {@code null} for a minor-0 file
     * @param lines summary lines read (the header excluded)
     * @param duplicates always 0: a repeated summary is refused (kept for the launcher's report line)
     * @param index the index holding the file's distinct summaries
     */
    public record Loaded(
            Path path,
            String program,
            String size,
            String tool,
            String model,
            List<String> visible,
            List<String> roles,
            int lines,
            int duplicates,
            SetIndex index) {

        /**
         * Whether the header's roles were compared with the program's: {@code false} for a minor-0 file,
         * which has none (the caller warns).
         *
         * @return whether {@code roles} was compared
         */
        public boolean rolesCompared() {
            return roles != null;
        }
    }

    /**
     * What the implementation program declares, compared with the header.
     *
     * @param visible the program's visible roles
     * @param model the program's communication model as the header writes it ({@code p2p}, {@code asyn},
     *     {@code cd})
     * @param roles the program's header roles: its visible roles and the invisible roles a visible payload
     *     may name
     */
    public record Expected(Collection<String> visible, String model, Collection<String> roles) {

        /**
         * Checks the arguments.
         *
         * @param visible the program's visible roles
         * @param model the program's communication model as the header writes it
         * @param roles the program's header roles
         */
        public Expected {
            java.util.Objects.requireNonNull(visible);
            java.util.Objects.requireNonNull(model);
            java.util.Objects.requireNonNull(roles);
        }
    }

    private SpecificationFile() {}

    /**
     * Loads a file.
     *
     * @param path the file
     * @param expected what the implementation program declares
     * @return the loaded specification
     * @throws IllegalArgumentException if the file is refused; the message says why and names
     *     the line
     * @throws IOException if the file cannot be read
     */
    public static Loaded load(Path path, Expected expected) throws IOException {
        List<String> text = lines(path);
        if (text.isEmpty() || text.get(0).isBlank()) {
            throw new IllegalArgumentException("specification " + path + " has no header line");
        }
        JsonObject header = object(parse(text.get(0), 1), 1, "the header");
        headerForm(text.get(0), header);
        requireInt(header, "format", 1, 1);
        requireInt(header, "encoding", 1, 1);
        boolean minor1 = header.has("minor");
        if (minor1) {
            requireInt(header, "minor", 1, 1);
        }
        String enumeration = string(header, "enumeration", 1);
        if (!enumeration.equals("complete")) {
            throw new IllegalArgumentException("specification " + path + " has enumeration \"" + enumeration
                    + "\"; a file whose enumeration is not complete is never accepted as a specification");
        }
        List<String> visible = new ArrayList<>();
        for (JsonElement e : array(header.get("visible"), 1, "visible")) {
            visible.add(stringValue(e, 1, "a visible role"));
        }
        List<String> expectedVisible = MpValueEncoder.sortedRoles(expected.visible());
        if (!visible.equals(expectedVisible)) {
            throw new IllegalArgumentException("specification " + path + " has visible roles " + visible
                    + ", the program " + expectedVisible + "; both sides must declare the same visible roles");
        }
        if (!header.has("model")) {
            throw new IllegalArgumentException("specification " + path + " has no model; the program's is "
                    + expected.model());
        }
        String model = string(header, "model", 1);
        if (!model.equals(expected.model())) {
            throw new IllegalArgumentException("specification " + path + ": the two models differ: "
                    + model + " and " + expected.model());
        }
        List<String> roles = null;
        if (minor1) {
            roles = new ArrayList<>();
            for (JsonElement e : array(header.get("roles"), 1, "roles")) {
                roles.add(stringValue(e, 1, "a role"));
            }
            List<String> expectedRoles = MpValueEncoder.sortedRoles(expected.roles());
            if (!roles.equals(expectedRoles)) {
                throw new IllegalArgumentException("specification " + path + " has roles " + roles
                        + ", the program " + expectedRoles + "; both sides must declare the same roles (the"
                        + " visible and the payload-nameable ones)");
            }
            roles = List.copyOf(roles);
        }
        String program = string(header, "program", 1);
        String size = string(header, "size", 1);
        String tool = string(header, "tool", 1);

        SetIndex index = new SetIndex();
        int lines = 0;
        for (int i = 1; i < text.size(); i++) {
            String line = text.get(i);
            int no = i + 1;
            if (line.isEmpty()) {
                throw refuse(no, "an empty line; every line after the header is a summary line");
            }
            lines++;
            Summary s = writerFormLine(line, visible, no);
            if (index.insert(s) instanceof SetIndex.Inserted.Duplicate) {
                String key = s.normalForm();
                int first = 2;
                while (!writerFormLine(text.get(first - 1), visible, first).normalForm().equals(key)) {
                    first++;
                }
                throw refuse(no, "repeats line " + first + " (the same summary); a file holds each summary once");
            }
        }
        return new Loaded(path, program, size, tool, model, visible, roles, lines, 0, index);
    }

    /** The header keys, in the order both writers emit them; which must be present is checked by name. */
    private static final List<String> HEADER_KEYS = List.of("format", "minor", "tool", "program", "size", "visible",
            "roles", "model", "enumeration", "seed", "iterations", "first_miss_iteration", "first_miss_wall_ms",
            "uncovered_total", "encoding");

    /** The online check's header fields, written together. */
    private static final List<String> ONLINE_KEYS = List.of("first_miss_iteration", "first_miss_wall_ms",
            "uncovered_total");

    /** The keys a summary line may have; {@code n}, the execution count, is information only. */
    private static final List<String> LINE_KEYS = List.of("statuses", "words", "counts", "n");

    /**
     * Splits a file into lines at {@code \n}, a {@code \r\n} counting as one line end; a final line end ends the
     * last line. A bare {@code \r} is not a line end, so it stays inside its line (and fails that line's form).
     * The bytes must be UTF-8.
     */
    static List<String> lines(Path path) throws IOException {
        java.nio.charset.CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        char[] buf = new char[1 << 16];
        try (java.io.Reader in = new java.io.InputStreamReader(Files.newInputStream(path), utf8)) {
            for (int n = in.read(buf); n >= 0; n = in.read(buf)) {
                for (int i = 0; i < n; i++) {
                    char c = buf[i];
                    if (c != '\n') {
                        line.append(c);
                        continue;
                    }
                    int len = line.length();
                    if (len > 0 && line.charAt(len - 1) == '\r') {
                        line.setLength(len - 1);
                    }
                    out.add(line.toString());
                    line.setLength(0);
                }
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /**
     * Refuses a header that is not in the form both writers emit: only the known keys, each once, in
     * {@link #HEADER_KEYS} order, compact; {@code minor} absent (minor 0) or 1, with {@code roles} exactly when
     * {@code minor} is present; {@code seed} and {@code iterations} integers and only in a {@code sampled} file;
     * the online fields all three or none, integers. The other fields' presence and types are checked by name.
     */
    private static void headerForm(String text, JsonObject h) {
        for (String k : h.keySet()) {
            if (!HEADER_KEYS.contains(k)) {
                throw refuse(1, "unknown header key \"" + k + "\" (neither writer emits it)");
            }
        }
        if (h.has("minor") && isInteger(h.get("minor"), 0)) {
            throw refuse(1, "the header has \"minor\":0; a minor-0 header has no minor key");
        }
        if (h.has("roles") != h.has("minor")) {
            throw refuse(1, "the header has roles without minor or minor without roles");
        }
        JsonElement enumeration = h.get("enumeration");
        boolean sampled = enumeration != null && enumeration.isJsonPrimitive()
                && enumeration.getAsJsonPrimitive().isString() && enumeration.getAsString().equals("sampled");
        for (String k : List.of("seed", "iterations")) {
            if (h.has(k)) {
                if (!sampled) {
                    throw refuse(1, "the header has " + k + " in a file that is not sampled");
                }
                if (!isInteger(h.get(k), null)) {
                    throw refuse(1, "the header's " + k + " is " + h.get(k) + ", not an integer");
                }
            }
        }
        long online = ONLINE_KEYS.stream().filter(h::has).count();
        if (online != 0 && online != ONLINE_KEYS.size()) {
            throw refuse(1, "the header has some of the online fields (first_miss_iteration, first_miss_wall_ms,"
                    + " uncovered_total) but not all three");
        }
        for (String k : ONLINE_KEYS) {
            if (h.has(k) && !isInteger(h.get(k), null)) {
                throw refuse(1, "the header's " + k + " is " + h.get(k) + ", not an integer");
            }
        }
        StringBuilder expected = new StringBuilder("{");
        for (String k : HEADER_KEYS) {
            if (h.has(k)) {
                if (expected.length() > 1) {
                    expected.append(',');
                }
                MpValueEncoder.quote(expected, k);
                expected.append(':');
                plain(expected, h.get(k));
            }
        }
        expected.append('}');
        int at = differsAt(text, expected.toString());
        if (at >= 0) {
            throw refuse(1, "the header is not in the form the writers emit (compact, each key once, in the order"
                    + " format, minor, tool, program, size, visible, roles, model, enumeration, seed, iterations,"
                    + " the online fields, encoding): it differs at byte " + at);
        }
    }

    /**
     * Parses a summary line and refuses it unless it is in the form both writers emit: its normal form
     * ({@link Summary#normalForm()}), optionally followed by {@code n} as the last member, compact. The value of
     * {@code n} is not checked beyond strict JSON: it is information only.
     */
    static Summary writerFormLine(String line, List<String> visible, int no) {
        JsonObject o = object(parse(line, no), no, "a summary line");
        for (String k : o.keySet()) {
            if (!LINE_KEYS.contains(k)) {
                throw refuse(no, "unknown key \"" + k + "\" in a summary line (neither writer emits it)");
            }
        }
        Summary s = summary(o, visible, no);
        String key = s.normalForm();
        String expected = key;
        if (o.has("n")) {
            String head = key.substring(0, key.length() - 1) + ",\"n\":";
            if (line.startsWith(head) && line.endsWith("}")
                    && compact(line.substring(head.length(), line.length() - 1))) {
                return s;
            }
            StringBuilder sb = new StringBuilder(head);
            plain(sb, o.get("n"));
            expected = sb.append('}').toString();
        }
        int at = differsAt(line, expected);
        if (at >= 0) {
            throw refuse(no, "the line is not in the form the writers emit (the normal form, then n if any;"
                    + " compact, each key once, in order): it differs at byte " + at);
        }
        return s;
    }

    /**
     * The header's lists when the reader would accept the header on its own, without comparing it with a program:
     * the writers' form, {@code format} and {@code encoding} 1, {@code minor} absent or 1, the string fields
     * present, {@code visible} distinct names, and with {@code minor} a {@code roles} list of valid names
     * (non-empty ASCII, not {@code main}, not starting with {@code ~}), byte-sorted and distinct, holding every
     * visible name. {@code null} when the header would be refused.
     *
     * @param text the header line
     * @return the header's {@code visible} list (as written) and {@code roles} list ({@code null} for minor 0),
     *     or {@code null}
     */
    static HeaderLists readableHeader(String text) {
        try {
            JsonObject h = object(parse(text, 1), 1, "the header");
            headerForm(text, h);
            requireInt(h, "format", 1, 1);
            requireInt(h, "encoding", 1, 1);
            if (h.has("minor")) {
                requireInt(h, "minor", 1, 1);
            }
            for (String k : List.of("enumeration", "model", "program", "size", "tool")) {
                string(h, k, 1);
            }
            List<String> visible = new ArrayList<>();
            for (JsonElement e : array(h.get("visible"), 1, "visible")) {
                visible.add(stringValue(e, 1, "a visible role"));
            }
            if (new java.util.HashSet<>(visible).size() != visible.size()) {
                return null;
            }
            List<String> roles = null;
            if (h.has("minor")) {
                roles = new ArrayList<>();
                for (JsonElement e : array(h.get("roles"), 1, "roles")) {
                    String r = stringValue(e, 1, "a role");
                    if (r.isEmpty() || r.equals("main") || r.startsWith("~") || !r.chars().allMatch(c -> c < 0x80)) {
                        return null;
                    }
                    roles.add(r);
                }
                for (int i = 1; i < roles.size(); i++) {
                    if (MpValueEncoder.compareBytes(roles.get(i - 1), roles.get(i)) >= 0) {
                        return null;
                    }
                }
                if (!roles.containsAll(visible)) {
                    return null;
                }
            }
            return new HeaderLists(visible, roles);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * A readable header's lists.
     *
     * @param visible the {@code visible} list as written
     * @param roles the {@code roles} list, or {@code null} for a minor-0 header
     */
    record HeaderLists(List<String> visible, List<String> roles) {}

    /** The first UTF-8 byte offset at which {@code text} and {@code expected} differ, or -1 if they are equal. */
    private static int differsAt(String text, String expected) {
        byte[] a = text.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        if (java.util.Arrays.equals(a, b)) {
            return -1;
        }
        int i = 0;
        while (i < a.length && i < b.length && a[i] == b[i]) {
            i++;
        }
        return i;
    }

    /** Whether {@code raw} has no whitespace outside its JSON strings. */
    private static boolean compact(String raw) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code e} is an integer, equal to {@code value} when that is not null. */
    private static boolean isInteger(JsonElement e, Integer value) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        String lit = e.getAsJsonPrimitive().getAsNumber().toString();
        return INTEGER.matcher(lit).matches() && (value == null || lit.equals(value.toString()));
    }

    /** A parsed value written compactly, strings with the encoder's escaping, keys in written order. */
    private static void plain(StringBuilder sb, JsonElement v) {
        if (v.isJsonNull()) {
            sb.append("null");
        } else if (v.isJsonPrimitive()) {
            JsonPrimitive p = v.getAsJsonPrimitive();
            if (p.isString()) {
                MpValueEncoder.quote(sb, p.getAsString());
            } else if (p.isBoolean()) {
                sb.append(p.getAsBoolean() ? "true" : "false");
            } else {
                sb.append(p.getAsNumber().toString());
            }
        } else if (v.isJsonArray()) {
            sb.append('[');
            boolean first = true;
            for (JsonElement e : v.getAsJsonArray()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                plain(sb, e);
            }
            sb.append(']');
        } else {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> e : v.getAsJsonObject().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                MpValueEncoder.quote(sb, e.getKey());
                sb.append(':');
                plain(sb, e.getValue());
            }
            sb.append('}');
        }
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
