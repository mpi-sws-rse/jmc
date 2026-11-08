package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Out;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WITNESS item 4, test 2 (tester): without {@code --witness} the launcher's report is byte-identical to
 * that of the base build (main 648e9c83), except for wall-time, identity and RSS values.
 *
 * <p>The golden files (resources {@code golden/<case>.stdout}, {@code .stderr}, {@code .export}) are
 * the stdout, stderr and export file of MpLauncher <em>built from 648e9c83</em> (a detached worktree of
 * that commit, since removed), run outside JUnit with the classpath laid out as Gradle's test runtime
 * (test classes, test resources, so that {@code log4j2-test.xml} configures logging as here, then main
 * classes and resources, then the dependencies), the same arguments and the same working-directory
 * layout and no JVM-option variable in the environment (the child here also runs without
 * {@code JAVA_TOOL_OPTIONS}, {@code JDK_JAVA_OPTIONS} and {@code _JAVA_OPTIONS}, which CI sets and
 * which make the JVM print {@code Picked up …} on stderr), then passed through {@link #stdout},
 * {@link #stderr} and {@link #export} (the capture
 * program calls these methods). Both sides go through the same functions, and every byte that is not
 * listed below is compared, newlines (including the final one) included.
 *
 * <p>Masked values, and nothing else:
 * <ul>
 *   <li>wall time: the values of {@code rate:}, {@code elapsed:}, {@code wall_ms}, {@code explore_ms},
 *       {@code summarise_ms}, {@code index_ms}, {@code lookup_ms}, {@code export_ms},
 *       {@code first_miss_wall_ms} (measurement block and export header), the ms of
 *       {@code first miss: iteration I, T ms} and of {@code earliest T ms (wall)}, and the timestamp
 *       prefix of every log4j line (stdout {@code yyyy-MM-dd HH:mm:ss,SSS}, stderr ISO instant);</li>
 *   <li>identity: the identity hash in {@code observed: …@h} and the split run directory's UUID;</li>
 *   <li>RSS value: the value of {@code peak_rss_kb} (the key is kept, and on both sides the value is
 *       asserted to be a positive integer, exactly one such line per must run).</li>
 * </ul>
 *
 * <p>One structural allowance, cross-thread interleaving only: stdout is written by the main thread (the
 * report lines and the {@code [main]} log4j lines) and by the checker's other threads (the scheduler's
 * {@code [Thread-0]} log4j lines), and lines of different threads interleave in no fixed order (observed
 * between two runs of the same build). Each thread's lines are therefore compared as that thread's
 * sequence, in order: the main thread's report and log lines together in one sequence (their order is
 * the main thread's program order), and each other thread's log4j lines in their own section. Only the
 * interleaving between threads is not compared. stderr is compared in order.
 */
class WitnessByteIdenticalTest {

    private static final Pattern LOG4J = Pattern.compile("^\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d,\\d{3} (\\[([^\\]]*)\\].*)$");
    private static final Pattern ISO = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d+Z (.*)$");
    private static final Pattern RSS = Pattern.compile("(?m)^peak_rss_kb: (.*)$");
    private static final String[] TIME_KEYS = {"wall_ms", "explore_ms", "summarise_ms", "index_ms", "lookup_ms",
        "export_ms", "first_miss_wall_ms"};

