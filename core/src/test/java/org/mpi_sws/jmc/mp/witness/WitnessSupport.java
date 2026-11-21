package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mpi_sws.jmc.mp.summary.MpValueEncoder;
import org.mpi_sws.jmc.mp.summary.Summary;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * WITNESS tester support: the launcher in a child JVM with a working directory, the launcher's
 * report fields, a parser of the witness text, and the consistency oracle (the summary re-derived
 * from a witness).
 */
final class WitnessSupport {

    static final String LAUNCHER = "org.mpi_sws.jmc.mp.cli.MpLauncher";
    static final String PKG = "org.mpi_sws.jmc.mp.witness.";
    static final String BANK = "org.mpi_sws.jmc.mp.j8s.OnlineBank";
    static final String TIMEOUT = "300";
    /** The working directories of the child runs (core/ is Gradle's test working directory). */
    static final Path ROOT = Path.of("build/test-results/witness-tester").toAbsolutePath();

    private WitnessSupport() {}

    /** A child run: its exit status, its stdout and its stderr. */
    record Out(int exit, String stdout, String stderr) {
        String text() {
            return stdout + stderr;
        }
    }

    /** A fresh empty working directory {@code ROOT/name}. */
    static Path fresh(String name) throws IOException {
        Path d = ROOT.resolve(name);
        if (Files.exists(d)) {
            try (Stream<Path> s = Files.walk(d)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        Files.createDirectories(d);
        return d;
    }

    /** The launcher with {@code args}, in working directory {@code cwd}; stdout and stderr kept apart. */
    static Out launch(Path cwd, String... args) throws Exception {
        return launch(cwd, false, args);
    }

    /**
     * The JVM-option variables that make every JVM print {@code Picked up …} on stderr (CI and the
     * regression scripts set {@code JAVA_TOOL_OPTIONS}); environment, not the launcher's report.
     */
    static final List<String> JVM_OPTION_VARIABLES = List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS");

    /** As {@link #launch(Path, String...)}; with {@code cleanJvmOptions} the child runs without {@link #JVM_OPTION_VARIABLES}. */
    static Out launch(Path cwd, boolean cleanJvmOptions, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(LAUNCHER);
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).redirectInput(ProcessBuilder.Redirect.from(
                new File("/dev/null")));
        if (cleanJvmOptions) {
            JVM_OPTION_VARIABLES.forEach(pb.environment()::remove);
        }
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread a = pump(p.getInputStream(), out);
        Thread b = pump(p.getErrorStream(), err);
        if (!p.waitFor(900, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("launcher did not finish in 900 s: " + String.join(" ", args));
        }
        a.join();
        b.join();
        return new Out(p.exitValue(), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Thread pump(InputStream in, ByteArrayOutputStream to) {
        Thread t = new Thread(() -> {
            try (in) {
                in.transferTo(to);
            } catch (IOException e) {
                // the child is gone
            }
        });
        t.start();
        return t;
    }

    /** The value of the report line {@code key:} (the first one), or {@code null}. */
    static String field(String text, String key) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s+(.*)$").matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    /** The iteration of the {@code at:} line. */
    static int atIteration(String text) {
        String at = field(text, "at");
        Matcher m = Pattern.compile("^iteration (\\d+), ").matcher(at == null ? "" : at);
        assertTrue(m.find(), "at: " + at + "\n" + text);
        return Integer.parseInt(m.group(1));
    }

    /**
     * The complete must export of {@code program} into {@code dir/full.jsonl}, then the specification
     * {@code dir/<name>}: the header and the export's body lines {@code keep} (1-based), each passed
     * through {@code edit} (identity for none).
     */
    static Path spec(Path dir, String program, String name, java.util.function.UnaryOperator<String> edit,
            int... keep) throws Exception {
        Path full = dir.resolve("full-" + name);
        Out o = launch(dir, program, "must", "0", "1", "rep-export", full.toString(), TIMEOUT);
        assertEquals(0, o.exit(), o.text());
        List<String> lines = Files.readAllLines(full, StandardCharsets.UTF_8);
        assertTrue(lines.get(0).contains("\"enumeration\":\"complete\""), lines.get(0));
        List<String> out = new ArrayList<>();
        out.add(lines.get(0));
        for (int k : keep) {
            out.add(edit.apply(lines.get(k)));
        }
        Path s = dir.resolve(name);
        Files.write(s, out, StandardCharsets.UTF_8);
        return s;
    }

    /**
     * As {@link #spec}, keeping exactly one body line: the one matching {@code keep} (asserted unique).
     */
    static Path specWhere(Path dir, String program, String name, java.util.function.Predicate<String> keep)
            throws Exception {
        Path full = dir.resolve("full-" + name);
        Out o = launch(dir, program, "must", "0", "1", "rep-export", full.toString(), TIMEOUT);
        assertEquals(0, o.exit(), o.text());
        List<String> lines = Files.readAllLines(full, StandardCharsets.UTF_8);
        assertTrue(lines.get(0).contains("\"enumeration\":\"complete\""), lines.get(0));
        List<String> kept = lines.subList(1, lines.size()).stream().filter(keep).toList();
        assertEquals(1, kept.size(), "one line kept of " + lines);
        Path s = dir.resolve(name);
        Files.write(s, List.of(lines.get(0), kept.get(0)), StandardCharsets.UTF_8);
        return s;
    }

    /** The witness files {@code witness-1.txt} … of {@code dir}, checked to be exactly those, in n order. */
    static List<String> witnessFiles(Path dir, int expected) throws IOException {
        List<String> names;
        try (Stream<Path> s = Files.list(dir)) {
            names = s.map(p -> p.getFileName().toString()).sorted().toList();
        }
        Set<String> want = new HashSet<>();
        for (int i = 1; i <= expected; i++) {
            want.add("witness-" + i + ".txt");
        }
        assertEquals(want, new HashSet<>(names), "files of " + dir);
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= expected; i++) {
            texts.add(Files.readString(dir.resolve("witness-" + i + ".txt"), StandardCharsets.UTF_8));
        }
        return texts;
    }

    // ---------------------------------------------------------------- the witness text

    /** One event line: thread position {@code t}, index {@code k}, the text after {@code ": "}. */
    record Ev(int t, int k, String body) {}

    /** One thread block; {@code daemon} if its header carries the daemon marker. */
    record Th(String name, int t, boolean daemon, List<Ev> events) {}

    /** A parsed witness. */
    record Witness(int iteration, String summaryLine, List<Th> threads) {
        Ev event(int t, int k) {
            for (Th th : threads) {
                if (th.t() == t) {
                    return k < th.events().size() ? th.events().get(k) : null;
                }
            }
            return null;
        }
    }

    private static final Pattern FIRST = Pattern.compile("^uncovered summary \\(iteration (\\d+)\\): (.*)$");
    private static final Pattern THREAD = Pattern.compile("^thread \"(.*)\"\\[tid=(\\d+)(, daemon)?\\]:$");
    private static final Pattern EVENT = Pattern.compile("^\\t\\((\\d+), (\\d+)\\): (.*)$");
    /** A receive that reads a send: the send's thread {@code t} and index {@code k}, then its thread's name. */
    static final Pattern RECV = Pattern.compile("^RECV\\(\\) \\[\\((\\d+), (\\d+)\\) (.*)\\]$");
    private static final Pattern TJOIN = Pattern.compile("^TJOIN\\((\\d+)\\)$");
    private static final Pattern TCREATE = Pattern.compile("^TCREATE\\((\\d+):\"(.*)\"\\)$");

    /** A SEND line's destination name and value. */
    record Send(String destination, String value) {}

    /**
     * A SEND line split at its destination: the longest thread name {@code n} of the witness with the
     * body {@code SEND(n, <value>)} (a name may hold commas and quotes); {@code null} if not a SEND.
     */
    static Send send(Witness w, String body) {
        if (!body.startsWith("SEND(") || !body.endsWith(")")) {
            return null;
        }
        String best = null;
        for (Th th : w.threads()) {
            String n = th.name();
            if (body.startsWith("SEND(" + n + ", ") && body.length() > ("SEND(" + n + ", )").length() - 1
                    && (best == null || n.length() > best.length())) {
                best = n;
            }
        }
        assertTrue(best != null, "the destination of " + body + " is a thread of the witness");
        return new Send(best, body.substring(("SEND(" + best + ", ").length(), body.length() - 1));
    }

    /**
     * Parses a witness and checks its shape: every line ends in a newline; threads numbered 0, 1, …
     * in order, main first; per thread {@code k} = 0, 1, … with {@code t} the thread's; {@code BEGIN}
     * first; exactly one of {@code END}, {@code BLK}, {@code ERROR(..)}, last; every line one of the
     * format's kinds.
     */
    static Witness parse(String text) {
        assertTrue(text.endsWith("\n"), "the text ends in a newline");
        String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
        Matcher f = FIRST.matcher(lines[0]);
        assertTrue(f.matches(), "line 1: " + lines[0]);
        List<Th> threads = new ArrayList<>();
        Th cur = null;
        for (int i = 1; i < lines.length; i++) {
            Matcher th = THREAD.matcher(lines[i]);
            if (th.matches()) {
                cur = new Th(th.group(1), Integer.parseInt(th.group(2)), th.group(3) != null, new ArrayList<>());
                assertEquals(threads.size(), cur.t(), "thread positions in order: " + lines[i]);
                threads.add(cur);
                continue;
            }
            Matcher ev = EVENT.matcher(lines[i]);
            assertTrue(ev.matches() && cur != null, "line " + (i + 1) + ": " + lines[i]);
            Ev e = new Ev(Integer.parseInt(ev.group(1)), Integer.parseInt(ev.group(2)), ev.group(3));
            assertEquals(cur.t(), e.t(), lines[i]);
            assertEquals(cur.events().size(), e.k(), "k in order: " + lines[i]);
            String b = e.body();
            assertTrue(b.equals("BEGIN") || b.equals("END") || b.equals("BLK") || b.startsWith("ERROR(")
                    || TCREATE.matcher(b).matches() || TJOIN.matcher(b).matches() || b.startsWith("SEND(")
                    || RECV.matcher(b).matches()
                    || b.equals("RECV() [TIMEOUT]") || b.matches("NONDET \\d+"), "kind: " + lines[i]);
            cur.events().add(e);
        }
        assertTrue(!threads.isEmpty() && threads.get(0).name().equals("main"), "main first");
        for (Th th : threads) {
            List<Ev> es = th.events();
            assertTrue(es.size() >= 2, "BEGIN and an ending: " + th);
            assertEquals("BEGIN", es.get(0).body(), th.toString());
            for (int j = 1; j < es.size(); j++) {
                String b = es.get(j).body();
                boolean ending = b.equals("END") || b.equals("BLK") || b.startsWith("ERROR(");
                assertEquals(j == es.size() - 1, ending, "one ending, last: " + th);
                assertTrue(!b.equals("BEGIN"), "one BEGIN: " + th);
            }
        }
        Witness w = new Witness(Integer.parseInt(f.group(1)), f.group(2), threads);
        // TCREATE(t:"name") names the thread t of that name; each non-main thread is created exactly once,
        // and the creations in text order are threads 1, 2, ... (thread order is creation order).
        int next = 1;
        for (Th th : threads) {
            for (Ev e : th.events()) {
                Matcher c = TCREATE.matcher(e.body());
                if (c.matches()) {
                    int child = Integer.parseInt(c.group(1));
                    assertEquals(next, child, "creations in thread order: " + e);
                    assertTrue(child < threads.size(), "created thread exists: " + e);
                    assertEquals(threads.get(child).name(), c.group(2), "TCREATE names the child's header: " + e);
                    next++;
                }
                if (e.body().startsWith("SEND(")) {
                    assertTrue(send(w, e.body()) != null, "a SEND line: " + e);
                }
                Matcher j = TJOIN.matcher(e.body());
                if (j.matches()) {
                    assertTrue(Integer.parseInt(j.group(1)) < threads.size(), "joined thread exists: " + e);
                }
            }
        }
        assertEquals(threads.size(), next, "every thread but main is created once");
        return w;
    }

    /**
     * Every {@code RECV() [(t, k) name]} names an event of the witness that is a {@code SEND}, and {@code name} is
     * thread {@code t}'s; returns how many.
     */
    static int recvsNameSends(Witness w) {
        int n = 0;
        Set<String> read = new HashSet<>();
        for (Th th : w.threads()) {
            for (Ev e : th.events()) {
                Matcher m = RECV.matcher(e.body());
                if (m.matches()) {
                    Ev s = w.event(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                    assertTrue(s != null && s.body().startsWith("SEND("),
                            "(" + e.t() + ", " + e.k() + ") reads (" + m.group(1) + ", " + m.group(2) + "), " + s);
                    assertEquals(w.threads().get(Integer.parseInt(m.group(1))).name(), m.group(3),
                            "the receive names the sender's thread: " + e);
                    assertEquals(th.name(), send(w, s.body()).destination(),
                            "the send read by (" + e.t() + ", " + e.k() + ") is addressed to its thread");
                    assertTrue(read.add(m.group(1) + "," + m.group(2)), "a send read twice: " + e);
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * The consistency oracle: the summary re-derived from the witness text alone — per visible role
     * (a thread of that name) the status (BLK blocked, ERROR err, else done), the word (SEND → s with
     * the printed value, RECV → r with the value of the send it names, TIMEOUT → r null) and the counts
     * (for each visible event, per other visible role in order, the number of that role's visible
     * events before it in the transitive closure of program order and receive-from) — as a format
     * line with multiplicity 1. The order includes the spawn edges (a thread's {@code BEGIN} after the
     * {@code TCREATE} that created it) and the join edges (a {@code TJOIN} after the joined thread's last line).
     */
    static String rederive(Witness w, Collection<String> visibleRoles) {
        List<String> roles = MpValueEncoder.sortedRoles(visibleRoles);
        Map<String, Th> byName = new HashMap<>();
        for (Th th : w.threads()) {
            byName.put(th.name(), th);
        }
        Map<String, String> statuses = new LinkedHashMap<>();
        Map<String, List<String>> words = new LinkedHashMap<>();
        Map<String, List<Ev>> visible = new LinkedHashMap<>();
        for (String r : roles) {
            Th th = byName.get(r);
            assertTrue(th != null, "visible role " + r + " is a thread of the witness");
            String last = th.events().get(th.events().size() - 1).body();
            statuses.put(r, last.equals("BLK") ? "blocked" : last.startsWith("ERROR(") ? "err" : "done");
            List<String> word = new ArrayList<>();
            List<Ev> evs = new ArrayList<>();
            for (Ev e : th.events()) {
                Send s = send(w, e.body());
                Matcher rv = RECV.matcher(e.body());
                if (s != null) {
                    word.add("{\"s\":" + s.value() + "}");
                    evs.add(e);
                } else if (rv.matches()) {
                    Send src = send(w, w.event(Integer.parseInt(rv.group(1)), Integer.parseInt(rv.group(2))).body());
                    word.add("{\"r\":" + src.value() + "}");
                    evs.add(e);
                } else if (e.body().equals("RECV() [TIMEOUT]")) {
                    word.add("{\"r\":null}");
                    evs.add(e);
                }
            }
            words.put(r, word);
            visible.put(r, evs);
        }
        List<Integer> counts = new ArrayList<>();
        for (String ri : roles) {
            for (Ev e : visible.get(ri)) {
                Set<String> before = before(w, e);
                for (String rj : roles) {
                    if (rj.equals(ri)) {
                        continue;
                    }
                    int c = 0;
                    for (Ev a : visible.get(rj)) {
                        if (before.contains(a.t() + "," + a.k())) {
                            c++;
                        }
                    }
                    counts.add(c);
                }
            }
        }
        return new Summary(roles, statuses, words, counts).toJson(1);
    }

    /** The events strictly before {@code e} in (po ∪ rf)+, as "t,k". */
    private static Set<String> before(Witness w, Ev e) {
        Set<String> seen = new HashSet<>();
        List<Ev> todo = new ArrayList<>(preds(w, e));
        while (!todo.isEmpty()) {
            Ev x = todo.remove(todo.size() - 1);
            if (seen.add(x.t() + "," + x.k())) {
                todo.addAll(preds(w, x));
            }
        }
        return seen;
    }

    private static List<Ev> preds(Witness w, Ev e) {
        List<Ev> p = new ArrayList<>();
        if (e.k() > 0) {
            p.add(w.event(e.t(), e.k() - 1));
        } else {
            // A thread's BEGIN comes after the TCREATE that created it.
            for (Th th : w.threads()) {
                for (Ev c : th.events()) {
                    Matcher m = TCREATE.matcher(c.body());
                    if (m.matches() && Integer.parseInt(m.group(1)) == e.t()) {
                        p.add(c);
                    }
                }
            }
        }
        Matcher m = RECV.matcher(e.body());
        if (m.matches()) {
            p.add(w.event(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        Matcher j = TJOIN.matcher(e.body());
        if (j.matches()) {
            List<Ev> joined = w.threads().get(Integer.parseInt(j.group(1))).events();
            p.add(joined.get(joined.size() - 1));
        }
        return p;
    }

    /** Parses, checks the receive references and the oracle; returns the parsed witness. */
    static Witness checkConsistent(String text, Collection<String> visibleRoles) {
        Witness w = parse(text);
        recvsNameSends(w);
        assertEquals(w.summaryLine(), rederive(w, visibleRoles), "the summary re-derived from the witness\n" + text);
        return w;
    }

    /** Joins lines, each ending in a newline. */
    static String lines(String... ls) {
        StringBuilder sb = new StringBuilder();
        for (String l : ls) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }
}
