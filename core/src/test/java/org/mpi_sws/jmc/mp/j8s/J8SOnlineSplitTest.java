package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * J8S tester round 02, risk 15 (J8R-PLAN revision 7 §5 row 15) through the launcher: every fixture of the
 * round-01 derivation at W ∈ {1, 4, 16} × K ∈ {1, 4, 8·W}, against the sequential run of the same row and
 * spec and against the derived values (uncovered total, distinct, first-miss iteration, verdict); plus the
 * root-replay, one-JVM, repeated-summary, no-split, no-export and fault cases. Each split launch is a fresh
 * coordinator JVM; three launches run at a time.
 */
class J8SOnlineSplitTest {

    /** A fixture: label, program, spec, derived uncovered total, distinct, first-miss iteration. */
    record Fx(String label, String program, Path spec, long unc, long dist, long first) {}

    static List<Fx> bankFixtures(int from, int to) throws Exception {
        List<Fx> all = List.of(
                new Fx("F1 bank2-root", J8SSupport.BANK, J8SSupport.bankSpec("root", 1), 1, 1, 1),
                new Fx("F2 bank2-itemNm1", J8SSupport.BANK, J8SSupport.bankSpec("itemNm1", 2), 1, 1, 2),
                new Fx("F3 bank2-middle", J8SSupport.BANK, J8SSupport.bankSpec("middle", 5), 1, 1, 5),
                new Fx("F4 bank2-item0", J8SSupport.BANK, J8SSupport.bankSpec("item0", 9), 1, 1, 9),
                new Fx("F5 bank2-multi", J8SSupport.BANK, J8SSupport.bankSpec("multi", 3, 5, 9, 12), 4, 4, 3),
                new Fx("F6 bank2-w1seq", J8SSupport.BANK, J8SSupport.bankSpec("w1seq", 3, 8), 2, 2, 3),
                new Fx("F7 bank2-none", J8SSupport.BANK, J8SSupport.bankSpec("none"), 0, 0, 0));
        return all.subList(from, to);
    }

    static List<Fx> j4Fixtures() throws Exception {
        Path a3spec = J8SSupport.j4Spec("A3Lock:spec,3,1");
        return List.of(
                new Fx("F8 double-grant,3,1", J8SSupport.J4 + "A3Lock:double-grant,3,1", a3spec, 84, 30, 1),
                new Fx("F9 early-ack,2,1", J8SSupport.J4 + "A2Store:early-ack,2,1", J8SSupport.j4Spec("A2Store:spec,2,1"),
                        1, 1, 2),
                new Fx("F10 never-grant,3,1", J8SSupport.J4 + "A3Lock:never-grant,3,1", a3spec, 10, 6, 1),
                new Fx("F11 fifo,3,1", J8SSupport.J4 + "A3Lock:fifo,3,1", a3spec, 0, 0, 0),
                new Fx("F12 rev wrong-round,3,1", J8SSupport.J4 + "A3Lock:spec,3,1",
                        J8SSupport.j4Spec("A3Lock:wrong-round,3,1"), 6, 6, 1));
    }

    static void matrix(List<Fx> fixtures) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<String> bad = Collections.synchronizedList(new ArrayList<>());
        List<String> evidence = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> fs = new ArrayList<>();
        for (Fx f : fixtures) {
            J8SSupport.Out seq = J8SSupport.launch(f.program(), true, "--spec", f.spec().toString());
            for (int w : new int[] {1, 4, 16}) {
                for (int k : new int[] {1, 4, 8 * w}) {
                    fs.add(pool.submit(() -> {
                        J8SSupport.Out sp = J8SSupport.launch(f.program(), true, "--spec", f.spec().toString(),
                                "--split", Integer.toString(w), "--k", Integer.toString(k));
                        String label = f.label() + " W=" + w + " K=" + k;
                        List<String> b = J8SSupport.compare(label, seq, sp, f.unc(), f.dist(), f.first());
                        bad.addAll(b);
                        evidence.add(label + " | " + sp.field("split") + " | first miss " + sp.field("first miss")
                                + " | uncovered " + sp.field("uncovered") + (b.isEmpty() ? " | PASS" : " | FAIL"));
                        return null;
                    }));
                }
            }
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        List<String> sorted = new ArrayList<>(evidence);
        Collections.sort(sorted);
        sorted.forEach(x -> System.out.println("J8S " + x.replaceAll("run directory \\S+", "")));
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
        assertEquals(fixtures.size() * 9, evidence.size());
    }

