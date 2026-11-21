package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * P4 tester round 01, launcher comparisons (plan/jmc-must/P4-PROPOSAL.md rev 7, §4 "Assume": identical counts,
 * graphs and export md5, sequential and {@code --split 2/3}; online check of the pair against a covering and a
 * non-covering specification; main's failed assume with a split boundary around it). Each launch is a fresh JVM
 * running {@code MpLauncher} (the J8/WITNESS pattern). <b>Node only</b> ({@code @Tag("p4-node")}): about 40
 * launches; run on a brain node, one method per invocation, with the long budget the timeouts allow.
 *
 * <p>Oracles: export files byte for byte (md5); stdout on its deterministic fields only ({@code ran},
 * {@code blocked}, {@code completed}, {@code graphs}, {@code exported} without its path; online:
 * {@code lookups}, {@code verdict}, {@code mode}, {@code uncovered}, {@code witness}, {@code at}, the first
 * miss's iteration); timing and RSS are never compared. A split run must show that the threshold was crossed
 * ({@code split:} without "no split") and that items ran ({@code items:} with at least one entry), otherwise the
 * comparison is vacuous. New-API: AssumePair and MainAssume do not compile on f3a5de93; AssumePairBaseline does,
 * and its sequential export md5 on f3a5de93 is the baseline oracle (printed as {@code P4 BASELINE}).
 */
@Tag("p4-node")
class P4LaunchTest {

    static final String LAUNCHER = "org.mpi_sws.jmc.mp.cli.MpLauncher";
    static final String PKG = "org.mpi_sws.jmc.mp.programs.p4.";
    static final Path ROOT = Path.of("build/test-results/p4-tester/launch").toAbsolutePath();
    static final String TIMEOUT = "600";

    record Out(int exit, String stdout, String stderr) {
        String field(String key) {
            Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s+(.*)$").matcher(stdout);
            return m.find() ? m.group(1).trim() : null;
        }

        String text() {
            return stdout + "\n--- stderr\n" + stderr;
        }
    }

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

    static Out launch(Path cwd, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-Xss4m");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(LAUNCHER);
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(cwd.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null"))).start();
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
        Out o = new Out(p.exitValue(), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        System.out.println("P4 LAUNCH " + String.join(" ", args) + " -> exit " + o.exit() + "\n" + o.stdout());
        return o;
    }

    private static Thread pump(InputStream in, ByteArrayOutputStream to) {
        Thread t = new Thread(() -> {
            try {
                in.transferTo(to);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        t.start();
        return t;
    }

    static String md5(Path f) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(Files.readAllBytes(f)));
    }

    /** A sequential exporting Must run. */
    static Out export(Path dir, String program, Path file, String... extra) throws Exception {
        List<String> a = new ArrayList<>(List.of(PKG + program, "must", "0", "1", dir.resolve("rep").toString(),
                file.toString(), TIMEOUT));
        a.addAll(List.of(extra));
        return launch(dir, a.toArray(new String[0]));
    }

    static final List<String> LINES = List.of("ran", "blocked", "completed", "graphs");

    /** The deterministic stdout fields of an exporting run, the export path removed. */
    static Map<String, String> fields(Out o) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : LINES) {
            m.put(k, o.field(k));
        }
        String e = o.field("exported");
        m.put("exported", e == null ? null : e.substring(0, e.lastIndexOf(" to ")));
        return m;
    }

    /** A split run's evidence that the comparison is not vacuous. */
    static void splitRan(Out o, String label) {
        String split = o.field("split");
        assertNotNull(split, label + ": split line\n" + o.text());
        assertTrue(!split.contains("no split"), label + ": the threshold was not crossed: " + split);
        String items = o.field("items");
        assertNotNull(items, label + ": items line\n" + o.text());
        Matcher m = Pattern.compile("(\\d+)=(\\d+)").matcher(items);
        int ran = 0;
        while (m.find()) {
            if (Integer.parseInt(m.group(2)) > 0) {
                ran++;
            }
        }
        assertTrue(ran > 0, label + ": no item ran: " + items);
    }

    static void seqAndSplitIdentical(String name, List<String> programs, String graphs) throws Exception {
        Path dir = fresh(name);
        Map<String, String> want = null;
        String md5 = null;
        List<String> bad = new ArrayList<>();
        for (String program : programs) {
            String tag = program.replaceAll("[^A-Za-z0-9]", "_");
            List<String[]> modes = List.of(new String[] {"seq"}, new String[] {"W2", "--split", "2", "--k", "1"},
                    new String[] {"W3", "--split", "3", "--k", "1"});
            for (String[] mode : modes) {
                String label = program + " " + mode[0];
                Path file = dir.resolve(tag + "-" + mode[0] + ".jsonl");
                String[] extra = java.util.Arrays.copyOfRange(mode, 1, mode.length);
                Out o = export(dir, program, file, extra);
                if (o.exit() != 0) {
                    bad.add(label + ": exit " + o.exit() + " " + o.field("FAILED"));
                    continue;
                }
                if (!mode[0].equals("seq")) {
                    splitRan(o, label);
                }
                Map<String, String> f = fields(o);
                for (Map.Entry<String, String> e : f.entrySet()) {
                    if (e.getValue() == null) {
                        bad.add(label + ": field " + e.getKey() + " missing");
                    }
                }
                if (!graphs.equals(f.get("graphs"))) {
                    bad.add(label + ": graphs " + f.get("graphs") + ", derived " + graphs);
                }
                // Round 03 (Codex code review 01, P3): every launch of this comparison exports, so its file must
                // exist and be non-empty before it is hashed; a missing file is a failure, never a "-" that would
                // compare equal to another missing file.
                if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                    bad.add(label + ": export file missing or empty: " + file);
                    continue;
                }
                String m = md5(file);
                System.out.println("P4 " + (program.startsWith("AssumePairBaseline") ? "BASELINE " : "") + label
                        + " export " + m + " " + f);
                if (want == null) {
                    want = f;
                    md5 = m;
                } else {
                    if (!want.equals(f)) {
                        bad.add(label + ": fields " + f + " vs " + want);
                    }
                    if (!md5.equals(m)) {
                        bad.add(label + ": export md5 " + m + " vs " + md5);
                    }
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * The pair, sequential and --split 2/3 (K 1): identical deterministic fields and export md5 for assume, the
     * workaround and the baseline-API program; graphs derived 2 complete, 3 blocked.
     */
    @Test
    @Timeout(3600)
    void pair_sequentialAndSplit_identicalFieldsAndExport() throws Exception {
        seqAndSplitIdentical("pair", List.of("AssumePairBaseline:recv", "AssumePair:recv", "AssumePair:assume"),
                "2 distinct complete, 3 distinct blocked; stack exhausted");
    }

    /** MainAssume late and early, sequential and --split 2/3 (K 1): main's failed assume around a boundary. */
    @Test
    @Timeout(3600)
    void mainAssume_sequentialAndSplit_identical() throws Exception {
        seqAndSplitIdentical("main-late", List.of("MainAssume:late"),
                "1 distinct complete, 2 distinct blocked; stack exhausted");
        seqAndSplitIdentical("main-early", List.of("MainAssume:early"),
                "0 distinct complete, 3 distinct blocked; stack exhausted");
    }

    static final List<String> ONLINE = List.of("lookups", "verdict", "mode", "uncovered", "witness", "at");

    static Map<String, String> onlineFields(Out o) {
        Map<String, String> m = new LinkedHashMap<>(fields(o));
        m.remove("exported");
        for (String k : ONLINE) {
            m.put(k, o.field(k));
        }
        String fm = o.field("first miss");
        m.put("first miss", fm == null ? null : fm.substring(0, fm.indexOf(',') < 0 ? fm.length() : fm.indexOf(',')));
        return m;
    }

    /**
     * The specifications: covering = the complete export of AssumePair:recv; non-covering = its header and its
     * body lines without a blocked status (so every blocked summary is uncovered, and its witness ends in BLK).
     */
    static Path[] specs(Path dir) throws Exception {
        Path full = dir.resolve("full.jsonl");
        Out o = export(dir, "AssumePair:recv", full);
        assertEquals(0, o.exit(), o.text());
        List<String> lines = Files.readAllLines(full, StandardCharsets.UTF_8);
        assertTrue(lines.get(0).contains("\"enumeration\":\"complete\""), lines.get(0));
        List<String> partial = new ArrayList<>();
        partial.add(lines.get(0));
        for (String l : lines.subList(1, lines.size())) {
            if (!l.contains("\"blocked\"")) {
                partial.add(l);
            }
        }
        assertTrue(partial.size() > 1 && partial.size() < lines.size(), "a non-covering subset: " + partial);
        Path cover = dir.resolve("cover.jsonl");
        Path part = dir.resolve("part.jsonl");
        Files.write(cover, lines, StandardCharsets.UTF_8);
        Files.write(part, partial, StandardCharsets.UTF_8);
        return new Path[] {cover, part};
    }

    /**
     * Online check of the pair (sequential, full mode, file-mode witness): against the covering spec both conform
     * with 0 uncovered; against the non-covering spec both report, with identical fields and witness bytes, and
     * the witness holds a BLK line (r's final position). Bug-finder mode (--stop-at-first): identical fields.
     */
    @Test
    @Timeout(3600)
    void pair_online_coveringAndNonCovering_identical() throws Exception {
        Path dir = fresh("online");
        Path[] spec = specs(dir);
        List<String> bad = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            for (boolean stop : new boolean[] {false, true}) {
                Map<String, String> want = null;
                byte[] wantWitness = null;
                for (String v : List.of("recv", "assume")) {
                    String label = (s == 0 ? "cover" : "part") + (stop ? " stop" : " full") + " " + v;
                    Path w = dir.resolve("w-" + s + (stop ? "-stop-" : "-full-") + v + ".txt");
                    List<String> a = new ArrayList<>(List.of(PKG + "AssumePair:" + v, "must", "0", "1",
                            dir.resolve("rep").toString(), "-", TIMEOUT, "--spec", spec[s].toString(), "--witness",
                            w.toString()));
                    if (stop) {
                        a.add("--stop-at-first");
                    }
                    Out o = launch(dir, a.toArray(new String[0]));
                    if (o.exit() != 0) {
                        bad.add(label + ": exit " + o.exit() + " " + o.field("FAILED"));
                        continue;
                    }
                    Map<String, String> f = onlineFields(o);
                    if (!stop) {
                        // Round 04: the full run's fields present with their derived values (bug-finder mode stops
                        // early, so its counts are only compared between the variants).
                        for (Map.Entry<String, String> e : expectedOnline(s == 0).entrySet()) {
                            if (!e.getValue().equals(f.get(e.getKey()))) {
                                bad.add(label + ": " + e.getKey() + " '" + f.get(e.getKey()) + "', derived '"
                                        + e.getValue() + "'");
                            }
                        }
                    } else if (f.get("verdict") == null || f.get("uncovered") == null || f.get("lookups") == null) {
                        bad.add(label + ": a required field is missing: " + f);
                    }
                    if (s == 0) {
                        if (!"conforms".equals(f.get("verdict"))) {
                            bad.add(label + ": verdict " + f.get("verdict"));
                        }
                        if (!"0 graphs (0 distinct summaries)".equals(f.get("uncovered"))) {
                            bad.add(label + ": uncovered " + f.get("uncovered"));
                        }
                    } else {
                        if (!"reports".equals(f.get("verdict"))) {
                            bad.add(label + ": verdict " + f.get("verdict"));
                        }
                        if (!Files.exists(w)) {
                            bad.add(label + ": no witness file");
                        } else if (!Files.readString(w, StandardCharsets.UTF_8).contains("BLK")) {
                            bad.add(label + ": the witness has no BLK line");
                        }
                    }
                    // A witness file is expected only when something is uncovered (the non-covering spec); with the
                    // covering spec none may be written. Its absence there is the expected state, checked here.
                    if (s == 0 && Files.exists(w)) {
                        bad.add(label + ": a witness file was written although everything is covered");
                    }
                    if (s == 1 && (!Files.isRegularFile(w) || Files.size(w) == 0)) {
                        bad.add(label + ": witness file missing or empty");
                        continue;
                    }
                    byte[] wb = s == 1 ? Files.readAllBytes(w) : new byte[0];
                    if (want == null) {
                        want = f;
                        wantWitness = wb;
                    } else {
                        if (!want.equals(f)) {
                            bad.add(label + ": fields " + f + " vs " + want);
                        }
                        if (!java.util.Arrays.equals(wantWitness, wb)) {
                            bad.add(label + ": witness bytes differ");
                        }
                    }
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * The derived full-run online fields of AssumePair (either variant). 5 executions, each summarised and
     * looked up once. Covering spec (the full export): all 5 found, nothing uncovered. Non-covering spec (the
     * export without its blocked lines): the 2 complete summaries are found; the 3 blocked ones (r reads V(3)
     * first; V(1) then V(3); V(2) then V(3)) are distinct and their signatures (statuses with r blocked) are held
     * by no spec line, so 3 signature misses, 3 uncovered graphs, 3 distinct summaries.
     */
    static Map<String, String> expectedOnline(boolean covering) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ran", "5 iterations");
        m.put("graphs", "2 distinct complete, 3 distinct blocked; stack exhausted");
        m.put("verdict", covering ? "conforms" : "reports");
        m.put("uncovered", covering ? "0 graphs (0 distinct summaries)" : "3 graphs (3 distinct summaries)");
        m.put("lookups", covering ? "5 (5 found, 0 signature misses, 0 not found)"
                : "5 (2 found, 3 signature misses, 0 not found)");
        return m;
    }

    /** Online check under --split 2/3 (K 1, full mode; witnesses are refused with split): identical fields. */
    @Test
    @Timeout(3600)
    void pair_online_split_identical() throws Exception {
        Path dir = fresh("online-split");
        Path[] spec = specs(dir);
        List<String> bad = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            for (String w : List.of("2", "3")) {
                Map<String, String> want = null;
                for (String v : List.of("recv", "assume")) {
                    String label = (s == 0 ? "cover" : "part") + " W" + w + " " + v;
                    Out o = launch(dir, PKG + "AssumePair:" + v, "must", "0", "1", dir.resolve("rep").toString(), "-",
                            TIMEOUT, "--spec", spec[s].toString(), "--split", w, "--k", "1");
                    if (o.exit() != 0) {
                        bad.add(label + ": exit " + o.exit() + " " + o.field("FAILED"));
                        continue;
                    }
                    splitRan(o, label);
                    Map<String, String> f = new LinkedHashMap<>();
                    for (String k : List.of("ran", "graphs", "verdict", "uncovered", "lookups")) {
                        f.put(k, o.field(k));
                    }
                    // Round 04 (Codex code review 02, P3): every field present with its derived value before the
                    // variants are compared, so two runs that both omit a field cannot pass.
                    Map<String, String> expected = expectedOnline(s == 0);
                    for (Map.Entry<String, String> e : expected.entrySet()) {
                        if (!e.getValue().equals(f.get(e.getKey()))) {
                            bad.add(label + ": " + e.getKey() + " '" + f.get(e.getKey()) + "', derived '" + e.getValue()
                                    + "'");
                        }
                    }
                    if (want == null) {
                        want = f;
                    } else if (!want.equals(f)) {
                        bad.add(label + ": fields " + f + " vs " + want);
                    }
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    // ------------------------------------------------------------------ round 04 (Codex code review 02, P2)

    /** A summary line without its multiplicity. */
    static String withoutN(String line) {
        return line.replaceAll(",\"n\":\\d+}$", "}");
    }

    /**
     * The mp-random exporter through the launcher: MainAssume early and late, AssumePair:assume, 200 iterations
     * at seed 1, with an export file. Derived: exit 0; the file is a non-empty regular file whose header says
     * {@code "sampled"}; stdout has no "main performs a receive" and no "never awaited"; the body lines without
     * their multiplicities equal the Must export's as a set (fixed seed: deterministic). Fails before the lead's
     * recorder fix.
     */
    @Test
    @Timeout(3600)
    void recorder_export_mainAssumeAndPair_equalsMust() throws Exception {
        Path dir = fresh("recorder");
        List<String> bad = new ArrayList<>();
        for (String program : List.of("MainAssume:early", "MainAssume:late", "AssumePair:assume")) {
            String tag = program.replaceAll("[^A-Za-z0-9]", "_");
            Path rf = dir.resolve(tag + "-random.jsonl");
            Path mf = dir.resolve(tag + "-must.jsonl");
            Out r = launch(dir, PKG + program, "mp-random", "200", "1", dir.resolve("rep").toString(), rf.toString(),
                    TIMEOUT);
            Out m = export(dir, program, mf);
            if (r.exit() != 0 || m.exit() != 0) {
                bad.add(program + ": exit " + r.exit() + "/" + m.exit() + " " + r.field("FAILED") + " / "
                        + m.field("FAILED"));
                continue;
            }
            if (r.text().contains("main performs a receive") || r.text().contains("never awaited")) {
                bad.add(program + ": a refusal in the output");
            }
            if (!Files.isRegularFile(rf) || Files.size(rf) == 0 || !Files.isRegularFile(mf) || Files.size(mf) == 0) {
                bad.add(program + ": export file missing or empty");
                continue;
            }
            List<String> rl = Files.readAllLines(rf, StandardCharsets.UTF_8);
            List<String> ml = Files.readAllLines(mf, StandardCharsets.UTF_8);
            if (!rl.get(0).contains("\"enumeration\":\"sampled\"")) {
                bad.add(program + ": header " + rl.get(0));
            }
            java.util.Set<String> rs = new java.util.TreeSet<>();
            rl.subList(1, rl.size()).forEach(l -> rs.add(withoutN(l)));
            java.util.Set<String> ms = new java.util.TreeSet<>();
            ml.subList(1, ml.size()).forEach(l -> ms.add(withoutN(l)));
            if (rs.isEmpty() || !rs.equals(ms)) {
                bad.add(program + ": mp-random lines " + rs + " vs Must " + ms);
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
