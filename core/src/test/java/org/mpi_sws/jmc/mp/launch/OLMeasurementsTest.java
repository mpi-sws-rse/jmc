package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tester test for OL round 01 (plan/jmc-must/log/dev/OL/round-01.prompt.md): the machine-readable
 * measurement block of MESSAGE-online-mode.md §0, printed by {@link MpLauncher} after the marker line
 * {@code --- measurements}. One method (owner: "one test that parses the lines"); it launches several
 * child JVMs as {@link MpLauncherTest} does. Every expected value is derived before the run (see the
 * round-01 report): the H1 constants of {@code H1CountsTest}, the J4/J7 constants, the export file
 * itself, and hand counts of tiny programs. Round 02: the keys and definitions of brain-data/SCHEMA.md
 * "Key definitions (ruled 2026-10-31, both tools)" and {@code stop_reason: first-miss} for a miss on the
 * last graph.
 */
class OLMeasurementsTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String H1 = "org.mpi_sws.jmc.mp.h1.";
    private static final String J4 = "org.mpi_sws.jmc.mp.programs.j4.";
    private static final String DIR = "build/test-results/ol-tester/";

    /** §0's keys in the message's order; the three first-miss keys only with a spec file. */
    private static final List<String> HEAD = List.of("wall_ms", "explore_ms", "summarise_ms", "index_ms",
            "lookup_ms", "export_ms", "graphs_complete", "graphs_blocked", "summaries_distinct", "iterations",
            "stop_reason", "peak_rss_kb", "max_message_events", "max_porf_pairs");
    private static final List<String> SPEC = List.of("first_miss_iteration", "first_miss_wall_ms", "uncovered_total");
    private static final List<String> SHAPE = List.of("max_graph_events", "mean_graph_events", "max_visible_events",
            "mean_visible_events", "max_summary_bytes", "max_porf_count");
    private static final Set<String> MEANS = Set.of("mean_graph_events", "mean_visible_events");
    // "error": the fifth value of R1 / Codex m2 (owner ruling pending); accepted by the parser.
    private static final Set<String> STOPS = Set.of("exhausted", "budget", "timeout", "first-miss", "error");

    /** Parses the block after the marker; asserts keys, order, uniqueness and value forms (exit 0, RSS read). */
    static Map<String, String> block(MpLauncherTest.Out o, boolean online) {
        return block(o, online, 0, false);
    }

    /**
     * As {@link #block(MpLauncherTest.Out, boolean)} with the expected exit status and whether
     * {@code peak_rss_kb} must be empty (VmHWM unreadable) instead of positive.
     */
    static Map<String, String> block(MpLauncherTest.Out o, boolean online, int exit, boolean rssEmpty) {
        String t = o.text();
        assertEquals(exit, o.exit(), "exit status\n" + t);
        String[] lines = t.split("\n", -1);
        int marker = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals(MpLauncher.MEASUREMENTS_MARKER)) {
                assertEquals(-1, marker, "the marker line appears once\n" + t);
                marker = i;
            }
        }
        assertTrue(marker >= 0, "a '--- measurements' line\n" + t);
        Pattern kv = Pattern.compile("^([a-z_]+):(?: ?(.*))$"); // "key:" or "key: " = empty value
        List<String> keys = new ArrayList<>();
        Map<String, String> v = new LinkedHashMap<>();
        for (int i = marker + 1; i < lines.length; i++) {
            Matcher m = kv.matcher(lines[i]);
            if (!m.matches()) {
                break;
            }
            keys.add(m.group(1));
            v.put(m.group(1), m.group(2) == null ? "" : m.group(2).trim());
        }
        List<String> expected = new ArrayList<>(HEAD);
        if (online) {
            expected.addAll(SPEC);
        }
        expected.addAll(SHAPE);
        assertEquals(expected, keys, "keys after the marker, in §0's order, each once\n" + t);
        for (Map.Entry<String, String> e : v.entrySet()) {
            String k = e.getKey();
            String x = e.getValue();
            if (k.equals("stop_reason")) {
                assertTrue(STOPS.contains(x), "stop_reason " + x);
            } else if (MEANS.contains(k)) {
                assertTrue(x.matches("([0-9]+\\.[0-9]+)?"), k + " is a non-negative decimal or empty: " + x);
            } else {
                assertTrue(x.matches("[0-9]*"), k + " is a non-negative integer or empty: " + x);
            }
            // SCHEMA.md: empty = applies but unmeasured; the parser accepts it. JMC measures every key (OL0),
            // except peak_rss_kb when VmHWM cannot be read (Codex m1).
            if (!(rssEmpty && k.equals("peak_rss_kb"))) {
                assertTrue(!x.isEmpty(), "JMC measures " + k);
            }
        }
        long phases = n(v, "explore_ms") + n(v, "summarise_ms") + n(v, "lookup_ms") + n(v, "index_ms")
                + n(v, "export_ms");
        // Codex M3: explore + summarise + lookup are disjoint parts of the checker's run, inside wall_ms.
        assertTrue(n(v, "explore_ms") + n(v, "summarise_ms") + n(v, "lookup_ms") <= n(v, "wall_ms"),
                "explore + summarise + lookup <= wall_ms");
        // R3: disjoint phases inside wall_ms; each phase is truncated to ms, explore_ms is a difference (+2 ms).
        assertTrue(phases <= n(v, "wall_ms") + 3, "phases " + phases + " <= wall_ms " + v.get("wall_ms"));
        if (rssEmpty) {
            assertEquals("", v.get("peak_rss_kb"), "VmHWM unreadable: empty (SCHEMA: applies, unmeasured)");
        } else {
            assertTrue(n(v, "peak_rss_kb") > 0, "Linux: VmHWM is available");
        }
        // The block agrees with the human graphs: line when the run got that far (not on a refused spec).
        Matcher g = Pattern.compile("(?m)^graphs:\\s+([0-9]+) distinct complete, ([0-9]+) distinct blocked").matcher(t);
        if (g.find()) {
            assertEquals(g.group(1), v.get("graphs_complete"), "graphs_complete vs graphs:");
            assertEquals(g.group(2), v.get("graphs_blocked"), "graphs_blocked vs graphs:");
        } else {
            assertTrue(exit != 0, "a graphs: line on a run that did not fail early\n" + t);
        }
        System.out.println("OL block " + v);
        return v;
    }

    static long n(Map<String, String> v, String k) {
        return Long.parseLong(v.get(k));
    }

    static MpLauncherTest.Out counting(String row) throws Exception {
        return MpLauncherTest.launch(LAUNCHER, row, "must", "0", "1", DIR + "report-" + slug(row), "-", "60");
    }

    static String slug(String row) {
        return row.substring(row.lastIndexOf('.') + 1).replaceAll("[:, ]", "_");
    }

    static Path exportPath(String row) {
        return Path.of(DIR + slug(row) + ".must.jsonl");
    }

    static MpLauncherTest.Out export(String row) throws Exception {
        Path p = exportPath(row);
        Files.deleteIfExists(p);
        return MpLauncherTest.launch(LAUNCHER, row, "must", "0", "1", DIR + "report-" + slug(row), p.toString(), "300");
    }

    static MpLauncherTest.Out online(String impl, Path spec) throws Exception {
        return MpLauncherTest.launch(LAUNCHER, impl, "must", "0", "1", DIR + "report-online-" + slug(impl), "-", "300",
                "--spec", spec.toString());
    }

    /** Round 05: {@link #online} in bug-finder mode ({@code --stop-at-first}). */
    static MpLauncherTest.Out onlineStop(String impl, Path spec) throws Exception {
        return MpLauncherTest.launch(LAUNCHER, impl, "must", "0", "1", DIR + "report-online-stop-" + slug(impl), "-",
                "300", "--spec", spec.toString(), "--stop-at-first");
    }

    static void counting0(Map<String, String> v) {
        for (String k : List.of("summarise_ms", "lookup_ms", "index_ms", "export_ms", "summaries_distinct",
                "max_visible_events", "max_summary_bytes", "max_porf_count", "max_porf_pairs")) {
            assertEquals("0", v.get(k), "counting mode: " + k);
        }
        assertEquals("0.000", v.get("mean_visible_events"), "counting mode: mean_visible_events");
        assertEquals("exhausted", v.get("stop_reason"));
    }

    /** Words per visible role in a summary line = events of the visible roles when no client blocked. */
    static int words(String line) {
        Matcher m = Pattern.compile("\\{\"[sr]\":").matcher(line);
        int c = 0;
        while (m.find()) {
            c++;
        }
        return c;
    }

    /** Summary-file facts: max bytes without "n", max/mean visible send+receive (words), order counts. */
    record FileFacts(int lines, int maxBytesNoN, int maxWords, String meanWords, int maxCount, int maxNonZero,
            long sumN) {}

    static FileFacts facts(Path p) throws Exception {
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        List<String> body = lines.subList(1, lines.size());
        int maxBytes = 0;
        int maxWords = 0;
        int maxCount = 0;
        int maxNonZero = 0;
        long sumWords = 0;
        long sumN = 0;
        for (String l : body) {
            Matcher nm = Pattern.compile(",\"n\":([0-9]+)}$").matcher(l);
            assertTrue(nm.find(), l);
            long mult = Long.parseLong(nm.group(1));
            String noN = l.substring(0, nm.start()) + "}";
            maxBytes = Math.max(maxBytes, noN.getBytes(StandardCharsets.UTF_8).length);
            maxWords = Math.max(maxWords, words(l));
            sumWords += mult * words(l);
            sumN += mult;
            Matcher cm = Pattern.compile("\"counts\":\\[([0-9,]*)\\]").matcher(l);
            assertTrue(cm.find(), l);
            int nonZero = 0;
            for (String c : cm.group(1).isEmpty() ? new String[0] : cm.group(1).split(",")) {
                int v = Integer.parseInt(c);
                maxCount = Math.max(maxCount, v);
                nonZero += v != 0 ? 1 : 0;
            }
            maxNonZero = Math.max(maxNonZero, nonZero);
        }
        return new FileFacts(body.size(), maxBytes, maxWords,
                String.format(java.util.Locale.ROOT, "%.3f", (double) sumWords / sumN), maxCount, maxNonZero, sumN);
    }

    /** The summary keys of a block equal the facts of the run's summaries. */
    static void sameAsFile(Map<String, String> v, FileFacts f) {
        assertEquals(String.valueOf(f.lines()), v.get("summaries_distinct"), "summaries_distinct = lines - 1");
        assertEquals(String.valueOf(f.maxBytesNoN()), v.get("max_summary_bytes"), "longest line without \"n\"");
        assertEquals(String.valueOf(f.maxWords()), v.get("max_visible_events"), "visible send+receive = words");
        assertEquals(f.meanWords(), v.get("mean_visible_events"), "mean words per summarised graph");
        assertEquals(String.valueOf(f.maxCount()), v.get("max_porf_count"), "largest order-count entry");
        assertEquals(String.valueOf(f.maxNonZero()), v.get("max_porf_pairs"), "most non-zero entries");
    }

    @Test
    void measurementLines() throws Exception {
        // ---- 1. counting mode on an H1 row: Litmus:r_s_ss, TraceForge 2 complete, 1 blocked (H1CountsTest).
        // Message events (complete graphs): rh tryRecv 1; sh1 r, s 2; sh2 r, s, s 3; main s, s 2 = 8.
        // The blocked graph (sh2 reads V(1), assume fails) has fewer: 1 + 2 + 1 + 2 = 6.
        Map<String, String> c = block(counting(H1 + "Litmus:r_s_ss"), false);
        assertEquals("2", c.get("graphs_complete"));
        assertEquals("1", c.get("graphs_blocked"));
        assertEquals("3", c.get("iterations"), "Must is optimal: one iteration per distinct graph");
        assertEquals("8", c.get("max_message_events"));
        counting0(c);

        // ---- 2. hand count: ExecutionOrder:two_receives_two_sends (1 complete, 0 blocked).
        // T1: r1(Tg1) r2(Tg2); T2: s1(Tg2) s2(Tg1); main has no event (spawn/join are edges): 4 events, all messages.
        // No summary in counting mode: max_porf_count, max_porf_pairs 0 (a phase that does not apply).
        Map<String, String> s = block(counting(H1 + "ExecutionOrder:two_receives_two_sends"), false);
        assertEquals("1", s.get("graphs_complete"));
        assertEquals("0", s.get("graphs_blocked"));
        assertEquals("1", s.get("iterations"));
        assertEquals("4", s.get("max_graph_events"));
        assertEquals("4.000", s.get("mean_graph_events"));
        assertEquals("4", s.get("max_message_events"));
        counting0(s);

        // ---- 2b. hand count with a non-message event: Litmus:r_nondet (H1: 4 complete, 0 blocked).
        // main: Nondet (two options: an event) + one send; h: tryRecv (reads the value or ⊥). Every graph:
        // 3 events, 2 of them messages.
        Map<String, String> nd = block(counting(H1 + "Litmus:r_nondet"), false);
        assertEquals("4", nd.get("graphs_complete"));
        assertEquals("0", nd.get("graphs_blocked"));
        assertEquals("4", nd.get("iterations"));
        assertEquals("3", nd.get("max_graph_events"));
        assertEquals("3.000", nd.get("mean_graph_events"));
        assertEquals("2", nd.get("max_message_events"), "the Nondet event is not a message event");
        counting0(nd);

        // ---- 3. export, hand count: A3Lock:spec,2,1. lock: recv Acq, send Grant, recv Rel, twice (6);
        // clients: Acq, Grant, Rel (3 each): 12 events, all messages, 2 graphs (which client first).
        // c0 first: counts (c0's events vs c1, then c1's vs c0) = [0,0,0, 0,3,3]: c1's Grant and Rel come after
        // the lock received c0's Rel, so all 3 c0 events precede them. Mirror [0,3,3, 0,0,0].
        // max_porf_count 3, max_porf_pairs 2, visible 6 per graph.
        String specRow = J4 + "A3Lock:spec,2,1";
        Map<String, String> sp = block(export(specRow), false);
        Path spec = exportPath(specRow);
        FileFacts sf = facts(spec);
        assertEquals("2", sp.get("iterations"));
        assertEquals("2", sp.get("summaries_distinct"));
        assertEquals("12", sp.get("max_graph_events"));
        assertEquals("12", sp.get("max_message_events"));
        assertEquals("3", sp.get("max_porf_count"));
        assertEquals("2", sp.get("max_porf_pairs"));
        assertEquals("6", sp.get("max_visible_events"));
        assertEquals("6.000", sp.get("mean_visible_events"));
        sameAsFile(sp, sf);
        assertEquals("exhausted", sp.get("stop_reason"));

        // ---- 4. export mode on a J4 row: A3Lock:fifo,2,1 (J4: 4 complete, 0 blocked, 2 distinct summaries,
        // the same summary set as spec,2,1). coordinator: 4 receives + 2 grants; clients 6: 12 message events.
        String fifo = J4 + "A3Lock:fifo,2,1";
        Map<String, String> e = block(export(fifo), false);
        FileFacts ff = facts(exportPath(fifo));
        sameAsFile(e, ff);
        assertEquals(4, ff.sumN(), "multiplicities sum to the iterations");
        assertEquals("2", e.get("summaries_distinct"), "J4 constant");
        assertEquals("4", e.get("graphs_complete"));
        assertEquals("0", e.get("graphs_blocked"));
        assertEquals("4", e.get("iterations"));
        assertEquals("12", e.get("max_message_events"));
        assertEquals("3", e.get("max_porf_count"), "same summaries as spec,2,1");
        assertEquals("2", e.get("max_porf_pairs"));
        assertEquals(sp.get("max_summary_bytes"), e.get("max_summary_bytes"), "same summaries as spec,2,1");
        assertEquals("0", e.get("lookup_ms"));
        assertEquals("0", e.get("index_ms"));
        assertEquals("exhausted", e.get("stop_reason"));

        // ---- 4b. export with a blocked visible client: A3Lock:never-grant,2,1 (J4: 3 complete, 1 blocked,
        // 3 distinct summaries, 1 with a blocked client). Done graphs: 6 visible send+receive; the blocked one:
        // c0 3 + c1 Acq 1 = 4 (c1's Block is not a message). Mean over the 4 summarised graphs (3*6 + 4)/4 = 5.500.
        String ng = J4 + "A3Lock:never-grant,2,1";
        Map<String, String> ngv = block(export(ng), false);
        FileFacts ngf = facts(exportPath(ng));
        sameAsFile(ngv, ngf);
        assertEquals("3", ngv.get("graphs_complete"));
        assertEquals("1", ngv.get("graphs_blocked"));
        assertEquals("3", ngv.get("summaries_distinct"));
        assertEquals("6", ngv.get("max_visible_events"));
        assertEquals("5.500", ngv.get("mean_visible_events"));

        // ---- 5. online, conforming: fifo,2,1 against spec,2,1: 4 lookups all found, exhausted; R2: 0, 0, 0.
        Map<String, String> ok = block(online(fifo, spec), true);
        assertEquals("exhausted", ok.get("stop_reason"));
        assertEquals("0", ok.get("first_miss_iteration"));
        assertEquals("0", ok.get("first_miss_wall_ms"));
        assertEquals("0", ok.get("uncovered_total"));
        assertEquals("4", ok.get("iterations"));
        sameAsFile(ok, ff);
        for (String k : List.of("max_graph_events", "max_message_events", "max_porf_count", "max_porf_pairs")) {
            assertEquals(e.get(k), ok.get(k), "same graphs and summaries as the export: " + k);
        }
        assertEquals("0", ok.get("export_ms"), "no export file");

        // ---- 6. online (bug-finder mode since round 05), violating, miss before the last graph: double-grant,2,1
        // (6 graphs) against spec,2,1.
        // J7: NotFound, uncovered summary [0,0,0,0,1,1] or [0,1,1,0,0,0]; the miss is at iteration 1, so the only
        // summary is the uncovered one: max_porf_count 1, max_porf_pairs 2, visible 6; 12 message events.
        MpLauncherTest.Out bo = onlineStop(J4 + "A3Lock:double-grant,2,1", spec);
        Map<String, String> bad = block(bo, true);
        String at = J7OnlineTest.field(bo.text(), "at");
        assertTrue(at != null && at.matches("iteration [0-9]+, NotFound .*"), "at: " + at);
        String atIt = at.split("[ ,]")[1];
        assertTrue(J7OnlineTest.field(bo.text(), "graphs").endsWith("stack not exhausted"), bo.text());
        assertEquals("first-miss", bad.get("stop_reason"));
        assertEquals(atIt, bad.get("first_miss_iteration"), "first miss at the reported iteration");
        assertEquals("1", bad.get("uncovered_total"), "R2: stop at the miss, so 1 before OL1");
        assertEquals(atIt, bad.get("iterations"), "the run stops right after the miss");
        assertTrue(n(bad, "first_miss_wall_ms") <= n(bad, "wall_ms"), "first miss within the run");
        assertEquals("1", atIt, "observed in round 01 (not derived): the first graph misses");
        assertEquals("6", bad.get("max_visible_events"));
        assertEquals("1", bad.get("max_porf_count"));
        assertEquals("2", bad.get("max_porf_pairs"));
        assertEquals("12", bad.get("max_message_events"));

        // ---- 7. online (bug-finder mode since round 05), miss on the last graph: fwd A2Store:early-ack,2,1 (2 graphs) against A2Store:spec,2,1.
        // J7: NotFound at iteration 2, stack exhausted. The run stopped because of the miss: first-miss (OL-1 ruling).
        String a2spec = J4 + "A2Store:spec,2,1";
        block(export(a2spec), false);
        MpLauncherTest.Out lo = onlineStop(J4 + "A2Store:early-ack,2,1", exportPath(a2spec));
        Map<String, String> last = block(lo, true);
        String lat = J7OnlineTest.field(lo.text(), "at");
        assertTrue(lat != null && lat.matches("iteration 2, NotFound .*"), "at: " + lat);
        assertTrue(J7OnlineTest.field(lo.text(), "graphs").endsWith("stack exhausted"), lo.text());
        assertEquals("first-miss", last.get("stop_reason"), "a miss on the last graph still stops the run");
        assertEquals("2", last.get("first_miss_iteration"));
        assertEquals("1", last.get("uncovered_total"));
        assertEquals("2", last.get("iterations"));

        // ---- 7b (round 05). The same pair in full mode: the miss is not a stop; one graph uncovered of 2
        // (TraceForge J3: early-ack 2-1 Violation/1 (graphs 1)): exhausted, uncovered_total 1, first miss 2.
        Map<String, String> lastFull = block(online(J4 + "A2Store:early-ack,2,1", exportPath(a2spec)), true);
        assertEquals("exhausted", lastFull.get("stop_reason"), "full mode: a miss never stops the run");
        assertEquals("2", lastFull.get("first_miss_iteration"));
        assertEquals("1", lastFull.get("uncovered_total"));
        assertEquals("2", lastFull.get("iterations"));

        // ================================================================ round 03 (Codex OL0 round 01, M4)

        // ---- 8. varying graph sizes (hand count): Varying below. main: Nondet(2 options) then, on true, one send
        // to h; h: tryRecv. Graphs: false -> Nondet, h ⊥ (2 events); true -> Nondet, send, h ⊥ (3) and
        // Nondet, send, h reads (3). 3 complete graphs; max 3, mean 8/3 = 2.667; message events max 2.
        Map<String, String> va = block(counting(Varying.class.getName()), false);
        assertEquals("3", va.get("graphs_complete"));
        assertEquals("0", va.get("graphs_blocked"));
        assertEquals("3", va.get("iterations"));
        assertEquals("3", va.get("max_graph_events"));
        assertEquals("2.667", va.get("mean_graph_events"), "mean != max on graphs of different sizes");
        assertEquals("2", va.get("max_message_events"));
        counting0(va);

        // ---- 9. budget: Estimation:ns_nseqr,4 (H1: 24 complete) with 5 iterations: 5 distinct graphs, budget.
        Map<String, String> bu = block(MpLauncherTest.launch(LAUNCHER, H1 + "Estimation:ns_nseqr,4", "must", "5", "1",
                DIR + "report-budget", "-", "60"), false);
        assertEquals("5", bu.get("iterations"));
        assertEquals("5", bu.get("graphs_complete"));
        assertEquals("budget", bu.get("stop_reason"));
        // ---- 9b (round 04 addendum, owner ruling O1 = option (a)): a budget equal to the space is exhausted.
        // ns_nseqr,3 (6 graphs): must 6 explores all 6 and stops with no work left -> exhausted, "stack exhausted";
        // must 5 leaves work -> budget, "stack not exhausted"; must 7 -> exhausted with 6 iterations.
        MpLauncherTest.Out ebo = MpLauncherTest.launch(LAUNCHER, H1 + "Estimation:ns_nseqr,3", "must", "6", "1",
                DIR + "report-budget6", "-", "60");
        Map<String, String> eb = block(ebo, false);
        assertEquals("6", eb.get("iterations"));
        assertEquals("6", eb.get("graphs_complete"));
        assertTrue(J7OnlineTest.field(ebo.text(), "graphs").endsWith("stack exhausted"), ebo.text());
        assertEquals("exhausted", eb.get("stop_reason"), "O1 (a): a budget equal to the space is exhausted");
        MpLauncherTest.Out e5o = MpLauncherTest.launch(LAUNCHER, H1 + "Estimation:ns_nseqr,3", "must", "5", "1",
                DIR + "report-budget5", "-", "60");
        Map<String, String> e5 = block(e5o, false);
        assertEquals("5", e5.get("iterations"));
        assertTrue(J7OnlineTest.field(e5o.text(), "graphs").endsWith("stack not exhausted"), e5o.text());
        assertEquals("budget", e5.get("stop_reason"), "work remained");
        Map<String, String> e7 = block(MpLauncherTest.launch(LAUNCHER, H1 + "Estimation:ns_nseqr,3", "must", "7", "1",
                DIR + "report-budget7", "-", "60"), false);
        assertEquals("6", e7.get("iterations"));
        assertEquals("6", e7.get("graphs_complete"));
        assertEquals("exhausted", e7.get("stop_reason"), "the whole space within the budget: exhausted");
        // The export header follows: A3Lock:fifo,3,1 (J4: 30 graphs, 12 summaries). Budget 29 -> budget, bounded;
        // 30 -> exhausted, complete with all 12 summaries.
        String fifo31 = J4 + "A3Lock:fifo,3,1";
        for (int budget : new int[] {29, 30}) {
            Path bp = Path.of(DIR + "fifo31-budget" + budget + ".jsonl");
            Files.deleteIfExists(bp);
            Map<String, String> bv = block(MpLauncherTest.launch(LAUNCHER, fifo31, "must", String.valueOf(budget), "1",
                    DIR + "report-fifo31-" + budget, bp.toString(), "300"), false);
            String header = Files.readAllLines(bp, StandardCharsets.UTF_8).get(0);
            assertEquals(String.valueOf(budget), bv.get("iterations"));
            if (budget == 29) {
                assertEquals("budget", bv.get("stop_reason"));
                assertTrue(header.contains("\"enumeration\":\"bounded\""), header);
            } else {
                assertEquals("exhausted", bv.get("stop_reason"));
                assertTrue(header.contains("\"enumeration\":\"complete\""), header);
                assertEquals("12", bv.get("summaries_distinct"), "J4 constant");
            }
        }

        // ---- 10. timeout: A3Lock:double-grant,4,1 (2520 graphs, ~6 s here) with timeoutSeconds 1.
        String big = J4 + "A3Lock:double-grant,4,1";
        MpLauncherTest.Out to = MpLauncherTest.launch(LAUNCHER, big, "must", "0", "1", DIR + "report-timeout", "-", "1");
        Map<String, String> tov = block(to, false);
        assertEquals("timeout", tov.get("stop_reason"));
        assertTrue(n(tov, "iterations") < 2520, "stopped before the end");
        assertTrue(J7OnlineTest.field(to.text(), "graphs").endsWith("stack not exhausted"), to.text());

        // ---- 11. precedence error > timeout: the same timeout with an export to a directory (cannot write).
        Path dirTarget = Path.of(DIR + "a-directory.jsonl");
        Files.createDirectories(dirTarget);
        Map<String, String> te = block(MpLauncherTest.launch(LAUNCHER, big, "must", "0", "1", DIR + "report-timeout-x",
                dirTarget.toString(), "1"), false, 1, false);
        assertEquals("error", te.get("stop_reason"), "a failed run is an error even when it also timed out");

        // ---- 12. runtime error: a process throws (mutants.AssertsInProcess), counting must: exit 1, error.
        MpLauncherTest.Out re = MpLauncherTest.launch(LAUNCHER, "org.mpi_sws.jmc.mp.programs.mutants.AssertsInProcess",
                "must", "0", "1", DIR + "report-rt", "-", "60");
        Map<String, String> rev = block(re, false, 1, false);
        assertEquals("error", rev.get("stop_reason"));
        assertTrue(re.text().contains("FAILED:"), re.text());

        // ---- 13. export failure: fifo,2,1 exported to a directory: exit 1, error; the counters stay (2 summaries).
        Map<String, String> xf = block(MpLauncherTest.launch(LAUNCHER, fifo, "must", "0", "1", DIR + "report-xf",
                dirTarget.toString(), "300"), false, 1, false);
        assertEquals("error", xf.get("stop_reason"));
        assertEquals("2", xf.get("summaries_distinct"));
        assertEquals("4", xf.get("iterations"));

        // ---- 14. first miss + export failure (bug-finder mode since round 05): double-grant,2,1 --spec spec,2,1,
        // export to a directory. Precedence: error; the miss is still reported (iteration 1, as in case 6).
        MpLauncherTest.Out fx = MpLauncherTest.launch(LAUNCHER, J4 + "A3Lock:double-grant,2,1", "must", "0", "1",
                DIR + "report-fx", dirTarget.toString(), "300", "--spec", spec.toString(), "--stop-at-first");
        Map<String, String> fxv = block(fx, true, 1, false);
        assertEquals("error", fxv.get("stop_reason"));
        assertEquals("1", fxv.get("first_miss_iteration"));
        assertEquals("1", fxv.get("uncovered_total"));
        assertEquals("1", fxv.get("iterations"));

        // ---- 15. refused specification: spec,2,1's lines repeated 50 000 times, then a malformed line.
        // Exit 2, one block, error, spec keys present (0, 0, 0), nothing explored; index_ms > 0 (parsing
        // ~100 000 lines before the bad one is counted although the load fails).
        List<String> sl = Files.readAllLines(spec, StandardCharsets.UTF_8);
        Path malformed = Path.of(DIR + "malformed-spec.jsonl");
        try (java.io.BufferedWriter w = Files.newBufferedWriter(malformed, StandardCharsets.UTF_8)) {
            w.write(sl.get(0));
            w.newLine();
            for (int i = 0; i < 50_000; i++) {
                for (String l : sl.subList(1, sl.size())) {
                    w.write(l);
                    w.newLine();
                }
            }
            w.write("{this is not a summary");
            w.newLine();
        }
        MpLauncherTest.Out rf = online(fifo, malformed);
        Map<String, String> rfv = block(rf, true, 2, false);
        assertTrue(rf.text().contains("refused"), rf.text());
        assertEquals("error", rfv.get("stop_reason"));
        assertEquals("0", rfv.get("first_miss_iteration"));
        assertEquals("0", rfv.get("first_miss_wall_ms"));
        assertEquals("0", rfv.get("uncovered_total"));
        assertEquals("0", rfv.get("iterations"));
        assertEquals("0", rfv.get("graphs_complete"));
        assertTrue(n(rfv, "index_ms") > 0, "the failed load's time is counted (finally)");

        // ---- 16. phase times on a large export: A3Lock:double-grant,3,1 (J4: 90 complete, 36 summaries).
        Map<String, String> ph = block(export(J4 + "A3Lock:double-grant,3,1"), false);
        assertEquals("90", ph.get("graphs_complete"));
        assertEquals("36", ph.get("summaries_distinct"));
        assertTrue(n(ph, "summarise_ms") > 0, "summarising 90 graphs takes time");
        assertTrue(n(ph, "explore_ms") > 0, "exploring 90 graphs takes time");
        // index_ms > 0 with a (small) spec: the conforming run of case 5.
        assertTrue(n(ok, "index_ms") > 0, "loading the spec takes time");

        // ---- 18 (round 04, Codex M5). --spec with a plain Runnable (Varying, not an MpProgram): rejected
        // before the path is opened (it does not exist). Exit 2, one block, error, nothing explored, spec keys
        // present and 0/0/0 (no miss).
        MpLauncherTest.Out pr = MpLauncherTest.launch(LAUNCHER, Varying.class.getName(), "must", "0", "1",
                DIR + "report-plain-spec", "-", "60", "--spec", DIR + "no-such-spec.jsonl");
        Map<String, String> prv = block(pr, true, 2, false);
        assertEquals("error", prv.get("stop_reason"), "a rejected invocation is an error, not budget");
        assertEquals("0", prv.get("iterations"));
        assertEquals("0", prv.get("graphs_complete"));
        assertEquals("0", prv.get("first_miss_iteration"));
        assertEquals("0", prv.get("first_miss_wall_ms"));
        assertEquals("0", prv.get("uncovered_total"));

        // ---- 17. peak_rss_kb empty: the child runs in a user+mount+pid namespace with /dev/null bound over
        // /proc/1/status (= /proc/self/status of the exec'd JVM). Skipped (printed) if unshare is unavailable.
        if (new ProcessBuilder("unshare", "-rmpf", "--mount-proc", "true").start().waitFor() == 0) {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath().toString();
            String cmd = "mount --bind /dev/null /proc/1/status && exec " + java + " -cp '"
                    + System.getProperty("java.class.path") + "' " + LAUNCHER + " " + H1
                    + "Litmus:r_nondet must 0 1 " + DIR + "report-rss - 60";
            MpLauncherTest.Out rs = run(List.of("unshare", "-rmpf", "--mount-proc", "sh", "-c", cmd));
            Map<String, String> rsv = block(rs, false, 0, true);
            assertEquals("exhausted", rsv.get("stop_reason"));
        } else {
            System.out.println("OL SKIPPED peak_rss_kb fallback: unshare -rmpf not permitted here");
        }
    }

    /**
     * OL round 05 (MESSAGE-online-mode.md §1): full mode (default) and bug-finder mode ({@code --stop-at-first}).
     * Expected values (derived before the runs): TraceForge's offline J3 table (plan/traceForge/log/dev/J3.md
     * §6 and the graph table): A3Lock double-grant 2-2 has 70 graphs, 34 distinct summaries, and against
     * spec 2-2 is "Violation/28 (graphs 64)": 64 uncovered graphs, 28 distinct uncovered summaries, so 6
     * graphs covered; fifo 2-2 has 28 graphs, 6 summaries, "Conforms/0".
     */
    @Test
    void onlineModes() throws Exception {
        String spec22 = J4 + "A3Lock:spec,2,2";
        block(export(spec22), false);
        Path spec = exportPath(spec22);
        String dg = J4 + "A3Lock:double-grant,2,2";

        // ---- 1. full mode, violating, with an export beside it.
        Path beside = Path.of(DIR + "dg22-full-beside.jsonl");
        Files.deleteIfExists(beside);
        MpLauncherTest.Out fo = MpLauncherTest.launch(LAUNCHER, dg, "must", "0", "1", DIR + "report-dg22-full",
                beside.toString(), "300", "--spec", spec.toString());
        Map<String, String> f = block(fo, true);
        String t = fo.text();
        assertEquals("reports", J7OnlineTest.field(t, "verdict"));
        assertEquals("full", J7OnlineTest.field(t, "mode"));
        assertEquals("64 graphs (28 distinct summaries)", J7OnlineTest.field(t, "uncovered"),
                "TraceForge offline: Violation/28 (graphs 64)");
        assertEquals("70 iterations", J7OnlineTest.field(t, "ran"));
        assertEquals("70 distinct complete, 0 distinct blocked; stack exhausted", J7OnlineTest.field(t, "graphs"));
        Matcher lk = Pattern.compile("70 \\(6 found, ([0-9]+) signature misses, ([0-9]+) not found\\)")
                .matcher(J7OnlineTest.field(t, "lookups"));
        assertTrue(lk.matches(), "every graph looked up, 6 covered: " + J7OnlineTest.field(t, "lookups"));
        assertEquals(64, Integer.parseInt(lk.group(1)) + Integer.parseInt(lk.group(2)));
        String at = J7OnlineTest.field(t, "at");
        assertTrue(at != null && at.matches("iteration [0-9]+, (NoSig|NotFound) .*"), "at: " + at);
        String firstIt = at.split("[ ,]")[1];
        Matcher fm = Pattern.compile("iteration ([0-9]+), ([0-9]+) ms").matcher(J7OnlineTest.field(t, "first miss"));
        assertTrue(fm.matches(), "first miss: " + J7OnlineTest.field(t, "first miss"));
        assertEquals(firstIt, fm.group(1), "first miss = the witness's iteration");
        assertTrue(J7OnlineTest.field(t, "witness") != null, "the first report is kept");
        assertEquals("exhausted", f.get("stop_reason"), "full mode: a miss never stops the run");
        assertEquals("64", f.get("uncovered_total"));
        assertEquals(firstIt, f.get("first_miss_iteration"));
        assertEquals(fm.group(2), f.get("first_miss_wall_ms"));
        assertEquals("70", f.get("iterations"));
        assertEquals("34", f.get("summaries_distinct"));
        List<String> bl = Files.readAllLines(beside, StandardCharsets.UTF_8);
        String h = bl.get(0);
        assertEquals(35, bl.size(), "34 summaries: the full exploration is exported");
        assertTrue(h.contains("\"enumeration\":\"complete\""), h);
        assertTrue(h.contains(",\"first_miss_iteration\":" + firstIt + ",\"first_miss_wall_ms\":" + fm.group(2)
                + ",\"uncovered_total\":64,\"encoding\":1}"), "the three header fields: " + h);

        // ---- 2. full mode, conforming: fifo,2,2 (28 graphs): uncovered 0, no first-miss line, no witness.
        MpLauncherTest.Out co = online(J4 + "A3Lock:fifo,2,2", spec);
        Map<String, String> c = block(co, true);
        String ct = co.text();
        assertEquals("conforms", J7OnlineTest.field(ct, "verdict"));
        assertEquals("full", J7OnlineTest.field(ct, "mode"));
        assertEquals("0 graphs (0 distinct summaries)", J7OnlineTest.field(ct, "uncovered"));
        assertEquals(null, J7OnlineTest.field(ct, "first miss"), "no first-miss line");
        assertEquals(null, J7OnlineTest.field(ct, "witness"));
        assertEquals(null, J7OnlineTest.field(ct, "at"));
        assertEquals("28 (28 found, 0 signature misses, 0 not found)", J7OnlineTest.field(ct, "lookups"));
        assertEquals("exhausted", c.get("stop_reason"));
        assertEquals("0", c.get("uncovered_total"));
        assertEquals("0", c.get("first_miss_iteration"));
        assertEquals("0", c.get("first_miss_wall_ms"));
        assertEquals("28", c.get("iterations"));

        // ---- 3. bug-finder mode on the violating pair: stops at the first miss (the same iteration as full mode's
        // first miss: the exploration order does not depend on the mode).
        MpLauncherTest.Out so = onlineStop(dg, spec);
        Map<String, String> sv = block(so, true);
        String st = so.text();
        assertEquals("reports", J7OnlineTest.field(st, "verdict"));
        assertEquals("bug-finder (--stop-at-first)", J7OnlineTest.field(st, "mode"));
        assertEquals("1 graphs (1 distinct summaries)", J7OnlineTest.field(st, "uncovered"));
        assertEquals("first-miss", sv.get("stop_reason"));
        assertEquals("1", sv.get("uncovered_total"));
        assertEquals(firstIt, sv.get("first_miss_iteration"));
        assertEquals(firstIt, sv.get("iterations"));
        assertEquals(J7OnlineTest.field(t, "witness"), J7OnlineTest.field(st, "witness"), "the same first report");
        assertTrue(J7OnlineTest.field(st, "graphs").endsWith("stack not exhausted"), st);

        // ---- 4. --stop-at-first without --spec: a usage error (exit 2) before any exploration.
        MpLauncherTest.Out ue = MpLauncherTest.launch(LAUNCHER, dg, "must", "0", "1", DIR + "report-usage", "-", "300",
                "--stop-at-first");
        assertEquals(2, ue.exit(), ue.text());
        assertTrue(ue.text().contains("--stop-at-first needs --spec"), ue.text());
        assertTrue(!ue.text().contains("verdict:"), ue.text());

        // ---- 5. full mode, budget with a miss: 10 iterations of double-grant,2,2. The first miss is at
        // iteration firstIt (<= 10 if it is 1): budget (work remains), reports, 1 <= uncovered_total <= 10 and
        // equal to the human count; 10 lookups.
        MpLauncherTest.Out bo = MpLauncherTest.launch(LAUNCHER, dg, "must", "10", "1", DIR + "report-dg22-budget", "-",
                "300", "--spec", spec.toString());
        Map<String, String> b = block(bo, true);
        String btx = bo.text();
        assertTrue(Integer.parseInt(firstIt) <= 10, "the derivation needs the miss within 10 iterations: " + firstIt);
        assertEquals("budget", b.get("stop_reason"));
        assertEquals("10", b.get("iterations"));
        assertEquals("reports", J7OnlineTest.field(btx, "verdict"));
        long u = n(b, "uncovered_total");
        assertTrue(u >= 1 && u <= 10, "uncovered_total " + u);
        assertTrue(J7OnlineTest.field(btx, "uncovered").startsWith(u + " graphs ("), J7OnlineTest.field(btx, "uncovered"));
        assertTrue(J7OnlineTest.field(btx, "lookups").startsWith("10 ("), J7OnlineTest.field(btx, "lookups"));
        assertTrue(J7OnlineTest.field(btx, "graphs").endsWith("stack not exhausted"), btx);
    }

    /** Runs a command (not the launcher's java wrapper) and returns exit and output. */
    static MpLauncherTest.Out run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        Thread pump = new Thread(() -> {
            try (java.io.InputStream in = p.getInputStream()) {
                in.transferTo(buf);
            } catch (java.io.IOException ignored) {
                // process ended
            }
        });
        pump.start();
        if (!p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
        }
        pump.join(5000);
        String text = buf.toString(StandardCharsets.UTF_8);
        System.out.println("RUN " + cmd.get(0) + " -> exit " + p.exitValue() + "\n" + text);
        return new MpLauncherTest.Out(p.exitValue(), text);
    }

    /** Round 03 fixture: graphs of different sizes (see case 8). */
    public static final class Varying implements Runnable {
        /** A payload. */
        record I(int v) implements org.mpi_sws.jmc.mp.events.MpMessage {}

        /** For the launcher. */
        public Varying() {}

        @Override
        public void run() {
            org.mpi_sws.jmc.api.mp.MpThread h = new org.mpi_sws.jmc.api.mp.MpThread(
                    () -> org.mpi_sws.jmc.api.mp.Channel.tryRecv(org.mpi_sws.jmc.mp.events.CommunicationModel.P2P));
            h.start();
            if (org.mpi_sws.jmc.api.mp.Nondet.chooseBoolean()) {
                org.mpi_sws.jmc.api.mp.Channel.send(org.mpi_sws.jmc.mp.events.CommunicationModel.P2P,
                        org.mpi_sws.jmc.mp.events.Tid.of(h.getTaskId()), new I(1));
            }
            try {
                h.join1();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
