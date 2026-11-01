package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * J8 tester round 10 (plan/jmc-must/log/dev/J8/round-10.prompt.md): the D-9 fix. Each {@code item-<j>.json}
 * carries {@code peak_rss_kb} and {@code maxima} (its worker JVM's, root replay included, at publication);
 * the coordinator takes the maximum over the root and every committed result.
 *
 * <p>Derived: a process that dies after publishing still contributes, through its last published result,
 * the maxima of every item it explored (its maxima are cumulative), so the printed maxima equal the
 * sequential ones whichever item the death follows; an attempt that dies before publishing contributes
 * nothing, but its item is re-explored by the retry, which carries its own maxima. {@code peak_rss_kb} is
 * at least every envelope's. No {@code *.stats.json} file exists. Malformed fields (no maxima, no or a
 * non-numeric {@code peak_rss_kb}, a negative or missing maximum) are a semantic failure: the run aborts
 * (the launcher then prints {@code stop_reason: error}, round 09) and no worker survives.
 */
class J8Round10Test {

    /**
     * die-after on item 0 (the item whose graphs hold the largest maxima on these rows: round 09, D-9)
     * and on item 4, at W = 1 and W = 4, and a restart run (die-before:0:0 at W = 1: the restarted worker
     * explores item 0 at attempt 1).
     */
    @Test
    void dieAfterAndRestart_maximaEqualSequential_rssAtLeastEnvelopes() throws Exception {
        List<String> bad = new ArrayList<>();
        for (String r : List.of("A3Lock:never-grant,3,1", "A3Lock:double-grant,3,1")) {
            String row = J8Launch.J4 + r;
            J8Launch.Out seqOut = J8Launch.launch(row, true);
            Map<String, String> a = J8Round09Test.block(seqOut);
            List<String[]> runs = new ArrayList<>();
            for (String w : List.of("1", "4")) {
                runs.add(new String[] {"die-after:0:0", w});
                runs.add(new String[] {"die-after:4:0", w});
            }
            runs.add(new String[] {"die-before:0:0", "1"});
            for (String[] run : runs) {
                J8Launch.Out o = J8Launch.launch(List.of("-Djmc.split.fault=" + run[0]), row, true, "--split", run[1],
                        "--k", "1");
                Map<String, String> b = J8Round09Test.block(o);
                String p = r + " " + run[0] + " W=" + run[1] + ": ";
                List<Long> peaks = J8Round09Test.envelopePeaks(o.runDir());
                int stats = J8Round09Test.statsFiles(o.runDir());
                System.out.println("J8R10 " + p + "exit " + o.exit() + " graphs " + o.field("graphs") + " maxima "
                        + J8Round09Test.MAXIMA.stream().map(b::get).toList() + " seq "
                        + J8Round09Test.MAXIMA.stream().map(a::get).toList() + " rss " + b.get("peak_rss_kb")
                        + " envelopes " + peaks + " stats files " + stats);
                if (o.exit() != 0) {
                    bad.add(p + "exit " + o.exit() + " " + o.field("FAILED"));
                    continue;
                }
                if (!seqOut.exportMd5().equals(o.exportMd5())) {
                    bad.add(p + "export differs");
                }
                for (String k : J8Round09Test.EQUAL) {
                    if (!a.get(k).equals(b.get(k))) {
                        bad.add(p + k + " " + b.get(k) + " vs " + a.get(k));
                    }
                }
                for (String k : J8Round09Test.MAXIMA) {
                    if (!a.get(k).equals(b.get(k))) {
                        bad.add(p + k + " " + b.get(k) + " vs sequential " + a.get(k));
                    }
                }
                long rss = Long.parseLong(b.get("peak_rss_kb"));
                for (long x : peaks) {
                    if (rss < x) {
                        bad.add(p + "peak_rss_kb " + rss + " < an envelope's " + x);
                    }
                }
                if (stats != 0) {
                    bad.add(p + stats + " stats files");
                }
                int n = Integer.parseInt(o.field("split").replaceAll(".*, N (\\d+).*", "$1"));
                if (peaks.size() != n) {
                    bad.add(p + peaks.size() + " envelopes, N = " + n);
                }
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * Malformed envelope fields. W = 2 on H1 Actors:token_server (counting; K = 1: R 1, N 2): worker 0 is
     * {@link J8EnvelopeFakeWorker} (it publishes item 1, the first assignment), worker 1 the real worker,
     * tagged. Expected for every malformed mode: the run aborts on item 1 with the coordinator's
     * validation message and no tagged process is alive; the control mode {@code valid} is accepted (the
     * run completes; its fake outcome digest is merged, which only shows that the fields under test are
     * what the validation refuses).
     */
    @Test
    void malformedEnvelopeFields_abort_noWorkerSurvives() throws Exception {
        String program = J8Launch.H1 + "Actors:token_server";
        Runnable prog = J8Step0Probe.instantiate(program);
        List<String> bad = Collections.synchronizedList(new ArrayList<>());
        for (String mode : List.of("missing-maxima", "missing-rss", "string-rss", "negative-max", "missing-one-max",
                "valid")) {
            String tag = UUID.randomUUID().toString();
            Path d = J8Round06Test.dir("r10-" + mode);
            SplitCoordinator.Result r = SplitCoordinator.run(new SplitCoordinator.Config(program, prog, null, null, 1, 2,
                    300, d, (x, id) -> id == 0
                            ? List.of(J8Round06Test.JAVA, "-Dj8.tag=" + tag, "-cp", J8Round06Test.CP,
                                    J8EnvelopeFakeWorker.class.getName(), x.toString(), "0", mode)
                            : realCounting(x, id, program, tag)));
            List<String> left = J8Round06Test.leftovers(tag);
            System.out.println("J8R10 malformed " + mode + ": failure " + r.failure() + "; leftovers " + left);
            if (!left.isEmpty()) {
                bad.add(mode + ": leftovers " + left);
            }
            if (mode.equals("valid")) {
                if (r.failure() != null) {
                    bad.add("valid: " + r.failure());
                }
                continue;
            }
            // Round 11 (Codex round 06 M1): the messages of the strict numeric validation.
            String expected = switch (mode) {
                case "missing-maxima" -> "the result carries no graph-shape maxima";
                case "missing-rss", "string-rss" -> "the result's peak RSS is missing, not an integer, or below -1";
                case "negative-max" -> "the result's maximum max_graph_events is missing, not an integer, or negative";
                default -> "the result's maximum max_porf_count is missing, not an integer, or negative";
            };
            if (r.failure() == null || !r.failure().contains("item 1") || !r.failure().contains(expected)) {
                bad.add(mode + ": failure " + r.failure() + ", expected item 1 and '" + expected + "'");
            }
            assertTrue(r.maxima().isEmpty(), mode + ": maxima empty on abort");
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /** The real worker in counting mode (export file "-"), tagged. */
    static List<String> realCounting(Path d, int id, String program, String tag) {
        return new ArrayList<>(List.of(J8Round06Test.JAVA, "-Dj8.tag=" + tag, "-Xss4m", "-cp", J8Round06Test.CP,
                "org.mpi_sws.jmc.mp.launch.MpLauncher", program, "must", "0", "1", d.resolve("worker-report").toString(),
                "-", "300", "--worker", d.toString(), Integer.toString(id)));
    }
}
