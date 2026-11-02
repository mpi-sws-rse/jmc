package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * J8S tester round 04, Codex code-gate finding M3: the merged online result and the launcher's output
 * after an abort (owner ruling A2: a committed miss is reported, the totals are not, the first position
 * only when no segment before it is missing). Aborts are deterministic: the {@code fail} fault writes the
 * item's fail marker when it publishes. OnlineBank:2 at K 1: root {@code [1]}, item2 {@code [2,3]}, item1
 * {@code [4,5,6]}, item0 {@code [7..12]}; 12 complete graphs, one distinct summary each (J8S round 01), so
 * every iteration is one lookup.
 *
 * <p>Cases (derived before the run):
 * <ol>
 *   <li>Committed miss, no gap before it: spec without iteration 2 (item2, first_local 1), W 1, normal
 *       order (item2, item1, item0), {@code fail:0:0}. Committed: item2, item1. Online: miss, first 2,
 *       witness = iteration 2's line, answer as the sequential run's, totals null, lookups 1 + 2 + 3 = 6,
 *       found 5.</li>
 *   <li>Committed miss with an uncommitted segment before it: spec without iteration 9 (item0,
 *       first_local 3). API: W 1, reversed (item0, item1, item2), {@code fail:1:0}; committed: item0.
 *       Launcher (no reversed order): W 2, {@code delay:1:0,fail:1:0}: item1 starts 2000 ms late and then
 *       fails, while the other worker commits item2 and item0 (checked from the run directory). Online:
 *       miss, first −1 (launcher: "unknown (a segment before it is missing)", block empty), witness =
 *       iteration 9's line, answer as the sequential run's, totals null; API lookups 1 + 6 = 7, found 6.</li>
 *   <li>Abort without a committed miss: spec without iteration 9, W 1, normal order, {@code fail:0:0}.
 *       Committed: item2, item1 (no miss). Online: no miss, first 0, no witness or answer, wall −1, totals
 *       null, lookups 6, found 6; launcher verdict {@code partial}.</li>
 * </ol>
 * Every case: the failure is set (launcher: {@code FAILED:     incomplete: …}, exit 1, {@code stop_reason:
 * error}) and no worker is left.
 */
class J8SAbortOutputTest {

    record Case(String label, int drop, int w, boolean reversed, String fault, int first, Integer expectLookups,
                Integer expectFound, List<Integer> committed) {}