    /**
     * The stdout, normalised: the main thread's lines (report lines and {@code [main]} log4j lines) in order,
     * then each other thread's log4j lines in order, threads by name (see the class comment).
     */
    static String stdout(String raw) {
        Matcher m = RSS.matcher(raw);
        int rss = 0;
        while (m.find()) {
            assertTrue(m.group(1).matches("[1-9][0-9]*"), "peak_rss_kb is a positive integer: " + m.group(1));
            rss++;
        }
        // A must run prints one measurement block with one peak_rss_kb line; an mp-random run none.
        assertEquals(raw.contains("\n--- measurements\n") ? 1 : 0, rss, "peak_rss_kb lines");
        List<String> report = new ArrayList<>();
        java.util.TreeMap<String, List<String>> others = new java.util.TreeMap<>();
        for (String line : raw.split("\n", -1)) {
            Matcher l = LOG4J.matcher(line);
            if (l.matches()) {
                if (l.group(2).equals("main")) {
                    report.add("TS " + l.group(1));
                } else {
                    others.computeIfAbsent(l.group(2), t -> new ArrayList<>()).add("TS " + l.group(1));
                }
                continue;
            }
            line = line.replaceAll("^(rate:\\s+).*$", "$1X").replaceAll("^(elapsed:\\s+).*$", "$1X")
                    .replaceAll("^(observed:\\s+.*@)[0-9a-f]+$", "$1HASH")
                    .replaceAll("split-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "split-UUID")
                    .replaceAll("^(first miss: iteration \\d+, )\\d+ ms$", "$1T ms")
                    .replaceAll("earliest \\d+ ms \\(wall\\)", "earliest T ms (wall)")
                    .replaceAll("^(peak_rss_kb: )[1-9][0-9]*$", "$1RSS");
            for (String k : TIME_KEYS) {
                line = line.replaceAll("^(" + k + ": ).*$", "$1X");
            }
            report.add(line);
        }
        // The split keeps the final empty element of a text ending in a newline, so the join restores it.
        StringBuilder sb = new StringBuilder("== thread main: report and log4j lines, in order\n");
        sb.append(String.join("\n", report));
        for (var e : others.entrySet()) {
            sb.append("\n== thread ").append(e.getKey()).append(": log4j lines, in order\n")
                    .append(String.join("\n", e.getValue()));
        }
        return sb.append("\n== end\n").toString();
    }

    /** The stderr, normalised: ISO timestamps masked, in order. */
    static String stderr(String raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\n", -1)) {
            Matcher m = ISO.matcher(line);
            out.add(m.matches() ? "TS " + m.group(1) : line);
        }
        return String.join("\n", out);
    }

    /** The export file, normalised: only first_miss_wall_ms masked. */
    static String export(String raw) {
        return raw.replaceAll("\"first_miss_wall_ms\":\\d+", "\"first_miss_wall_ms\":X");
    }

    static String resource(String name) throws IOException {
        try (InputStream in = WitnessByteIdenticalTest.class.getResourceAsStream("golden/" + name)) {
            if (in == null) {
                throw new IOException("missing golden resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Runs {@code args} in a fresh directory holding spec2.jsonl and spec3.jsonl; compares with the goldens. */
    static void same(String name, boolean export, String... args) throws Exception {
        Path dir = WitnessSupport.fresh("golden-" + name);
        for (String s : List.of("spec2.jsonl", "spec3.jsonl")) {
            Files.writeString(dir.resolve(s), resource(s), StandardCharsets.UTF_8);
        }
        Out o = WitnessSupport.launch(dir, true, args);
        assertEquals(0, o.exit(), o.text());
        assertFalse(o.stdout().contains("witnesses:"), "no witness line without --witness");
        assertEquals(resource(name + ".stdout"), stdout(o.stdout()), "stdout of " + name);
        assertEquals(resource(name + ".stderr"), stderr(o.stderr()), "stderr of " + name);
        Path out = dir.resolve("out.jsonl");
        assertEquals(export, Files.exists(out));
        if (export) {
            assertEquals(resource(name + ".export"), export(Files.readString(out, StandardCharsets.UTF_8)),
                    "export of " + name);
        }
    }

    @Test
    void withoutWitness_onlineFull_byteIdenticalToBase() throws Exception {
        same("online-full", false, WitnessSupport.BANK + ":2", "must", "0", "1", "rep", "-", "300", "--spec",
                "spec2.jsonl");
    }

    @Test
    void withoutWitness_bugFinderWithExport_byteIdenticalToBase() throws Exception {
        same("bugfinder", true, "--stop-at-first", WitnessSupport.BANK + ":3", "must", "0", "1", "rep", "out.jsonl",
                "300", "--spec", "spec3.jsonl");
    }

    @Test
    void withoutWitness_exportMode_byteIdenticalToBase() throws Exception {
        same("export-must", true, WitnessSupport.BANK + ":2", "must", "0", "1", "rep", "out.jsonl", "300");
    }

    @Test
    void withoutWitness_countingMode_byteIdenticalToBase() throws Exception {
        same("counting", false, WitnessSupport.BANK + ":3", "must", "0", "1", "rep", "-", "300");
    }

    @Test
    void withoutWitness_splitExport_byteIdenticalToBase() throws Exception {
        same("split-export", true, WitnessSupport.BANK + ":3", "must", "0", "1", "rep", "out.jsonl", "300",
                "--split", "2", "--k", "2");
    }

    @Test
    void withoutWitness_splitOnline_byteIdenticalToBase() throws Exception {
        same("split-online", true, WitnessSupport.BANK + ":3", "must", "0", "1", "rep", "out.jsonl", "300",
                "--split", "2", "--k", "2", "--spec", "spec3.jsonl");
    }
}
