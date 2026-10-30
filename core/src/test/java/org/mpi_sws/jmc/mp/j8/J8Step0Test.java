package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * J8 test 1 (step-0 equivalence), tester round 02 (plan/jmc-must/log/dev/J8/round-02.prompt.md;
 * J8-PLAN.md revision 7 §3 and §7 test 1). Expected values are the tester's round-01 derivation,
 * observed with a logging hook in the unchanged sequential strategy before any split code existed:
 * {@code src/test/resources/j8/step0-expected.tsv} (row, mode, K label, K, R, N, per-item iteration
 * counts in processing order N−1…0, ran, distinct complete and blocked, md5 of the sequential export).
 *
 * <p>Per row, every run in a fresh JVM ({@link J8Step0Probe}):
 * <ul>
 *   <li>the sequential run: counts and export md5 as derived; digest equal ⇔ {@code GraphCanon}
 *       equal over every graph of the run (§4(a));</li>
 *   <li>for K ∈ {1, first stack size + 1, 8, max + 1}: {@code ItemDriver} in order SEQUENTIAL — split
 *       or not, R, N, per-item counts as derived; the root's outcome digests followed by the items'
 *       (N−1…0) equal the sequential sequence (blocked flags included); distinct complete and blocked,
 *       ran and blocked iterations equal to sequential; the export (J4 rows, built as the launcher
 *       builds it) byte-identical to the sequential export;</li>
 *   <li>when it splits: order REVERSED — each item's result equal to the SEQUENTIAL run's item, same
 *       totals, same export, same fingerprint, items explored 0…N−1;</li>
 *   <li>when it splits: isolated-item mode — for every j a fresh JVM runs only item j; R, N and the
 *       fingerprint equal the full run's, and item j's iterations, outcomes and summaries equal the
 *       full run's item j.</li>
 * </ul>
 *
 * <p>One method per program class (each Gradle invocation runs one method; child JVMs run six at a
 * time). Evidence per row is printed and written to {@code build/test-results/j8-tester/}.
 */
class J8Step0Test {

    private static final String PROBE = "org.mpi_sws.jmc.mp.j8.J8Step0Probe";
    private static final Path DIR = Path.of("build/test-results/j8-tester");
    private static final int PARALLEL = 6;

    /** One line of the derivation. */
    record Exp(String row, String mode, String kLabel, int k, int r, int n, List<Integer> items, int ran,
               int complete, int blocked, String md5) {}

    /** A parsed probe output. */
    record Run(Map<String, String> result, Map<String, String> counts, List<String> outs, String iff,
               List<Seg> segs, Map<Integer, List<String>> sums, List<String> export, String raw) {}

    record Seg(int index, int iterations, int blocked, List<String> outcomes) {}

