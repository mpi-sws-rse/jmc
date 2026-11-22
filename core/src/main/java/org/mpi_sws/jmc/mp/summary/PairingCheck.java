package org.mpi_sws.jmc.mp.summary;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;

import org.mpi_sws.jmc.mp.events.MpMessage;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The pairing check of the online check: before a specification file is compared with a program's executions,
 * verify the restrictions a meaningful comparison needs, each reported with its row ({@code C2} … {@code C18}) of
 * the comparison contract between JMC's and TraceForge's summary files. TraceForge's {@code conf-join} runs the
 * same check on two files, with the same wording; here the implementation side is the program: its visible
 * roles, its header roles, its model and the tags of the messages it can send.
 *
 * <ul>
 *   <li><b>C2</b>: the specification's {@code visible} list is byte-sorted with distinct names, and equal to the
 *       program's; when the specification carries {@code roles} (minor 1), that list equals the program's.</li>
 *   <li><b>C3</b>: the two models are equal and in the supported set {@code p2p}, {@code asyn}.</li>
 *   <li><b>C4</b>: the specification's {@code enumeration} is {@code complete}.</li>
 *   <li><b>C6</b>: the specification's lines are distinct (by their normal form when the line is readable, else
 *       by its text).</li>
 *   <li><b>C10</b>: every count is at most 2^31−1 and every word has at most 2^31−1 events.</li>
 *   <li><b>C13</b>: a tag used on both sides is used by the specification only with field arities the program
 *       declares for it (TraceForge, comparing two files, requires the same arities on both; a program may
 *       declare one tag with several arities, as two Rust enums may each have a variant of that name, and a
 *       specification run need not use them all). A tag on one side only is a warning: a specification-only tag
 *       is one the program never sends, a program-only tag one whose lines the check reports as uncovered.</li>
 *   <li><b>C18</b>: a specification value tagged {@code ThreadId} or {@code Tid} (the two tools' identity types)
 *       is a warning that it may be a raw identity instead of a role name.</li>
 * </ul>
 *
 * <p>A header the reader refuses contributes nothing to C2, C3 and C4, and a line it refuses nothing to C13 and
 * C18 (it still counts for C6, by its text, and for C10): those are the reader's refusals, reported by
 * {@link SpecificationFile#load} with its own message. A line that is not JSON is skipped here for the same
 * reason.
 *
 * <p>The program's tags ({@link #programTags}) are those of the records and enums in the program class's package
 * (the same classpath entry) that implement {@link MpMessage}, and of every record, enum and sealed interface
 * reachable from their components, through {@link List}, {@link Set} and {@link Optional} too: a record's tag is
 * its {@link Tag} with its number of components, an enum constant's its name with none, {@code Optional} gives
 * {@code None} and {@code Some}. A message type outside that package is not seen, which can only add one-sided
 * warnings.
 */
public final class PairingCheck {

    private static final BigInteger COUNT_MAX = BigInteger.valueOf((1L << 31) - 1);
    private static final long WORD_MAX = (1L << 31) - 1;
    private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]*)");
    private static final TypeAdapter<JsonElement> JSON = new Gson().getAdapter(JsonElement.class);

    private PairingCheck() {}

    /**
     * One failed or warned check.
     *
     * @param row the contract row, {@code C2} … {@code C18}
     * @param what the description
     */
    public record Item(String row, String what) {}

    /**
     * The check's result.
     *
     * @param failures the failed checks: the comparison must not run
     * @param warnings the warnings: it may
     */
    public record Report(List<Item> failures, List<Item> warnings) {

        /**
         * Whether no check failed.
         *
         * @return whether the comparison may run
         */
        public boolean passed() {
            return failures.isEmpty();
        }
    }

    /**
     * Checks a specification file against a program.
     *
     * @param spec the specification file
     * @param expected the program's visible roles, model and header roles
     * @param programTags the tags of the messages the program can send, with their field arities
     * @return the report
     * @throws IOException if the file cannot be read
     */
    public static Report check(Path spec, SpecificationFile.Expected expected,
                               Map<String, SortedSet<Integer>> programTags) throws IOException {
        List<Item> failures = new ArrayList<>();
        List<Item> warnings = new ArrayList<>();
        List<String> text = SpecificationFile.lines(Files.readAllBytes(spec));
        if (text.isEmpty()) {
            throw new IOException(spec + ": the file is empty");
        }
        String at = spec + ": ";
        SpecificationFile.HeaderLists lists = SpecificationFile.readableHeader(text.get(0));
        JsonObject header = lists == null ? null : object(text.get(0));
        List<String> visible = lists == null ? null : lists.visible();
        List<String> roles = lists == null ? null : lists.roles();
        for (Map.Entry<String, List<String>> e : listsOf(visible, roles).entrySet()) {
            List<String> l = e.getValue();
            for (int i = 1; i < l.size(); i++) {
                if (MpValueEncoder.compareBytes(l.get(i - 1), l.get(i)) >= 0) {
                    failures.add(new Item("C2", at + "line 1: the `" + e.getKey() + "` list " + debug(l)
                            + " is not byte-sorted with distinct names"));
                    break;
                }
            }
        }
        List<String> programVisible = MpValueEncoder.sortedRoles(expected.visible());
        List<String> programRoles = MpValueEncoder.sortedRoles(expected.roles());
        if (visible != null && !visible.equals(programVisible)) {
            failures.add(new Item("C2", "the two `visible` lists differ: " + debug(visible) + " and "
                    + debug(programVisible)));
        }
        if (roles != null && !roles.equals(programRoles)) {
            failures.add(new Item("C2", "the two `roles` lists differ: " + debug(roles) + " and "
                    + debug(programRoles)));
        }
        if (header != null) {
            String a = header.get("model").getAsString();
            String b = expected.model();
            if (!a.equals(b)) {
                failures.add(new Item("C3", "the two models differ: " + a + " and " + b));
            }
            for (String[] m : new String[][] {{a, "specification"}, {b, "implementation"}}) {
                if (!m[0].equals("p2p") && !m[0].equals("asyn")) {
                    failures.add(new Item("C3", "the " + m[1] + "'s model `" + m[0]
                            + "` is not supported for a join (p2p or asyn)"));
                }
            }
            String e = header.get("enumeration").getAsString();
            if (!e.equals("complete")) {
                failures.add(new Item("C4", "the specification is `" + e + "`, not `complete`"));
            }
        }

        Map<String, SortedSet<Integer>> specTags = new TreeMap<>(MpValueEncoder::compareBytes);
        List<String> sorted = MpValueEncoder.sortedRoles(visible == null ? List.of() : visible);
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < text.size(); i++) {
            int number = i + 1;
            String line = text.get(i);
            JsonObject m = object(line);
            if (m == null) {
                continue;
            }
            JsonElement counts = m.get("counts");
            if (counts != null && counts.isJsonArray()) {
                for (JsonElement c : counts.getAsJsonArray()) {
                    BigInteger v = unsigned(c);
                    if (v != null && v.compareTo(COUNT_MAX) > 0) {
                        failures.add(new Item("C10", at + "line " + number + ": the count " + v
                                + " is out of bounds (both readers accept counts up to 2^31-1)"));
                        break;
                    }
                }
            }
            JsonElement words = m.get("words");
            if (words != null && words.isJsonObject()) {
                for (Map.Entry<String, JsonElement> w : words.getAsJsonObject().entrySet()) {
                    if (w.getValue().isJsonArray() && w.getValue().getAsJsonArray().size() > WORD_MAX) {
                        failures.add(new Item("C10", at + "line " + number + ": the word of `" + w.getKey()
                                + "` has " + w.getValue().getAsJsonArray().size() + " events (at most 2^31-1)"));
                    }
                }
            }
            String key = line;
            try {
                key = SpecificationFile.writerFormLine(line, sorted, number).normalForm();
                for (JsonElement w : words.getAsJsonObject().asMap().values()) {
                    for (JsonElement ob : w.getAsJsonArray()) {
                        for (JsonElement v : ob.getAsJsonObject().asMap().values()) {
                            walk(v, specTags, what -> warnings.add(new Item("C18", at + "line " + number + ": "
                                    + what)));
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                // Refused by the reader: no tags; C6 by the line's text.
            }
            if (!seen.add(key)) {
                failures.add(new Item("C6", at + "line " + number + " repeats an earlier line"));
            }
        }

        for (Map.Entry<String, SortedSet<Integer>> t : specTags.entrySet()) {
            SortedSet<Integer> ia = programTags.get(t.getKey());
            String tag = quoted(t.getKey());
            if (ia == null) {
                warnings.add(new Item("C13", "the tag " + tag + " (arities " + debug(t.getValue())
                        + ") occurs only in the specification; the implementation never sends it, which the join"
                        + " does not count against it"));
            } else if (!ia.containsAll(t.getValue())) {
                failures.add(new Item("C13", "the tag " + tag + " has the field arities " + debug(t.getValue())
                        + " in the specification and " + debug(ia) + " in the implementation"));
            }
        }
        for (Map.Entry<String, SortedSet<Integer>> t : new TreeMap<>(programTags).entrySet()) {
            if (!specTags.containsKey(t.getKey())) {
                warnings.add(new Item("C13", "the tag " + quoted(t.getKey()) + " (arities " + debug(t.getValue())
                        + ") occurs only in the implementation; the join reports the lines that carry it as"
                        + " uncovered"));
            }
        }
        return new Report(failures, warnings);
    }

    /**
     * The tags of the messages a program can send, with their field arities (see the class comment).
     *
     * @param programClass the program class
     * @return tag → arities, in byte order of the tags
     */
    public static Map<String, SortedSet<Integer>> programTags(Class<?> programClass) {
        Map<String, SortedSet<Integer>> tags = new TreeMap<>(MpValueEncoder::compareBytes);
        Deque<Type> todo = new ArrayDeque<>(messageClasses(programClass));
        Set<Type> done = new HashSet<>();
        while (!todo.isEmpty()) {
            Type t = todo.pop();
            if (!done.add(t)) {
                continue;
            }
            if (t instanceof ParameterizedType p) {
                if (p.getRawType() == Optional.class) {
                    tags.computeIfAbsent("None", k -> new TreeSet<>()).add(0);
                    tags.computeIfAbsent("Some", k -> new TreeSet<>()).add(1);
                }
                if (p.getRawType() == Optional.class || p.getRawType() instanceof Class<?> r
                        && Collection.class.isAssignableFrom(r)) {
                    todo.addAll(List.of(p.getActualTypeArguments()));
                }
                continue;
            }
            if (!(t instanceof Class<?> c)) {
                continue;
            }
            if (c.isRecord()) {
                Tag tag = c.getAnnotation(Tag.class);
                if (tag != null) {
                    tags.computeIfAbsent(tag.value(), k -> new TreeSet<>()).add(c.getRecordComponents().length);
                }
                for (RecordComponent rc : c.getRecordComponents()) {
                    todo.add(rc.getGenericType());
                }
            } else if (c.isEnum()) {
                for (Object e : c.getEnumConstants()) {
                    tags.computeIfAbsent(((Enum<?>) e).name(), k -> new TreeSet<>()).add(0);
                }
            } else if (c.isSealed()) {
                todo.addAll(List.of(c.getPermittedSubclasses()));
            }
        }
        return tags;
    }

    /** The records and enums implementing {@link MpMessage} in the program class's package and classpath entry. */
    private static List<Class<?>> messageClasses(Class<?> programClass) {
        List<Class<?>> out = new ArrayList<>();
        String pkg = programClass.getPackageName();
        String dir = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
        CodeSource cs = programClass.getProtectionDomain().getCodeSource();
        if (cs == null || cs.getLocation() == null) {
            return out;
        }
        List<String> names = new ArrayList<>();
        try {
            Path root = Path.of(cs.getLocation().toURI());
            if (Files.isDirectory(root)) {
                Path d = root.resolve(dir);
                if (Files.isDirectory(d)) {
                    try (Stream<Path> s = Files.list(d)) {
                        s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".class"))
                                .forEach(n -> names.add(dir + n));
                    }
                }
            } else if (Files.isRegularFile(root)) {
                try (JarFile jar = new JarFile(root.toFile())) {
                    for (JarEntry e : java.util.Collections.list(jar.entries())) {
                        String n = e.getName();
                        if (n.startsWith(dir) && n.endsWith(".class") && n.indexOf('/', dir.length()) < 0) {
                            names.add(n);
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            return out;
        }
        names.sort(null);
        for (String n : names) {
            String cn = n.substring(0, n.length() - ".class".length()).replace('/', '.');
            try {
                Class<?> c = Class.forName(cn, false, programClass.getClassLoader());
                if ((c.isRecord() || c.isEnum()) && MpMessage.class.isAssignableFrom(c)) {
                    out.add(c);
                }
            } catch (ClassNotFoundException | LinkageError e) {
                // A class that cannot be loaded cannot be sent.
            }
        }
        return out;
    }

    /** The specification's two lists, by name, those present. */
    private static Map<String, List<String>> listsOf(List<String> visible, List<String> roles) {
        Map<String, List<String>> m = new java.util.LinkedHashMap<>();
        if (visible != null) {
            m.put("visible", visible);
        }
        if (roles != null) {
            m.put("roles", roles);
        }
        return m;
    }

    /** Records every tag of {@code v} with its arity, and warns about tags that name an identity type (C18). */
    private static void walk(JsonElement v, Map<String, SortedSet<Integer>> tags,
                             java.util.function.Consumer<String> c18) {
        if (v.isJsonArray()) {
            for (JsonElement x : v.getAsJsonArray()) {
                walk(x, tags, c18);
            }
        } else if (v.isJsonObject()) {
            JsonObject o = v.getAsJsonObject();
            JsonElement t = o.get("t");
            JsonElement f = o.get("f");
            if (t != null && t.isJsonPrimitive() && t.getAsJsonPrimitive().isString() && f != null
                    && f.isJsonArray()) {
                String name = t.getAsString();
                if (name.equals("ThreadId") || name.equals("Tid")) {
                    c18.accept("a value is tagged " + quoted(name) + ", the name of an identity type; if it is a"
                            + " process identity, the programs must send its role name instead");
                }
                JsonArray fields = f.getAsJsonArray();
                tags.computeIfAbsent(name, k -> new TreeSet<>()).add(fields.size());
                for (JsonElement x : fields) {
                    walk(x, tags, c18);
                }
            }
        }
    }

    /** A line as a JSON object, or {@code null} when it is not JSON or not an object. */
    private static JsonObject object(String line) {
        try (JsonReader r = new JsonReader(new StringReader(line))) {
            JsonElement e = JSON.read(r);
            return e != null && e.isJsonObject() && r.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT
                    ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** A non-negative integer literal's value, else {@code null}. */
    private static BigInteger unsigned(JsonElement c) {
        if (!c.isJsonPrimitive() || !c.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        String lit = c.getAsJsonPrimitive().getAsNumber().toString();
        return INTEGER.matcher(lit).matches() && !lit.startsWith("-") ? new BigInteger(lit) : null;
    }

    private static String quoted(String s) {
        StringBuilder sb = new StringBuilder();
        MpValueEncoder.quote(sb, s);
        return sb.toString();
    }

    /** A list of names as TraceForge prints it: {@code ["a", "b"]}. */
    private static String debug(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"');
            for (char ch : l.get(i).toCharArray()) {
                switch (ch) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(ch);
                }
            }
            sb.append('"');
        }
        return sb.append(']').toString();
    }

    /** A set of arities as TraceForge prints it: {@code {1, 2}}. */
    private static String debug(SortedSet<Integer> s) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Integer i : s) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(i);
        }
        return sb.append('}').toString();
    }
}
