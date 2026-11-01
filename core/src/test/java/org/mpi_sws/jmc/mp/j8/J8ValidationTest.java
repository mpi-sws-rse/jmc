package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * J8 validation (J8-PLAN revision 8 §7 and the brief), tester round 04: every H1 row and every A3/A2
 * row at W = 1, 4, 16 with the default K (8·W): distinct complete and blocked counts, ran, and (A3/A2)
 * the export byte-identical to sequential must, header included. Expected values: the sequential
 * launcher run in the same invocation, and the round-01 derivation ({@code step0-expected.tsv}: counts,
 * ran, the md5 of the sequential export). Per-row results go to {@code build/test-results/j8-tester-r4/
 * validation-<group>.csv}. Four rows run at a time; each row's launches run one after another.
 */
class J8ValidationTest {

    record Row(String row, String mode, int ran, int complete, int blocked, String md5) {}

    static Map<String, Row> rows(String prefix) throws Exception {
        Map<String, Row> out = new LinkedHashMap<>();
        for (J8Step0Test.Exp e : J8Step0Test.expectations()) {
            if (matches(e.row(), prefix)) {
                out.putIfAbsent(e.row(), new Row(e.row(), e.mode(), e.ran(), e.complete(), e.blocked(), e.md5()));
            }
        }
        return out;
    }

    static boolean matches(String row, String groups) {
        for (String g : groups.split("\\|")) {
            if (row.startsWith(g)) {
                return true;
            }
        }
        return false;
    }

    static List<String> check(Row r, List<String> csv, String k) throws Exception {
        List<String> bad = new ArrayList<>();
        boolean export = r.mode().equals("export");
        String graphs = r.complete() + " distinct complete, " + r.blocked() + " distinct blocked; stack exhausted";
        J8Launch.Out seq = J8Launch.launch(r.row(), export);
        String seqMd5 = seq.exportMd5();
        if (seq.exit() != 0 || !graphs.equals(seq.field("graphs")) || !(r.ran() + " iterations").equals(seq.field("ran"))
                || (export && !seqMd5.equals(r.md5()))) {
            bad.add(r.row() + ": sequential differs from the derivation: " + seq.field("graphs") + " / "
                    + seq.field("ran") + " / " + seqMd5);
        }
        csv.add(String.join(",", q(r.row()), r.mode(), "seq", "-", "-", "-", "-", q(seq.field("graphs")),
                q(seq.field("ran")), seqMd5, bad.isEmpty() ? "PASS" : "FAIL"));
        for (int w : new int[] {1, 4, 16}) {
            J8Launch.Out o = k == null ? J8Launch.launch(r.row(), export, "--split", Integer.toString(w))
                    : J8Launch.launch(r.row(), export, "--split", Integer.toString(w), "--k", k);
            String split = o.field("split");
            List<String> rb = new ArrayList<>();
            if (o.exit() != 0) {
                rb.add("exit " + o.exit() + ": " + o.field("FAILED"));
            }
            if (!graphs.equals(o.field("graphs")) || !graphs.equals(seq.field("graphs"))) {
                rb.add("graphs " + o.field("graphs"));
            }
            if (o.field("ran") == null || !o.field("ran").equals(seq.field("ran"))) {
                rb.add("ran " + o.field("ran"));
            }
            if (o.field("blocked") == null || !o.field("blocked").equals(seq.field("blocked"))) {
                rb.add("blocked " + o.field("blocked"));
            }
            if (export && !o.exportMd5().equals(seqMd5)) {
                rb.add("export md5 " + o.exportMd5() + " vs " + seqMd5);
            }
            String kSeen = split == null ? "-" : split.replaceAll("^K (\\d+),.*", "$1");
            String rr = split == null ? "-" : split.replaceAll(".*, R (\\d+),.*", "$1");
            String n = split == null ? "-" : split.replaceAll(".*, N (\\d+).*", "$1");
            boolean didSplit = split != null && !split.contains("no split");
            csv.add(String.join(",", q(r.row()), r.mode(), "W" + w, kSeen, Boolean.toString(didSplit), didSplit ? rr : "-",
                    didSplit ? n : "-", q(o.field("graphs")), q(o.field("ran")), export ? o.exportMd5() : "-",
                    rb.isEmpty() ? "PASS" : "FAIL " + String.join("; ", rb)));
            rb.forEach(x -> bad.add(r.row() + " W=" + w + ": " + x));
        }
        return bad;
    }