    static List<Exp> expectations() throws IOException {
        List<Exp> out = new ArrayList<>();
        try (InputStream in = J8Step0Test.class.getResourceAsStream("/j8/step0-expected.tsv");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                List<Integer> items = new ArrayList<>();
                if (!f[6].equals("-")) {
                    for (String x : f[6].trim().split(" ")) {
                        items.add(Integer.parseInt(x));
                    }
                }
                out.add(new Exp(f[0], f[1], f[2], Integer.parseInt(f[3]), f[4].equals("-") ? -1 : Integer.parseInt(f[4]),
                        Integer.parseInt(f[5]), items, Integer.parseInt(f[7]), Integer.parseInt(f[8]),
                        Integer.parseInt(f[9]), f[10]));
            }
        }
        return out;
    }

    static Map<String, String> kv(String line) {
        Map<String, String> m = new LinkedHashMap<>();
        String[] parts = line.split(" ");
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq > 0) {
                String key = parts[i].substring(0, eq);
                if (key.equals("failure")) {
                    m.put(key, line.substring(line.indexOf("failure=") + 8));
                    break;
                }
                m.put(key, parts[i].substring(eq + 1));
            }
        }
        return m;
    }

    static final AtomicInteger SEQ = new AtomicInteger();

    /** Runs the probe in a fresh JVM and parses its output. */
    static Run probe(String... args) throws Exception {
        Files.createDirectories(DIR);
        Path out = Files.createTempFile(DIR, "probe-" + SEQ.incrementAndGet() + "-", ".txt");
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-Xss4m");
        cmd.add("-Xmx512m");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(PROBE);
        Collections.addAll(cmd, args);
        cmd.add(out.toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(DIR.resolve("probe-" + SEQ.get() + ".log").toFile()).start();
        boolean done = p.waitFor(300, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            throw new IllegalStateException("probe timed out: " + String.join(" ", args));
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("probe exit " + p.exitValue() + ": " + String.join(" ", args));
        }
        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        Map<String, String> result = null;
        Map<String, String> counts = null;
        List<String> outs = new ArrayList<>();
        String iff = null;
        List<Seg> segs = new ArrayList<>();
        Map<Integer, List<String>> sums = new TreeMap<>();
        List<String> export = new ArrayList<>();
        for (String l : lines) {
            if (l.startsWith("RESULT ")) {
                result = kv(l);
            } else if (l.startsWith("COUNTS ")) {
                counts = kv(l);
            } else if (l.startsWith("OUT ")) {
                String[] f = l.split(" ");
                outs.add(f[1] + ":" + f[2]);
            } else if (l.startsWith("IFF ")) {
                iff = l.substring(4);
            } else if (l.startsWith("SEG ")) {
                String[] f = l.split(" ");
                segs.add(new Seg(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        f[4].equals("-") ? List.of() : List.of(f[4].split(","))));
            } else if (l.startsWith("SUM ")) {
                int sp = l.indexOf(' ', 4);
                sums.computeIfAbsent(Integer.parseInt(l.substring(4, sp)), x -> new ArrayList<>())
                        .add(l.substring(sp + 1));
            } else if (l.startsWith("EXPORT ")) {
                export.add(l.substring(7));
            }
        }
        return new Run(result, counts, outs, iff, segs, sums, export, String.join("\n", lines));
    }

    static String md5(List<String> lines) throws Exception {
        StringBuilder b = new StringBuilder();
        for (String l : lines) {
            b.append(l).append('\n');
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(
                b.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /** Checks one row; returns its problems (empty when it passes) and appends evidence. */
    static List<String> checkRow(String row, List<Exp> exps, ExecutorService pool, StringBuilder evidence)
            throws Exception {
        List<String> bad = new ArrayList<>();
        String mode = exps.get(0).mode();
        Exp any = exps.get(0);
        Future<Run> seqF = pool.submit(() -> probe("seq", row, mode));
        Map<Integer, Future<Run>> fullF = new TreeMap<>();
        Map<Integer, Future<Run>> revF = new TreeMap<>();
        Map<Integer, List<Future<Run>>> isoF = new TreeMap<>();
        Map<Integer, Exp> byK = new TreeMap<>();
        for (Exp e : exps) {
            byK.putIfAbsent(e.k(), e);
        }
        for (Exp e : byK.values()) {
            fullF.put(e.k(), pool.submit(() -> probe("split", row, mode, Integer.toString(e.k()), "SEQUENTIAL", "-")));
            if (e.r() >= 0) {
                revF.put(e.k(), pool.submit(() -> probe("split", row, mode, Integer.toString(e.k()), "REVERSED", "-")));
                List<Future<Run>> l = new ArrayList<>();
                for (int j = 0; j < e.n(); j++) {
                    String js = Integer.toString(j);
                    l.add(pool.submit(() -> probe("split", row, mode, Integer.toString(e.k()), "SEQUENTIAL", js)));
                }
                isoF.put(e.k(), l);
            }
        }
        Run seq = seqF.get();
        String pre = row + ": ";
        if (!seq.result().get("failure").equals("-")) {
            bad.add(pre + "sequential run failed: " + seq.result().get("failure"));
            return bad;
        }
        if (Integer.parseInt(seq.counts().get("ran")) != any.ran()
                || Integer.parseInt(seq.counts().get("complete")) != any.complete()
                || Integer.parseInt(seq.counts().get("blocked")) != any.blocked()) {
            bad.add(pre + "sequential counts " + seq.counts() + " differ from the derivation ran " + any.ran()
                    + " complete " + any.complete() + " blocked " + any.blocked());
        }
        if (!seq.iff().startsWith("0 ")) {
            bad.add(pre + "digest <=> GraphCanon violated: " + seq.iff());
        }
        if (mode.equals("export") && !md5(seq.export()).equals(any.md5())) {
            bad.add(pre + "sequential export md5 " + md5(seq.export()) + " differs from the derivation " + any.md5());
        }
        evidence.append(row).append(" | seq ran ").append(seq.counts().get("ran")).append(" c ")
                .append(seq.counts().get("complete")).append(" b ").append(seq.counts().get("blocked"))
                .append(" iff ").append(seq.iff());
        for (Exp e : exps) {
            Run full = fullF.get(e.k()).get();
            String p = pre + "K " + e.kLabel() + "=" + e.k() + ": ";
            if (!full.result().get("failure").equals("-")) {
                bad.add(p + "split run failed: " + full.result().get("failure"));
                continue;
            }
            boolean split = Boolean.parseBoolean(full.result().get("split"));
            if (split != (e.r() >= 0)) {
                bad.add(p + "split " + split + ", derived " + (e.r() >= 0));
                continue;
            }
            // outcomes: root followed by items in processing order N-1..0
            List<String> seqOut = new ArrayList<>();
            for (String o : seq.outs()) {
                seqOut.add(o);
            }
            List<String> splitOut = new ArrayList<>();
            List<Integer> itemCounts = new ArrayList<>();
            List<Integer> itemOrder = new ArrayList<>();
            for (Seg s : full.segs()) {
                splitOut.addAll(s.outcomes());
                if (s.index() >= 0) {
                    itemCounts.add(s.iterations());
                    itemOrder.add(s.index());
                }
                if (s.outcomes().size() != s.iterations()) {
                    bad.add(p + "segment " + s.index() + " has " + s.iterations() + " iterations but "
                            + s.outcomes().size() + " outcomes");
                }
            }
            if (!splitOut.equals(seqOut)) {
                bad.add(p + "outcome sequence differs from sequential (" + splitOut.size() + " vs " + seqOut.size() + ")");
            }
            if (split) {
                int r = Integer.parseInt(full.result().get("r"));
                int n = Integer.parseInt(full.result().get("n"));
                List<Integer> expectedOrder = new ArrayList<>();
                for (int j = n - 1; j >= 0; j--) {
                    expectedOrder.add(j);
                }
                if (r != e.r() || n != e.n()) {
                    bad.add(p + "R " + r + " N " + n + ", derived R " + e.r() + " N " + e.n());
                }
                if (!itemCounts.equals(e.items())) {
                    bad.add(p + "per-item counts " + itemCounts + ", derived " + e.items());
                }
                if (!itemOrder.equals(expectedOrder)) {
                    bad.add(p + "item order " + itemOrder + ", expected " + expectedOrder);
                }
                if (full.segs().isEmpty() || full.segs().get(0).index() != -1 || full.segs().get(0).iterations() != r) {
                    bad.add(p + "root segment does not have R iterations: " + full.segs());
                }
            } else if (full.segs().size() != 1 || full.segs().get(0).index() != -1) {
                bad.add(p + "no split, but segments " + full.segs());
            }
            for (String key : List.of("complete", "blocked", "ran", "blockedIterations")) {
                if (!full.counts().get(key).equals(seq.counts().get(key))) {
                    bad.add(p + key + " " + full.counts().get(key) + " vs sequential " + seq.counts().get(key));
                }
            }
            if (mode.equals("export") && !full.export().equals(seq.export())) {
                bad.add(p + "export differs from sequential: md5 " + md5(full.export()) + " vs " + md5(seq.export()));
            }
            evidence.append(" | K").append(e.kLabel()).append('=').append(e.k()).append(" split ").append(split);
            if (split) {
                evidence.append(" R ").append(full.result().get("r")).append(" N ").append(full.result().get("n"))
                        .append(" items ").append(itemCounts);
            }
            if (!split) {
                continue;
            }
            // reversed order
            Run rev = revF.get(e.k()).get();
            if (!rev.result().get("failure").equals("-")) {
                bad.add(p + "reversed run failed: " + rev.result().get("failure"));
            } else {
                Map<Integer, Seg> fullItems = new TreeMap<>();
                full.segs().forEach(s -> fullItems.put(s.index(), s));
                List<Integer> revOrder = new ArrayList<>();
                for (Seg s : rev.segs()) {
                    if (s.index() >= 0) {
                        revOrder.add(s.index());
                    }
                    if (!s.equals(fullItems.get(s.index()))) {
                        bad.add(p + "reversed: segment " + s.index() + " " + s + " vs " + fullItems.get(s.index()));
                    }
                    if (!rev.sums().getOrDefault(s.index(), List.of())
                            .equals(full.sums().getOrDefault(s.index(), List.of()))) {
                        bad.add(p + "reversed: summaries of segment " + s.index() + " differ");
                    }
                }
                List<Integer> asc = new ArrayList<>();
                for (int j = 0; j < e.n(); j++) {
                    asc.add(j);
                }
                if (!revOrder.equals(asc)) {
                    bad.add(p + "reversed: item order " + revOrder);
                }
                if (!rev.counts().equals(full.counts())) {
                    bad.add(p + "reversed: counts " + rev.counts() + " vs " + full.counts());
                }
                if (!rev.result().get("fp").equals(full.result().get("fp"))) {
                    bad.add(p + "reversed: fingerprint differs");
                }
                if (mode.equals("export") && !rev.export().equals(full.export())) {
                    bad.add(p + "reversed: export differs");
                }
            }
            // isolated items
            List<Future<Run>> isos = isoF.get(e.k());
            Map<Integer, Seg> fullItems = new TreeMap<>();
            full.segs().forEach(s -> fullItems.put(s.index(), s));
            for (int j = 0; j < isos.size(); j++) {
                Run iso = isos.get(j).get();
                String q = p + "isolated item " + j + ": ";
                if (!iso.result().get("failure").equals("-")) {
                    bad.add(q + "failed: " + iso.result().get("failure"));
                    continue;
                }
                if (!iso.result().get("r").equals(full.result().get("r"))
                        || !iso.result().get("n").equals(full.result().get("n"))
                        || !iso.result().get("fp").equals(full.result().get("fp"))) {
                    bad.add(q + "R/N/fingerprint differ: " + iso.result() + " vs " + full.result());
                }
                List<Seg> items = new ArrayList<>();
                for (Seg s : iso.segs()) {
                    if (s.index() >= 0) {
                        items.add(s);
                    }
                }
                if (items.size() != 1 || !items.get(0).equals(fullItems.get(j))) {
                    bad.add(q + "items " + items + " vs full-run item " + fullItems.get(j));
                }
                if (!iso.sums().getOrDefault(j, List.of()).equals(full.sums().getOrDefault(j, List.of()))) {
                    bad.add(q + "summaries differ");
                }
                if (!iso.segs().get(0).equals(full.segs().get(0))) {
                    bad.add(q + "root segment differs");
                }
            }
            evidence.append(" rev+iso(").append(isos.size()).append(") checked");
        }
        evidence.append(bad.isEmpty() ? " | PASS" : " | FAIL " + bad.size()).append('\n');
        return bad;
    }

    /** Checks every row whose program spec starts with {@code prefix}. */
    static void checkGroup(String prefix) throws Exception {
        Map<String, List<Exp>> rows = new LinkedHashMap<>();
        for (Exp e : expectations()) {
            if (e.row().startsWith(prefix)) {
                rows.computeIfAbsent(e.row(), x -> new ArrayList<>()).add(e);
            }
        }
        assertTrue(!rows.isEmpty(), "rows for " + prefix);
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        List<String> bad = new ArrayList<>();
        StringBuilder evidence = new StringBuilder();
        long t0 = System.nanoTime();
        try {
            for (Map.Entry<String, List<Exp>> r : rows.entrySet()) {
                try {
                    bad.addAll(checkRow(r.getKey(), r.getValue(), pool, evidence));
                } catch (Exception ex) {
                    bad.add(r.getKey() + ": " + ex);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        evidence.append(String.format("%s: %d rows, %d problems, %.1f s%n", prefix, rows.size(), bad.size(),
                (System.nanoTime() - t0) / 1e9));
        bad.forEach(b -> evidence.append("PROBLEM ").append(b).append('\n'));
        Files.createDirectories(DIR);
        String file = prefix.replaceAll("[^A-Za-z0-9]", "_");
        Files.writeString(DIR.resolve("evidence-" + file + ".txt"), evidence.toString(), StandardCharsets.UTF_8);
        System.out.println(evidence);
        assertEquals(List.of(), bad, prefix);
    }

    private static final String H1 = "org.mpi_sws.jmc.mp.h1.";
    private static final String J4 = "org.mpi_sws.jmc.mp.programs.j4.";

    @Test void test1_actorChannels() throws Exception { checkGroup(H1 + "ActorChannels:"); }
    @Test void test1_actors() throws Exception { checkGroup(H1 + "Actors:"); }
    @Test void test1_channelSemantics() throws Exception { checkGroup(H1 + "ChannelSemantics:"); }
    @Test void test1_estimation() throws Exception { checkGroup(H1 + "Estimation:"); }
    @Test void test1_executionOrder() throws Exception { checkGroup(H1 + "ExecutionOrder:"); }
    @Test void test1_h1Repro() throws Exception { checkGroup(H1 + "H1Repro:"); }
    @Test void test1_jobQueue() throws Exception { checkGroup(H1 + "JobQueue:"); }
    @Test void test1_litmus() throws Exception { checkGroup(H1 + "Litmus:"); }
    @Test void test1_litmusChannels() throws Exception { checkGroup(H1 + "LitmusChannels:"); }
    @Test void test1_micro() throws Exception { checkGroup(H1 + "Micro:"); }
    @Test void test1_minibus() throws Exception { checkGroup(H1 + "Minibus:"); }
    @Test void test1_ruPaxos() throws Exception { checkGroup(H1 + "RuPaxos:"); }
    @Test void test1_semanticsComparison() throws Exception { checkGroup(H1 + "SemanticsComparison:"); }
    @Test void test1_smallTests() throws Exception { checkGroup(H1 + "SmallTests:"); }
    @Test void test1_tags() throws Exception { checkGroup(H1 + "Tags:"); }
    @Test void test1_twoPc() throws Exception { checkGroup(H1 + "TwoPc:"); }
    @Test void test1_vecTags() throws Exception { checkGroup(H1 + "VecTags:"); }
    @Test void test1_workTerminate() throws Exception { checkGroup(H1 + "WorkTerminate:"); }
    @Test void test1_a3Lock() throws Exception { checkGroup(J4 + "A3Lock:"); }
    @Test void test1_a2Store() throws Exception { checkGroup(J4 + "A2Store:"); }
}
