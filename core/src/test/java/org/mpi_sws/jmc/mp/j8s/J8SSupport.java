package org.mpi_sws.jmc.mp.j8s;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * J8S tester round 02: fixtures, spec files and comparisons for the online check under {@code --split}
 * (J8R-PLAN revision 7 §2.12, §5 rows 15–16; the tester's round-01 derivation,
 * plan/jmc-must/log/dev/J8S/round-01.report.md). Launches go through {@code org.mpi_sws.jmc.mp.j8.J8Launch}
 * (reflection: it is package-private in another package).
 */
final class J8SSupport {

    static final String BANK = "org.mpi_sws.jmc.mp.j8s.OnlineBank:2";
    static final String J4 = "org.mpi_sws.jmc.mp.programs.j4.";
    static final Path DIR = Path.of("build/test-results/j8s-tester");

    private J8SSupport() {}

    /** A launch result (mirror of J8Launch.Out). */
    record Out(int exit, String text, Path exportFile) {

        String field(String key) {
            Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s+(.*)$").matcher(text);
            return m.find() ? m.group(1).trim() : null;
        }

        Map<String, String> block() {
            Map<String, String> m = new TreeMap<>();
            int at = text.indexOf("--- measurements");
            if (at < 0) {
                return m;
            }
            for (String l : text.substring(at).split("\n")) {
                int c = l.indexOf(':');
                if (!l.startsWith("---") && c > 0 && l.substring(0, c).matches("[a-z_]+")) {
                    m.put(l.substring(0, c), l.substring(c + 1).trim());
                }
            }
            return m;
        }

        Path runDir() {
            String s = field("split");
            return s == null || !s.contains("run directory ") ? null
                    : Path.of(s.substring(s.indexOf("run directory ") + "run directory ".length()));
        }
    }

    /** Runs {@code MpLauncher} in a fresh JVM via J8Launch. */
    static Out launch(List<String> jvm, String program, boolean export, String... extra) throws Exception {
        Class<?> c = Class.forName("org.mpi_sws.jmc.mp.j8.J8Launch");
        Method m = c.getDeclaredMethod("launch", List.class, String.class, boolean.class, String[].class);
        m.setAccessible(true);
        Object o = m.invoke(null, jvm, program, export, extra);
        Class<?> oc = o.getClass();
        Method exit = oc.getDeclaredMethod("exit");
        Method text = oc.getDeclaredMethod("text");
        Method ef = oc.getDeclaredMethod("exportFile");
        exit.setAccessible(true);
        text.setAccessible(true);
        ef.setAccessible(true);
        return new Out((int) exit.invoke(o), (String) text.invoke(o), (Path) ef.invoke(o));
    }

    static Out launch(String program, boolean export, String... extra) throws Exception {
        return launch(List.of(), program, export, extra);
    }

    /**
     * OnlineBank:2's twelve graphs, by sequential position (round-01 observation): the server's words
     * Stop(k), Go, then the three values in receive order.
     */
    static String bankWords(int iteration) {
        int[][] order = {{10, 1, 2}, {1, 10, 2}, {1, 2, 10}};
        int i = iteration - 1;
        int b = i < 6 ? 10 : 20;
        int k = (i % 6) < 3 ? 0 : 1;
        int[] o = order[i % 3];
        StringBuilder s = new StringBuilder("{\"r\":{\"t\":\"Stop\",\"f\":[" + k + "]}},{\"s\":{\"t\":\"Go\",\"f\":[]}}");
        for (int v : o) {
            s.append(",{\"r\":{\"t\":\"V\",\"f\":[").append(v == 10 ? b : v).append("]}}");
        }
        return s.toString();
    }

    /** The complete sequential export of OnlineBank:2 (written once per JVM). */
    static synchronized List<String> bankExport() throws Exception {
        if (bankExport == null) {
            Out o = launch(BANK, true);
            if (o.exit() != 0) {
                throw new IllegalStateException(o.text());
            }
            bankExport = Files.readAllLines(o.exportFile(), StandardCharsets.UTF_8);
        }
        return bankExport;
    }

    private static List<String> bankExport;

    /** The line of the given sequential iteration in OnlineBank:2's export (with its "n"). */
    static String bankLine(int iteration) throws Exception {
        String w = bankWords(iteration);
        String found = null;
        for (String l : bankExport().subList(1, bankExport().size())) {
            if (l.contains(w)) {
                if (found != null) {
                    throw new IllegalStateException("two lines for iteration " + iteration);
                }
                found = l;
            }
        }
        if (found == null) {
            throw new IllegalStateException("no line for iteration " + iteration);
        }
        return found;
    }