    static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "'")) + "\"";
    }

    static void group(String name, String prefixes) throws Exception {
        group(name, prefixes, null, 4);
    }

    static void group(String name, String prefixes, String k, int parallel) throws Exception {
        Map<String, Row> rows = rows(prefixes);
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        List<String> csv = Collections.synchronizedList(new ArrayList<>());
        List<String> bad = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> fs = new ArrayList<>();
        long t0 = System.nanoTime();
        for (Row r : rows.values()) {
            fs.add(pool.submit(() -> {
                try {
                    bad.addAll(check(r, csv, k));
                } catch (Exception e) {
                    bad.add(r.row() + ": " + e);
                }
                return null;
            }));
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        List<String> sorted = new ArrayList<>(csv);
        Collections.sort(sorted);
        sorted.add(0, "row,mode,run,K,split,R,N,graphs,ran,export_md5,verdict");
        Files.createDirectories(J8Launch.DIR);
        Files.write(J8Launch.DIR.resolve("validation-" + name + ".csv"), sorted, StandardCharsets.UTF_8);
        System.out.printf("J8R4 validation %s: %d rows, %d problems, %.1f s%n", name, rows.size(), bad.size(),
                (System.nanoTime() - t0) / 1e9);
        bad.forEach(b -> System.out.println("PROBLEM " + b));
        assertEquals(List.of(), bad, name);
    }

    private static final String H1 = J8Launch.H1;

    @Test
    void validation_h1_part1() throws Exception {
        group("h1-part1", H1 + "Actors:|" + H1 + "ActorChannels:|" + H1 + "ChannelSemantics:|" + H1 + "Estimation:|"
                + H1 + "ExecutionOrder:|" + H1 + "H1Repro:|" + H1 + "JobQueue:");
    }

    @Test
    void validation_h1_part2() throws Exception {
        group("h1-part2", H1 + "Litmus:|" + H1 + "LitmusChannels:|" + H1 + "Micro:|" + H1 + "Minibus:");
    }

    @Test
    void validation_h1_part3() throws Exception {
        group("h1-part3", H1 + "RuPaxos:|" + H1 + "SemanticsComparison:|" + H1 + "SmallTests:|" + H1 + "Tags:|"
                + H1 + "TwoPc:|" + H1 + "VecTags:|" + H1 + "WorkTerminate:");
    }

    @Test
    void validation_j4() throws Exception {
        group("j4", J8Launch.J4 + "A3Lock:|" + J8Launch.J4 + "A2Store:");
    }

    // Tester addition: at the default K (8·W) no row splits for W = 4 and 16 (round-01 table: max stack 24),
    // so the same rows are also run at K = 1 with W = 4 and W = 16, where every row with S1 >= 1 splits.

    @Test
    void validationK1_h1_part1() throws Exception {
        group("k1-h1-part1", H1 + "Actors:|" + H1 + "ActorChannels:|" + H1 + "ChannelSemantics:|" + H1 + "Estimation:|"
                + H1 + "ExecutionOrder:|" + H1 + "H1Repro:|" + H1 + "JobQueue:", "1", 2);
    }

    @Test
    void validationK1_h1_part2() throws Exception {
        group("k1-h1-part2", H1 + "Litmus:|" + H1 + "LitmusChannels:|" + H1 + "Micro:|" + H1 + "Minibus:", "1", 2);
    }

    @Test
    void validationK1_h1_part3() throws Exception {
        group("k1-h1-part3", H1 + "RuPaxos:|" + H1 + "SemanticsComparison:|" + H1 + "SmallTests:|" + H1 + "Tags:|"
                + H1 + "TwoPc:|" + H1 + "VecTags:|" + H1 + "WorkTerminate:", "1", 2);
    }

    @Test
    void validationK1_j4() throws Exception {
        group("k1-j4", J8Launch.J4 + "A3Lock:|" + J8Launch.J4 + "A2Store:", "1", 2);
    }
}
