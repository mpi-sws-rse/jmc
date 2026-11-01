package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * J8 tester round 09 (plan/jmc-must/log/dev/J8/round-09.prompt.md): the split measurement block after
 * the owner's ruling (J8.report.md §8). Derived: a split run explores exactly the sequential run's
 * graphs (root + items; workers also replay the root), so every graph-shape maximum — a maximum over
 * graphs — equals the sequential block's; counts, summaries and iterations equal sequential; the means
 * and {@code summarise_ms} are empty; {@code peak_rss_kb} is the maximum over the coordinator and every
 * worker process, hence at least every {@code *.stats.json} value and at least 1; each worker process
 * that ends normally writes one stats file (round 09; since the D-9 fix of round 10 there are no stats
 * files: each {@code item-<j>.json} carries its worker's {@code peak_rss_kb} and maxima); an aborted run
 * prints empty maxima and {@code stop_reason:
 * error}.
 */
class J8Round09Test {

    static final List<String> MAXIMA = List.of("max_message_events", "max_porf_pairs", "max_graph_events",
            "max_visible_events", "max_summary_bytes", "max_porf_count");
    static final List<String> EMPTY = List.of("summarise_ms", "mean_graph_events", "mean_visible_events");
    static final List<String> EQUAL = List.of("graphs_complete", "graphs_blocked", "summaries_distinct", "iterations",
            "stop_reason");

    static Map<String, String> block(J8Launch.Out o) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String[] kv : o.block()) {
            m.put(kv[0], kv[1]);
        }
        return m;
    }

    /** The number of {@code *.stats.json} files of a run directory (none since the D-9 fix). */
    static int statsFiles(Path runDir) throws Exception {
        if (runDir == null || !Files.isDirectory(runDir)) {
            return 0;
        }
        try (var s = Files.list(runDir)) {
            return (int) s.filter(p -> p.getFileName().toString().endsWith(".stats.json")).count();
        }
    }

    /** The {@code peak_rss_kb} of every committed result envelope ({@code item-<j>.json}) of a run directory. */
    static List<Long> envelopePeaks(Path runDir) throws Exception {
        List<Long> out = new ArrayList<>();
        if (runDir == null || !Files.isDirectory(runDir)) {
            return out;
        }
        try (var s = Files.list(runDir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (p.getFileName().toString().matches("item-\\d+\\.json")) {
                    Matcher m = Pattern.compile("\"peak_rss_kb\":(-?\\d+)").matcher(Files.readString(p));
                    assertTrue(m.find(), p.toString());
                    out.add(Long.parseLong(m.group(1)));
                }
            }
        }
        return out;
    }

    /** The stats files of a run directory: peak_rss_kb of each (round 09). */
    static List<Long> statsPeaks(Path runDir) throws Exception {
        List<Long> out = new ArrayList<>();
        if (runDir == null || !Files.isDirectory(runDir)) {
            return out;
        }
        try (var s = Files.list(runDir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (p.getFileName().toString().endsWith(".stats.json")) {
                    Matcher m = Pattern.compile("\"peak_rss_kb\":(-?\\d+)").matcher(Files.readString(p));
                    assertTrue(m.find(), p.toString());
                    out.add(Long.parseLong(m.group(1)));
                }
            }
        }
        return out;
    }

    /** One row: sequential versus --split 4 --k 1; returns its problems and appends evidence. */
    static List<String> check(String row, boolean export, List<String> evidence) throws Exception {
        List<String> bad = new ArrayList<>();
        J8Launch.Out seq = J8Launch.launch(row, export);
        J8Launch.Out sp = J8Launch.launch(row, export, "--split", "4", "--k", "1");
        Map<String, String> a = block(seq);
        Map<String, String> b = block(sp);
        String p = row + ": ";
        if (seq.exit() != 0 || sp.exit() != 0) {
            bad.add(p + "exit " + seq.exit() + "/" + sp.exit());
            return bad;
        }
        List<String> keys = new ArrayList<>(b.keySet());
        if (!keys.equals(new ArrayList<>(a.keySet())) || sp.block().size() != keys.size()) {
            bad.add(p + "keys " + keys + " vs " + a.keySet());
        }
        for (String k : EQUAL) {
            if (!a.get(k).equals(b.get(k))) {
                bad.add(p + k + " " + b.get(k) + " vs sequential " + a.get(k));
            }
        }
        for (String k : MAXIMA) {
            if (!a.get(k).equals(b.get(k))) {
                bad.add(p + k + " " + b.get(k) + " vs sequential " + a.get(k));
            }
        }
        for (String k : EMPTY) {
            if (!b.get(k).isEmpty()) {
                bad.add(p + k + " not empty: " + b.get(k));
            }
        }
        long rss = b.get("peak_rss_kb").isEmpty() ? -1 : Long.parseLong(b.get("peak_rss_kb"));
        String split = sp.field("split");
        boolean didSplit = split != null && !split.contains("no split");
        List<Long> peaks = envelopePeaks(sp.runDir());
        int n = didSplit ? Integer.parseInt(split.replaceAll(".*, N (\\d+).*", "$1")) : 0;
        if (rss < 1) {
            bad.add(p + "peak_rss_kb " + rss);
        }
        for (long x : peaks) {
            if (x < 1 || rss < x) {
                bad.add(p + "peak_rss_kb " + rss + " < a worker's " + x);
            }
        }
        if (peaks.size() != n) {
            bad.add(p + peaks.size() + " result envelopes, expected N = " + n);
        }
        if (statsFiles(sp.runDir()) != 0) {
            bad.add(p + statsFiles(sp.runDir()) + " stats files, expected none (D-9 fix)");
        }
        StringBuilder m = new StringBuilder();
        MAXIMA.forEach(k -> m.append(b.get(k)).append(' '));
        evidence.add(row + " split " + didSplit + " maxima " + m + "rss " + rss + " workers " + peaks
                + (bad.isEmpty() ? " PASS" : " FAIL"));
        return bad;
    }

    static final String H1 = J8Launch.H1;
    static final String J4 = J8Launch.J4;

    /** Every A3/A2 row and a sample of H1 rows (each kind of program; largest rows included). */
    @Test
    void block_maximaCountsEqualSequential_meansEmpty_rssAtLeastWorkers_K1W4() throws Exception {
        List<String[]> rows = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (J8Step0Test.Exp e : J8Step0Test.expectations()) {
            if (e.row().startsWith(J4)) {
                seen.putIfAbsent(e.row(), true);
            }
        }
        seen.keySet().forEach(r -> rows.add(new String[] {r, "true"}));
        for (String r : List.of("RuPaxos:1", "RuPaxos:1,tfjoin", "JobQueue:pull,4", "JobQueue:push-thread,4",
                "Litmus:r_ns_assm,7", "Litmus:test_named_thread", "Litmus:nested_spawn", "Litmus:r_s_r_ss_nonblocking",
                "LitmusChannels:stress_max,fifo,4", "LitmusChannels:blocking_rev", "Actors:token_server", "Actors:bank",
                "ActorChannels:locked_bank", "ActorChannels:bounded_channel,2", "TwoPc:two_pc,4", "Minibus:nowait",
                "WorkTerminate:hard_to_find,10", "Estimation:ns_nseqr,5", "SemanticsComparison:p1,bag",
                "Micro:wakeup_stress,4", "Tags:nontrivial_tag", "H1Repro:f2_nested_spawn", "VecTags:basic")) {
            rows.add(new String[] {H1 + r, "false"});
        }
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<String> bad = Collections.synchronizedList(new ArrayList<>());
        List<String> evidence = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> fs = new ArrayList<>();
        for (String[] r : rows) {
            fs.add(pool.submit(() -> {
                try {
                    bad.addAll(check(r[0], Boolean.parseBoolean(r[1]), evidence));
                } catch (Exception e) {
                    bad.add(r[0] + ": " + e);
                }
                return null;
            }));
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        List<String> sorted = new ArrayList<>(evidence);
        Collections.sort(sorted);
        Files.createDirectories(J8Launch.DIR);
        Files.write(J8Launch.DIR.resolve("r9-block-evidence.txt"), sorted);
        sorted.forEach(x -> System.out.println("J8R9 " + x));
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
        assertEquals(rows.size(), evidence.size());
    }

    /** A failed run: maxima empty, stop_reason error. */
    @Test
    void block_abortedRun_maximaEmpty_stopReasonError() throws Exception {
        J8Launch.Out o = J8Launch.launch(List.of("-Djmc.split.fault=fail:2:0"), J4 + "A3Lock:fifo,3,1", true,
                "--split", "2", "--k", "1");
        Map<String, String> b = block(o);
        System.out.println("J8R9 aborted: exit " + o.exit() + " block " + b);
        assertEquals(1, o.exit());
        assertEquals("error", b.get("stop_reason"));
        for (String k : MAXIMA) {
            assertEquals("", b.get(k), k);
        }
    }

    /**
     * Recovery: a worker restarted after a death. Derived: the dead process (killed by {@code halt}) writes
     * no stats file, its restarted successor writes one; for W = 1 and {@code die-after:<j>:0}: 2 worker
     * processes, 1 stats file (round 09). Since the D-9 fix: no stats files, 5 result envelopes, the
     * printed peak at least every envelope's. The maxima must still equal sequential (the item explored by the dead
     * process was committed, so its graphs are part of the run) — checked for every item j.
     */
    @Test
    void block_restartedWorker_statsFiles_and_maxima_forEveryItem() throws Exception {
        String row = J4 + "A3Lock:fifo,3,1";
        Map<String, String> a = block(J8Launch.launch(row, true));
        List<String> bad = new ArrayList<>();
        for (int j = 4; j >= 0; j--) {
            J8Launch.Out o = J8Launch.launch(List.of("-Djmc.split.fault=die-after:" + j + ":0"), row, true,
                    "--split", "1", "--k", "1");
            Map<String, String> b = block(o);
            List<Long> peaks = envelopePeaks(o.runDir());
            int stats = statsFiles(o.runDir());
            StringBuilder m = new StringBuilder();
            MAXIMA.forEach(k -> m.append(k).append('=').append(b.get(k)).append(' '));
            System.out.println("J8R9 die-after:" + j + ":0 exit " + o.exit() + " stats files " + stats + " envelopes "
                    + peaks.size() + " rss " + b.get("peak_rss_kb") + " " + m
                    + "| sequential " + MAXIMA.stream().map(a::get).toList());
            if (o.exit() != 0) {
                bad.add("j=" + j + ": exit " + o.exit());
                continue;
            }
            if (stats != 0 || peaks.size() != 5) {
                bad.add("j=" + j + ": " + stats + " stats files, " + peaks.size() + " envelopes; derived 0 and 5");
            }
            for (long x : peaks) {
                if (Long.parseLong(b.get("peak_rss_kb")) < x) {
                    bad.add("j=" + j + ": peak_rss_kb " + b.get("peak_rss_kb") + " < an envelope's " + x);
                }
            }
            for (String k : MAXIMA) {
                if (!a.get(k).equals(b.get(k))) {
                    bad.add("j=" + j + ": " + k + " " + b.get(k) + " vs sequential " + a.get(k));
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * Derived (exactness rule, J8.report.md §8): a worker that dies after publishing its last item is
     * restarted, and the run completes with the sequential counts; the graphs of every item the dead
     * process explored are part of the run, so the maxima must still equal the sequential block's.
     * Rows where the largest graphs lie in the items the dead process explored: A3Lock never-grant and
     * double-grant {@code 3,1}, W = 1, K = 1, {@code die-after:0:0} (item 0 is the last item; the dead
     * process explored items 4..0).
     */
    @Test
    void block_workerDeadAfterPublishing_maximaStillEqualSequential() throws Exception {
        List<String> bad = new ArrayList<>();
        for (String r : List.of("A3Lock:never-grant,3,1", "A3Lock:double-grant,3,1")) {
            String row = J4 + r;
            Map<String, String> a = block(J8Launch.launch(row, true));
            J8Launch.Out o = J8Launch.launch(List.of("-Djmc.split.fault=die-after:0:0"), row, true,
                    "--split", "1", "--k", "1");
            Map<String, String> b = block(o);
            System.out.println("J8R9 dead-after-publish " + r + ": exit " + o.exit() + " graphs " + o.field("graphs")
                    + " | split " + MAXIMA.stream().map(b::get).toList() + " | sequential "
                    + MAXIMA.stream().map(a::get).toList());
            if (o.exit() != 0) {
                bad.add(r + ": exit " + o.exit());
                continue;
            }
            for (String k : EQUAL) {
                if (!a.get(k).equals(b.get(k))) {
                    bad.add(r + ": " + k + " " + b.get(k) + " vs sequential " + a.get(k));
                }
            }
            for (String k : MAXIMA) {
                if (!a.get(k).equals(b.get(k))) {
                    bad.add(r + ": " + k + " " + b.get(k) + " vs sequential " + a.get(k));
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