    /** A spec file: OnlineBank:2's export without the lines of the given iterations. */
    static Path bankSpec(String name, int... dropIterations) throws Exception {
        List<String> lines = new ArrayList<>(bankExport());
        for (int i : dropIterations) {
            if (!lines.remove(bankLine(i))) {
                throw new IllegalStateException("line of iteration " + i + " not found");
            }
        }
        Files.createDirectories(DIR);
        Path p = DIR.resolve("bank2-" + name + ".jsonl");
        Files.write(p, lines, StandardCharsets.UTF_8);
        return p;
    }

    /** A J4 row's complete sequential export, as a spec file. */
    static Path j4Spec(String row) throws Exception {
        Out o = launch(J4 + row, true);
        if (o.exit() != 0) {
            throw new IllegalStateException(o.text());
        }
        Files.createDirectories(DIR);
        Path p = DIR.resolve("spec-" + row.replaceAll("[^A-Za-z0-9]", "_") + "-" + System.nanoTime() + ".jsonl");
        Files.copy(o.exportFile(), p);
        return p;
    }

    /** The first-miss iteration of a {@code first miss:} line (sequential or split form), or null. */
    static String firstMissIteration(String firstMissLine) {
        if (firstMissLine == null) {
            return null;
        }
        Matcher m = Pattern.compile("^iteration (\\d+)").matcher(firstMissLine);
        return m.find() ? m.group(1) : firstMissLine;
    }

    /** The export file with the header's first_miss_wall_ms value blanked. */
    static String exportWithoutWallMs(Path f) throws Exception {
        if (f == null || !Files.exists(f)) {
            return "-";
        }
        return Files.readString(f, StandardCharsets.UTF_8).replaceFirst("\"first_miss_wall_ms\":\\d+", "\"first_miss_wall_ms\":_");
    }

    /**
     * Compares a split run with the sequential run of the same row and spec; returns the problems.
     * Also checks the derived values (round 01): uncovered total, distinct, first-miss iteration, verdict.
     */
    static List<String> compare(String label, Out seq, Out split, long unc, long dist, long first) throws Exception {
        List<String> bad = new ArrayList<>();
        String p = label + ": ";
        if (seq.exit() != 0 || split.exit() != 0) {
            bad.add(p + "exit " + seq.exit() + "/" + split.exit() + " " + split.field("FAILED"));
            return bad;
        }
        String verdict = unc == 0 ? "conforms" : "reports";
        String uncLine = unc + " graphs (" + dist + " distinct summaries)";
        if (!verdict.equals(seq.field("verdict")) || !uncLine.equals(seq.field("uncovered"))
                || !(first == 0 ? seq.field("first miss") == null
                        : String.valueOf(first).equals(firstMissIteration(seq.field("first miss"))))) {
            bad.add(p + "sequential differs from the derivation: " + seq.field("verdict") + " / "
                    + seq.field("uncovered") + " / " + seq.field("first miss"));
        }
        for (String k : List.of("verdict", "mode", "uncovered", "witness", "at", "lookups")) {
            if (!String.valueOf(seq.field(k)).equals(String.valueOf(split.field(k)))) {
                bad.add(p + k + ": '" + split.field(k) + "' vs sequential '" + seq.field(k) + "'");
            }
        }
        String sf = split.field("first miss");
        if (!String.valueOf(firstMissIteration(seq.field("first miss"))).equals(String.valueOf(firstMissIteration(sf)))) {
            bad.add(p + "first miss " + sf + " vs " + seq.field("first miss"));
        }
        if (sf != null && !sf.matches("iteration \\d+ \\(sequential order\\), earliest \\d+ ms \\(wall\\)")) {
            bad.add(p + "first miss line format: " + sf);
        }
        if (!exportWithoutWallMs(seq.exportFile()).equals(exportWithoutWallMs(split.exportFile()))) {
            bad.add(p + "export differs (first_miss_wall_ms aside)");
        }
        Map<String, String> a = seq.block();
        Map<String, String> b = split.block();
        for (String k : List.of("first_miss_iteration", "uncovered_total", "graphs_complete", "graphs_blocked",
                "summaries_distinct", "iterations", "stop_reason")) {
            if (!String.valueOf(a.get(k)).equals(String.valueOf(b.get(k)))) {
                bad.add(p + "block " + k + " " + b.get(k) + " vs sequential " + a.get(k));
            }
        }
        if (!"".equals(b.get("index_ms")) || !"".equals(b.get("lookup_ms"))) {
            bad.add(p + "index_ms/lookup_ms not empty: " + b.get("index_ms") + "/" + b.get("lookup_ms"));
        }
        long wall = Long.parseLong(b.get("first_miss_wall_ms"));
        long explore = Long.parseLong(b.get("explore_ms"));
        if (wall < 0 || wall > explore) {
            bad.add(p + "first_miss_wall_ms " + wall + " outside 0.." + explore);
        }
        return bad;
    }
}