    @Test
    void matrix_bank2_F1toF4() throws Exception {
        matrix(bankFixtures(0, 4));
    }

    @Test
    void matrix_bank2_F5toF7() throws Exception {
        matrix(bankFixtures(4, 7));
    }

    @Test
    void matrix_j4_F8toF12() throws Exception {
        matrix(j4Fixtures());
    }

    /** The {@code online} object of {@code item-<j>.json} in a run directory. */
    static JsonObject onlineOf(Path runDir, int j) throws Exception {
        return JsonParser.parseString(Files.readString(runDir.resolve("item-" + j + ".json"))).getAsJsonObject()
                .getAsJsonObject("online");
    }

    static long field(JsonObject online, String key) {
        return online.get(key).getAsLong();
    }

    /**
     * Root-replay misses discarded (F1, K 1, W 4: uncovered 1, not 1 + 4); one JVM serving miss, empty,
     * no-miss, different miss (F6, K 4, W 1: items 3, 2, 1, 0; round-01 derivation: item3 uncovered 1 at
     * first_local 1, item2 empty, item1 no miss, item0 uncovered 1 at first_local 2); repeated summaries
     * across segments (F8, K 1: Σ per-segment distinct lines 34, union 30); root-only (F1, K 8: no split,
     * equal to sequential); an online run without an export (F5).
     */
    @Test
    void segments_rootReplay_oneJvm_repeated_rootOnly_noExport() throws Exception {
        List<String> bad = new ArrayList<>();
        Path root = J8SSupport.bankSpec("root", 1);
        J8SSupport.Out seq1 = J8SSupport.launch(J8SSupport.BANK, true, "--spec", root.toString());
        J8SSupport.Out r1 = J8SSupport.launch(J8SSupport.BANK, true, "--spec", root.toString(), "--split", "4", "--k", "1");
        bad.addAll(J8SSupport.compare("root replay F1 W4 K1", seq1, r1, 1, 1, 1));
        J8SSupport.Out r8 = J8SSupport.launch(J8SSupport.BANK, true, "--spec", root.toString(), "--split", "1", "--k", "8");
        bad.addAll(J8SSupport.compare("root only F1 W1 K8", seq1, r8, 1, 1, 1));
        if (!r8.field("split").contains("no split")) {
            bad.add("root only: split " + r8.field("split"));
        }
        // one JVM, F6, K 4, W 1
        Path w1 = J8SSupport.bankSpec("w1seq", 3, 8);
        J8SSupport.Out seq6 = J8SSupport.launch(J8SSupport.BANK, true, "--spec", w1.toString());
        J8SSupport.Out r6 = J8SSupport.launch(J8SSupport.BANK, true, "--spec", w1.toString(), "--split", "1", "--k", "4");
        bad.addAll(J8SSupport.compare("one JVM F6 W1 K4", seq6, r6, 2, 2, 3));
        long[][] expect = {{3, 1, 1}, {2, 0, 0}, {1, 0, 0}, {0, 1, 2}};
        for (long[] e : expect) {
            JsonObject o = onlineOf(r6.runDir(), (int) e[0]);
            String t = o.toString();
            System.out.println("J8S one JVM item " + e[0] + ": " + t.substring(0, Math.min(400, t.length())));
            if (field(o, "uncovered") != e[1] || field(o, "first_local") != e[2]
                    || (e[1] == 0 && (field(o, "first_at_ms") != -1 || o.getAsJsonArray("lines").size() != 0
                            || !o.get("witness").isJsonNull() || !o.get("answer").isJsonNull()))) {
                bad.add("one JVM item " + e[0] + ": " + o);
            }
        }
        // repeated summaries, F8 K 1 W 4
        Path a3spec = J8SSupport.j4Spec("A3Lock:spec,3,1");
        String dg = J8SSupport.J4 + "A3Lock:double-grant,3,1";
        J8SSupport.Out seq8 = J8SSupport.launch(dg, true, "--spec", a3spec.toString());
        J8SSupport.Out r8b = J8SSupport.launch(dg, true, "--spec", a3spec.toString(), "--split", "4", "--k", "1");
        bad.addAll(J8SSupport.compare("repeated F8 W4 K1", seq8, r8b, 84, 30, 1));
        int sum = 0;
        for (int j = 0; j < 9; j++) {
            sum += onlineOf(r8b.runDir(), j).getAsJsonArray("lines").size();
        }
        // the root's segment holds one distinct line (round 01)
        System.out.println("J8S repeated: Σ item distinct lines " + sum + " + root 1; union " + r8b.field("uncovered"));
        if (sum + 1 != 34) {
            bad.add("repeated: Σ per-segment distinct " + (sum + 1) + ", derived 34");
        }
        // online without export, F5
        Path multi = J8SSupport.bankSpec("multi", 3, 5, 9, 12);
        J8SSupport.Out seq5 = J8SSupport.launch(J8SSupport.BANK, false, "--spec", multi.toString());
        J8SSupport.Out r5 = J8SSupport.launch(J8SSupport.BANK, false, "--spec", multi.toString(), "--split", "4", "--k", "1");
        bad.addAll(J8SSupport.compare("no export F5 W4 K1", seq5, r5, 4, 4, 3));
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * Faults on the segment holding the first miss: F4 (item 0) and F3 (item 1) at K 1, W 1 and 4; F8 on
     * item 8 (its first item miss). die-after: not retried; die-before: retried once. Equal to sequential.
     */
    @Test
    void faults_onTheSegmentHoldingTheFirstMiss() throws Exception {
        List<String> bad = new ArrayList<>();
        Path a3spec = J8SSupport.j4Spec("A3Lock:spec,3,1");
        Object[][] rows = {
            {"F4", J8SSupport.BANK, J8SSupport.bankSpec("item0", 9), 0, 1L, 1L, 9L},
            {"F3", J8SSupport.BANK, J8SSupport.bankSpec("middle", 5), 1, 1L, 1L, 5L},
            {"F8", J8SSupport.J4 + "A3Lock:double-grant,3,1", a3spec, 8, 84L, 30L, 1L},
        };
        for (Object[] r : rows) {
            String program = (String) r[1];
            Path spec = (Path) r[2];
            int j = (int) r[3];
            J8SSupport.Out seq = J8SSupport.launch(program, true, "--spec", spec.toString());
            for (String kind : List.of("die-after", "die-before")) {
                for (String w : List.of("1", "4")) {
                    J8SSupport.Out o = J8SSupport.launch(List.of("-Djmc.split.fault=" + kind + ":" + j + ":0"), program,
                            true, "--spec", spec.toString(), "--split", w, "--k", "1");
                    String label = r[0] + " " + kind + ":" + j + ":0 W=" + w;
                    bad.addAll(J8SSupport.compare(label, seq, o, (long) r[4], (long) r[5], (long) r[6]));
                    boolean retried = o.runDir() != null && Files.exists(o.runDir().resolve("assigned/" + j + ".1.json"));
                    if (retried != kind.equals("die-before")) {
                        bad.add(label + ": retried " + retried);
                    }
                    System.out.println("J8S fault " + label + ": exit " + o.exit() + " first miss " + o.field("first miss")
                            + " uncovered " + o.field("uncovered") + " retried " + retried);
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
