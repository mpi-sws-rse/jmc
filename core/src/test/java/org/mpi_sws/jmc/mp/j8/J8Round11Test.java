package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * J8 tester round 11 (plan/jmc-must/log/dev/J8/round-11.prompt.md): Codex round 06 M1, strict numeric
 * validation of the envelope's {@code peak_rss_kb} and {@code maxima}.
 *
 * <p>Derived from the rule (a JSON number whose exact value is a {@code long} ≥ min; min −1 for the RSS, 0
 * for a maximum; {@code BigDecimal.longValueExact}): rejected — fractions (1.5, −0.5), values beyond a
 * long (2^63, 2^64, −2^63−1), an RSS below −1 (−2), a negative maximum (−1), NaN, a {@code maxima} that is
 * not an object; accepted — RSS −1 and 0, a maximum of 0, a maximum of 2^63−1, and numbers whose exact
 * value is integral although written with a fraction or an exponent (1.0, 2.0, 1e3, −0.0 = 0). A rejected
 * result aborts the run with the field's message and no worker survives; an accepted one lets the run
 * complete. Setup as round 10: H1 Actors:token_server, counting, K 1 (N 2), W 2; worker 0 is the fake
 * (it publishes item 1 with exactly one field set to the literal), worker 1 the real worker, tagged.
 */
class J8Round11Test {

    static final List<String> KEYS = List.of("max_message_events", "max_porf_pairs", "max_graph_events",
            "max_visible_events", "max_summary_bytes", "max_porf_count");
    static final String RSS_MSG = "the result's peak RSS is missing, not an integer, or below -1";

    static String maxMsg(String key) {
        return "the result's maximum " + key + " is missing, not an integer, or negative";
    }

    /** Runs one case; returns its problem or null. {@code expected} null means: accepted. */
    static String run(String mode, String expected) throws Exception {
        String program = J8Launch.H1 + "Actors:token_server";
        Runnable prog = J8Step0Probe.instantiate(program);
        String tag = UUID.randomUUID().toString();
        Path d = J8Round06Test.dir("r11-" + Math.abs(mode.hashCode()));
        SplitCoordinator.Result r = SplitCoordinator.run(new SplitCoordinator.Config(program, prog, null, null, 1, 2, 300,
                d, (x, id) -> id == 0
                        ? List.of(J8Round06Test.JAVA, "-Dj8.tag=" + tag, "-cp", J8Round06Test.CP,
                                J8EnvelopeFakeWorker.class.getName(), x.toString(), "0", mode)
                        : J8Round10Test.realCounting(x, id, program, tag)));
        List<String> left = J8Round06Test.leftovers(tag);
        String line = "J8R11 " + mode + " -> " + (r.failure() == null ? "accepted" : r.failure()) + "; leftovers " + left;
        System.out.println(line);
        if (!left.isEmpty()) {
            return mode + ": leftovers " + left;
        }
        if (expected == null) {
            return r.failure() == null ? null : mode + ": expected accepted, got " + r.failure();
        }
        if (r.failure() == null || !r.failure().equals("item 1: " + expected)) {
            return mode + ": expected 'item 1: " + expected + "', got " + r.failure();
        }
        return r.maxima().isEmpty() ? null : mode + ": maxima not empty on abort";
    }

    static void runAll(List<String[]> cases) throws Exception {
        // One case at a time: SplitCoordinator.run runs the root in this JVM, and JMC's runtime is static
        // and single-threaded (a first version ran three at once and the roots interfered).
        ExecutorService pool = Executors.newFixedThreadPool(1);
        List<String> bad = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> fs = new ArrayList<>();
        for (String[] c : cases) {
            fs.add(pool.submit(() -> {
                try {
                    String b = run(c[0], c[1]);
                    if (b != null) {
                        bad.add(b);
                    }
                } catch (Exception e) {
                    bad.add(c[0] + ": " + e);
                }
                return null;
            }));
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    @Test
    void rejected_maxima_fractionsOverflowsNegative_abortWithTheKeysMessage_noWorkerSurvives() throws Exception {
        List<String[]> cases = new ArrayList<>();
        for (String k : KEYS) {
            for (String lit : List.of("1.5", "-0.5", "9223372036854775808", "18446744073709551616")) {
                cases.add(new String[] {"raw:" + k + "=" + lit, maxMsg(k)});
            }
        }
        cases.add(new String[] {"raw:max_graph_events=-1", maxMsg("max_graph_events")});
        cases.add(new String[] {"raw:max_porf_count=NaN", maxMsg("max_porf_count")});
        runAll(cases);
    }

    @Test
    void rejected_rss_and_maximaObject_abortWithTheFieldsMessage_noWorkerSurvives() throws Exception {
        List<String[]> cases = new ArrayList<>();
        for (String lit : List.of("1.5", "-0.5", "9223372036854775808", "18446744073709551616", "-2",
                "-9223372036854775809", "NaN")) {
            cases.add(new String[] {"raw:rss=" + lit, RSS_MSG});
        }
        cases.add(new String[] {"maxima-array", "the result carries no graph-shape maxima"});
        cases.add(new String[] {"missing-maxima", "the result carries no graph-shape maxima"});
        runAll(cases);
    }

    @Test
    void accepted_boundaryAndIntegralValues_runCompletes() throws Exception {
        List<String[]> cases = new ArrayList<>();
        for (String lit : List.of("-1", "0", "1.0", "1e3")) {
            cases.add(new String[] {"raw:rss=" + lit, null});
        }
        cases.add(new String[] {"valid", null}); // every maximum 0
        cases.add(new String[] {"raw:max_graph_events=9223372036854775807", null});
        cases.add(new String[] {"raw:max_porf_pairs=2.0", null});
        cases.add(new String[] {"raw:max_summary_bytes=-0.0", null});
        runAll(cases);
    }
}
