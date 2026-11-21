package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.fixtures.apps.A3Lock;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * P3 tester round 01 (plan/jmc-must/P3-PROPOSAL.md rev 3 §2 contract, §3 byte identity): guards. With the P3
 * change the export bytes and the deterministic stdout lines of a split run equal the sequential run's and
 * the golden values below, at {@code --split 2} and {@code --split 3} (K 1: several items per worker), also
 * with a worker restart ({@code die-after:0:0}: the restarted worker replays the root and must verify after
 * the transcript release) and a retry ({@code die-before:1:0}).
 *
 * <p>Golden values, all recorded at 4ce59e76 by tests that pass there (not re-measured here): graphs lines from
 * J8Step1Test t2 (fifo 30/0, never-grant 18/10, double-grant 90/0, JobQueue:pull,4 256/0); the launcher's
 * sorted export md5 of A3Lock:fifo,3,1 {@link J8Step1Test#A3FIFO_MD5}; the coordinator's merge-order export md5s
 * of the three A3Lock rows (J8Round06Test m2); fifo's items {@code {0=10, 1=10, 2=3, 3=4, 4=2}} (J8Round05Test).
 * The sorted export md5s of never-grant and double-grant are not pinned anywhere: they are compared with the
 * sequential run of the same build and printed ({@code P3 GOLDEN}) so the lead can compare the line between
 * 4ce59e76 and the change.
 *
 * <p>Compared lines: {@code ran}, {@code blocked}, {@code completed}, {@code graphs}, {@code exported} (without
 * the file path, which differs per launch); block keys {@code graphs_complete}, {@code graphs_blocked},
 * {@code summaries_distinct}, {@code iterations}, {@code stop_reason}; {@code items} (split only) equal between
 * the split runs of a row.
 *
 * <p>Round 02 (Codex code review P3 round 01, finding P3): every compared field is asserted present before its
 * value is compared, so a field missing on both sides is a failure. Ordinary lines: {@code ran}, {@code blocked},
 * {@code completed}, {@code graphs} on both sides; {@code exported} on both sides for an exporting row and absent
 * on both for a counting row (and the export file exists on both sides for an exporting row); {@code items} on
 * the split side. The measurement block must carry exactly {@link #KEYS}, in that order, once each, on both sides
 * (MpLauncher printMeasurements and printSplitMeasurements without a specification); compared by value:
 * {@link #BLOCK} and the graph-shape maxima {@link #MAXIMA} (equal to the sequential ones under split: J8 round
 * 10); the time, RSS and mean keys are volatile or empty under split and only checked for presence.
 */
class P3ByteIdentityTest {

    record Row(String program, boolean export, String graphs, String md5, String items) {}

    static final List<Row> ROWS = List.of(
            new Row(J8Step1Test.A3FIFO, true, "30 distinct complete, 0 distinct blocked; stack exhausted",
                    J8Step1Test.A3FIFO_MD5, "{0=10, 1=10, 2=3, 3=4, 4=2}"),
            new Row(J8Launch.J4 + "A3Lock:never-grant,3,1", true,
                    "18 distinct complete, 10 distinct blocked; stack exhausted", null, null),
            new Row(J8Launch.J4 + "A3Lock:double-grant,3,1", true,
                    "90 distinct complete, 0 distinct blocked; stack exhausted", null, null),
            new Row(J8Launch.H1 + "JobQueue:pull,4", false,
                    "256 distinct complete, 0 distinct blocked; stack exhausted", null, null));

    static final List<String> LINES = List.of("ran", "blocked", "completed", "graphs");
    static final List<String> BLOCK = List.of("graphs_complete", "graphs_blocked", "summaries_distinct", "iterations",
            "stop_reason");
    static final List<String> MAXIMA = List.of("max_message_events", "max_porf_pairs", "max_graph_events",
            "max_visible_events", "max_summary_bytes", "max_porf_count");
    /** The maxima measured on summarised graphs only: empty when the run summarises nothing. */
    static final java.util.Set<String> PER_SUMMARY = java.util.Set.of("max_message_events", "max_graph_events",
            "max_visible_events");
    /** The measurement block's keys without a specification, in print order (both launcher modes). */
    static final List<String> KEYS = List.of("wall_ms", "explore_ms", "summarise_ms", "index_ms", "lookup_ms",
            "export_ms", "graphs_complete", "graphs_blocked", "summaries_distinct", "iterations", "stop_reason",
            "peak_rss_kb", "max_message_events", "max_porf_pairs", "max_graph_events", "mean_graph_events",
            "max_visible_events", "mean_visible_events", "max_summary_bytes", "max_porf_count");

    /** The block's keys in print order. */
    static List<String> keys(J8Launch.Out o) {
        List<String> k = new ArrayList<>();
        for (String[] kv : o.block()) {
            k.add(kv[0]);
        }
        return k;
    }

    /** A presence problem, or nothing. */
    static void present(List<String> bad, String p, String side, J8Launch.Out o, String field, boolean expected) {
        if ((o.field(field) != null) != expected) {
            bad.add(p + side + " field '" + field + "' " + (expected ? "missing" : "present but not expected"));
        }
    }

    static Map<String, String> block(J8Launch.Out o) {
        Map<String, String> m = new TreeMap<>();
        for (String[] kv : o.block()) {
            m.put(kv[0], kv[1]);
        }
        return m;
    }

    static String exported(J8Launch.Out o) {
        String e = o.field("exported");
        return e == null ? null : e.substring(0, e.lastIndexOf(" to "));
    }

    /** The differences of a split launch from the sequential one and from the row's golden values. */
    static List<String> compare(Row row, J8Launch.Out seq, J8Launch.Out o, String label) throws Exception {
        List<String> bad = new ArrayList<>();
        String p = label + ": ";
        if (seq.exit() != 0 || o.exit() != 0) {
            bad.add(p + "exit " + seq.exit() + "/" + o.exit() + " " + o.field("FAILED"));
            return bad;
        }
        for (String k : LINES) {
            present(bad, p, "sequential", seq, k, true);
            present(bad, p, "split", o, k, true);
        }
        present(bad, p, "sequential", seq, "exported", row.export());
        present(bad, p, "split", o, "exported", row.export());
        present(bad, p, "split", o, "items", true);
        present(bad, p, "sequential", seq, "items", false);
        if (row.export() && (seq.exportFile() == null || !java.nio.file.Files.exists(seq.exportFile())
                || o.exportFile() == null || !java.nio.file.Files.exists(o.exportFile()))) {
            bad.add(p + "export file missing on a side");
        }
        if (!KEYS.equals(keys(seq))) {
            bad.add(p + "sequential block keys " + keys(seq) + ", expected " + KEYS);
        }
        if (!KEYS.equals(keys(o))) {
            bad.add(p + "split block keys " + keys(o) + ", expected " + KEYS);
        }
        if (!row.graphs().equals(seq.field("graphs")) || !row.graphs().equals(o.field("graphs"))) {
            bad.add(p + "graphs " + o.field("graphs") + " / sequential " + seq.field("graphs") + ", golden " + row.graphs());
        }
        for (String k : LINES) {
            if (!String.valueOf(seq.field(k)).equals(String.valueOf(o.field(k)))) {
                bad.add(p + k + " '" + o.field(k) + "' vs sequential '" + seq.field(k) + "'");
            }
        }
        if (!String.valueOf(exported(seq)).equals(String.valueOf(exported(o)))) {
            bad.add(p + "exported '" + exported(o) + "' vs sequential '" + exported(seq) + "'");
        }
        Map<String, String> a = block(seq);
        Map<String, String> b = block(o);
        for (String k : java.util.stream.Stream.concat(BLOCK.stream(), MAXIMA.stream()).toList()) {
            // without a summary (counting mode) the per-summary counters are printed empty on both sides
            boolean empty = !row.export() && PER_SUMMARY.contains(k);
            if (a.get(k) == null || b.get(k) == null || a.get(k).isEmpty() != empty || !a.get(k).equals(b.get(k))) {
                bad.add(p + "block " + k + " " + b.get(k) + " vs sequential " + a.get(k));
            }
        }
        if (row.export()) {
            if (!seq.exportMd5().equals(o.exportMd5())) {
                bad.add(p + "export md5 " + o.exportMd5() + " vs sequential " + seq.exportMd5());
            }
            if (row.md5() != null && !row.md5().equals(o.exportMd5())) {
                bad.add(p + "export md5 " + o.exportMd5() + " vs golden " + row.md5());
            }
        }
        if (row.items() != null && !row.items().equals(o.field("items"))) {
            bad.add(p + "items " + o.field("items") + " vs golden " + row.items());
        }
        System.out.println("P3 GOLDEN " + label + " export " + o.exportMd5() + " items " + o.field("items") + " ran "
                + o.field("ran") + " graphs " + o.field("graphs") + " exported " + exported(o) + " block "
                + BLOCK.stream().map(k -> k + "=" + b.get(k)).toList());
        return bad;
    }

    @Test
    void launcher_split2_split3_stdoutAndExport_equalSequentialAndGolden() throws Exception {
        List<String> bad = new ArrayList<>();
        for (Row row : ROWS) {
            J8Launch.Out seq = J8Launch.launch(row.program(), row.export());
            System.out.println("P3 GOLDEN " + row.program() + " sequential export " + seq.exportMd5());
            String items = null;
            for (String w : List.of("2", "3")) {
                J8Launch.Out o = J8Launch.launch(row.program(), row.export(), "--split", w, "--k", "1");
                bad.addAll(compare(row, seq, o, row.program() + " W=" + w));
                if (items == null) {
                    items = o.field("items");
                } else if (!items.equals(String.valueOf(o.field("items")))) {
                    bad.add(row.program() + ": items W=3 " + o.field("items") + " vs W=2 " + items);
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    @Test
    void launcher_workerRestartAndRetry_equalSequential() throws Exception {
        List<String> bad = new ArrayList<>();
        for (Row row : ROWS.subList(0, 2)) {
            J8Launch.Out seq = J8Launch.launch(row.program(), row.export());
            for (String fault : List.of("die-after:0:0", "die-before:1:0")) {
                J8Launch.Out o = J8Launch.launch(List.of("-Djmc.split.fault=" + fault), row.program(), row.export(),
                        "--split", "2", "--k", "1");
                bad.addAll(compare(row, seq, o, row.program() + " W=2 " + fault));
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /** The coordinator's API (merge order, unsorted), W 2 and 3: pinned md5s and counts. */
    @Test
    void api_mergeOrder_W2W3_pinnedGolden() throws Exception {
        String[][] rows = {
            {J8Round06Test.A3, "fifo", J8Round06Test.MD5, "30", "0"},
            {J8Launch.J4 + "A3Lock:double-grant,3,1", "double-grant", "45dc7076677f571c6885284aff1697ac", "90", "0"},
            {J8Launch.J4 + "A3Lock:never-grant,3,1", "never-grant", "09c394ff3f21d16459ea20e9fc094d29", "18", "10"},
        };
        List<String> bad = new ArrayList<>();
        for (String[] row : rows) {
            A3Lock mp = new A3Lock(row[1], "3", "1");
            for (int w : new int[] {2, 3}) {
                Path d = P3Commit.freshDir("api-" + row[1] + "-W" + w);
                SplitCoordinator.Result r = J8Round06Test.run(d, row[0], mp, w, false,
                        (x, id) -> J8Round06Test.realWorker(x, id, row[0], "p3-api"));
                String p = row[1] + " W=" + w + ": ";
                System.out.println("P3 api " + p + "failure " + r.failure() + " complete " + r.distinctComplete()
                        + " blocked " + r.distinctBlocked() + " ran " + r.ran() + " items " + r.itemIterations());
                if (r.failure() != null) {
                    bad.add(p + r.failure());
                    continue;
                }
                if (!row[2].equals(J8Round06Test.exportMd5(mp, r))) {
                    bad.add(p + "merge-order export " + J8Round06Test.exportMd5(mp, r) + " vs golden " + row[2]);
                }
                if (r.distinctComplete() != Integer.parseInt(row[3]) || r.distinctBlocked() != Integer.parseInt(row[4])) {
                    bad.add(p + "counts " + r.distinctComplete() + "/" + r.distinctBlocked());
                }
                if (row[1].equals("fifo") && !"{0=10, 1=10, 2=3, 3=4, 4=2}".equals(r.itemIterations().toString())) {
                    bad.add(p + "items " + r.itemIterations());
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