    static List<Integer> committed(Path d) throws Exception {
        List<Integer> out = new ArrayList<>();
        try (var s = Files.list(d)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.matches("item-\\d+\\.json"))
                    .map(n -> Integer.parseInt(n.substring(5, n.length() - 5))).sorted().forEach(out::add);
        }
        return out;
    }

    /** The sequential run's answer for the spec without the given iteration ("NoSig" or "NotFound"). */
    static String sequentialAnswer(Path spec, int drop) throws Exception {
        J8SSupport.Out seq = J8SSupport.launch(J8SSupport.BANK, false, "--spec", spec.toString());
        String at = seq.field("at");
        if (at == null || !at.startsWith("iteration " + drop + ", ")) {
            throw new IllegalStateException("sequential at: " + at);
        }
        return at.substring(("iteration " + drop + ", ").length()).split(" ")[0];
    }

    @Test
    void api_committedMiss_withAndWithoutGap_andNoMiss() throws Exception {
        List<Case> cases = List.of(
                new Case("1 no gap", 2, 1, false, "fail:0:0", 2, 6, 5, List.of(1, 2)),
                new Case("2 gap before", 9, 1, true, "fail:1:0", -1, 7, 6, List.of(0)),
                new Case("3 no miss", 9, 1, false, "fail:0:0", 0, 6, 6, List.of(1, 2)));
        List<String> bad = new ArrayList<>();
        for (Case c : cases) {
            Path spec = J8SSupport.bankSpec("abort-drop" + c.drop(), c.drop());
            String answer = sequentialAnswer(spec, c.drop());
            Path d = J8SApiTest.dir("abort-" + c.label().charAt(0));
            String tag = UUID.randomUUID().toString();
            SplitCoordinator.Result r = J8SApiTest.run(d, spec, 1, c.w(), c.reversed(), (x, id) ->
                    J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag, List.of("-Djmc.split.fault=" + c.fault())));
            SplitCoordinator.Online on = r.online();
            List<String> left = J8SApiTest.leftovers(tag);
            System.out.println("J8S M3 api " + c.label() + ": failure " + r.failure() + "; committed " + committed(d)
                    + "; online " + on + "; leftovers " + left);
            String p = "case " + c.label() + ": ";
            if (r.failure() == null || !r.failure().contains("fail")) {
                bad.add(p + "failure " + r.failure());
            }
            if (!c.committed().equals(committed(d))) {
                bad.add(p + "committed " + committed(d) + ", expected " + c.committed());
            }
            if (on == null) {
                bad.add(p + "no online result");
                continue;
            }
            boolean miss = c.label().startsWith("3") ? false : true;
            if (on.miss() != miss || on.firstMissIteration() != c.first()) {
                bad.add(p + "miss " + on.miss() + " first " + on.firstMissIteration() + ", expected " + miss + " "
                        + c.first());
            }
            if (on.uncoveredTotal() != null || on.uncoveredDistinct() != null) {
                bad.add(p + "totals kept: " + on.uncoveredTotal() + " / " + on.uncoveredDistinct());
            }
            if (miss) {
                if (!J8SSupport.bankLine(c.drop()).equals(on.witness()) || !answer.equals(on.answer())
                        || on.firstMissWallMs() < 0) {
                    bad.add(p + "witness/answer/wall " + on.witness() + " / " + on.answer() + " (sequential " + answer
                            + ") / " + on.firstMissWallMs());
                }
            } else if (on.witness() != null || on.answer() != null || on.firstMissWallMs() != -1) {
                bad.add(p + "no miss but witness/answer/wall " + on.witness() + " / " + on.answer() + " / "
                        + on.firstMissWallMs());
            }
            if (on.lookups() != c.expectLookups() || on.found() != c.expectFound()
                    || on.found() + on.noSig() + on.notFound() != on.lookups()) {
                bad.add(p + "lookups " + on.lookups() + " found " + on.found() + ", expected " + c.expectLookups()
                        + " / " + c.expectFound());
            }
            if (!left.isEmpty()) {
                bad.add(p + "leftovers " + left);
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    @Test
    void launcher_committedMiss_withAndWithoutGap_andNoMiss() throws Exception {
        List<String> bad = new ArrayList<>();
        // label, drop, W, faults, expected verdict, expected first-miss text ("" none), committed items
        Object[][] cases = {
            {"1 no gap", 2, 1, "fail:0:0", "reports", "2", List.of(1, 2)},
            {"2 gap before", 9, 2, "delay:1:0,fail:1:0", "reports", "unknown (a segment before it is missing)",
                List.of(0, 2)},
            {"3 no miss", 9, 1, "fail:0:0", "partial", "", List.of(1, 2)},
        };
        for (Object[] c : cases) {
            String label = (String) c[0];
            int drop = (Integer) c[1];
            String verdict = (String) c[4];
            String first = (String) c[5];
            Path spec = J8SSupport.bankSpec("abort-drop" + drop, drop);
            String answer = sequentialAnswer(spec, drop);
            String tag = UUID.randomUUID().toString();
            J8SSupport.Out o = J8SSupport.launch(List.of("-Dj8.tag=" + tag, "-Djmc.split.fault=" + c[3]),
                    J8SSupport.BANK, false, "--spec", spec.toString(), "--split", c[2].toString(), "--k", "1");
            List<String> left = J8SApiTest.leftovers(tag);
            Map<String, String> b = o.block();
            String p = "case " + label + ": ";
            List<Integer> com = o.runDir() == null ? null : committed(o.runDir());
            System.out.println("J8S M3 launcher " + label + ": exit " + o.exit() + "; committed " + com + "; verdict "
                    + o.field("verdict") + "; first miss " + o.field("first miss") + "; uncovered " + o.field("uncovered")
                    + "; at " + o.field("at") + "; FAILED " + o.field("FAILED") + "; block first_miss_iteration '"
                    + b.get("first_miss_iteration") + "' uncovered_total '" + b.get("uncovered_total") + "' stop_reason '"
                    + b.get("stop_reason") + "'; leftovers " + left);
            if (o.exit() != 1 || o.field("FAILED") == null || !o.field("FAILED").startsWith("incomplete: ")) {
                bad.add(p + "exit " + o.exit() + " FAILED " + o.field("FAILED"));
            }
            if (!c[6].equals(com)) {
                bad.add(p + "committed " + com + ", expected " + c[6] + " (precondition)");
            }
            if (!verdict.equals(o.field("verdict"))) {
                bad.add(p + "verdict " + o.field("verdict") + ", expected " + verdict);
            }
            if (o.field("uncovered") != null || !"".equals(b.get("uncovered_total"))) {
                bad.add(p + "totals printed: " + o.field("uncovered") + " / '" + b.get("uncovered_total") + "'");
            }
            if (!"error".equals(b.get("stop_reason"))) {
                bad.add(p + "stop_reason " + b.get("stop_reason"));
            }
            if (first.isEmpty()) {
                if (o.field("first miss") != null || o.field("witness") != null || o.field("at") != null
                        || !"0".equals(b.get("first_miss_iteration"))) {
                    bad.add(p + "a miss printed: " + o.field("first miss") + " / " + o.field("witness") + " / '"
                            + b.get("first_miss_iteration") + "'");
                }
            } else {
                String fm = o.field("first miss");
                if (fm == null || !fm.matches(java.util.regex.Pattern.quote("iteration " + first
                        + " (sequential order), earliest ") + "\\d+ ms \\(wall\\)")) {
                    bad.add(p + "first miss " + fm);
                }
                if (!J8SSupport.bankLine(drop).equals(o.field("witness"))) {
                    bad.add(p + "witness " + o.field("witness"));
                }
                String at = o.field("at");
                if (at == null || !at.startsWith("iteration " + first + ", " + answer + " ")) {
                    bad.add(p + "at " + at + " (sequential answer " + answer + ")");
                }
                String bf = b.get("first_miss_iteration");
                if (!(first.startsWith("unknown") ? "" : first).equals(bf)) {
                    bad.add(p + "block first_miss_iteration '" + bf + "'");
                }
            }
            if (!left.isEmpty()) {
                bad.add(p + "leftovers " + left);
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }
}
