package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.j8.J8P3FakeWorker;
import org.mpi_sws.jmc.mp.j8.P3Commit;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * P3 tester round 01 (plan/jmc-must/P3-PROPOSAL.md rev 3 §2 contract, §3 online, §4.2, §4.5): the online check
 * under the P3 change.
 *
 * <ul>
 *   <li>Guard: F5 (OnlineBank:2, bank2-multi: 4 uncovered, 4 distinct, first 3) and F10 (A3Lock:never-grant,3,1
 *       against A3Lock:spec,3,1: 10, 6, 1; J8S round 01) at {@code --split 2} and {@code --split 3}, K 1, equal the
 *       sequential run (J8SSupport.compare: verdict, mode, uncovered, witness, at, lookups, first miss, the export
 *       with only {@code first_miss_wall_ms} normalised — the one documented volatile field — and the block
 *       keys), plus {@code ran}, {@code graphs} and {@code exported} without its path.</li>
 *   <li>Change (commit boundary, reflection through {@link P3Commit}, item 3 of an exporting online run on
 *       bank2-multi, unions seeded {@code complete = {D}}): an invalid online object with valid outcomes is
 *       refused by the existing message and folds nothing; a valid online object with a malformed later outcome
 *       is refused as "malformed outcome" and folds nothing; a valid result is committed without
 *       {@code outcomes}, its {@code online} kept.</li>
 *   <li>Change (end to end): OnlineBank:2 at K 1 (root [1], item2 [2,3], item1 [4,5,6], item0 [7..12]; J8S
 *       round 04), spec without iteration 9 (item0, first_local 3), W 2: worker 0 real, worker 1
 *       {@link J8P3FakeWorker} {@code malformed-null}, which publishes item 1 only after {@code assigned/2.0.json}
 *       exists, i.e. after item 0 was committed. Expected with P3: failure
 *       {@code item 1: malformed outcome …}; the partial online report as J8SAbortOutputTest case 2 derives it:
 *       miss, first −1 (item 1, before item 0 in the sequential order, is missing), witness iteration 9's line,
 *       answer the sequential run's, totals null; lookups 1 + 6 = 7, or 9 if item 2 was committed before the
 *       refusal; found = lookups − 1. On 4ce59e76 the malformed result was committed and
 *       {@code SplitCoordinator.run} threw from the final merge, so this test fails there.</li>
 * </ul>
 */
class P3OnlineTest {

    /**
     * The measurement block's keys with a specification, in print order (MpLauncher printMeasurements and
     * printSplitMeasurements with an online check).
     */
    static final List<String> ONLINE_KEYS = List.of("wall_ms", "explore_ms", "summarise_ms", "index_ms",
            "lookup_ms", "export_ms", "graphs_complete", "graphs_blocked", "summaries_distinct", "iterations",
            "stop_reason", "peak_rss_kb", "max_message_events", "max_porf_pairs", "first_miss_iteration",
            "first_miss_wall_ms", "uncovered_total", "max_graph_events", "mean_graph_events", "max_visible_events",
            "mean_visible_events", "max_summary_bytes", "max_porf_count");

    /** The graph-shape maxima, compared by value (equal to the sequential ones under split: J8 round 10). */
    static final List<String> MAXIMA = List.of("max_message_events", "max_porf_pairs", "max_graph_events",
            "max_visible_events", "max_summary_bytes", "max_porf_count");

    /**
     * Round 03 (Codex code review P3 round 02): the block's keys in print order, read from the raw text before
     * any map is built (J8SSupport.Out.block() is a TreeMap, which hides order and duplicates). Same line rule as
     * block(): after the marker, lines {@code key: value} whose key is {@code [a-z_]+}, the marker itself skipped.
     */
    static List<String> orderedKeys(J8SSupport.Out o) {
        List<String> keys = new ArrayList<>();
        int at = o.text().indexOf("--- measurements");
        if (at < 0) {
            return keys;
        }
        for (String l : o.text().substring(at).split("\n")) {
            int c = l.indexOf(':');
            if (!l.startsWith("---") && c > 0 && l.substring(0, c).matches("[a-z_]+")) {
                keys.add(l.substring(0, c));
            }
        }
        return keys;
    }

    static final String NO_MISS = "{\"uncovered\":0,\"lines\":[],\"first_local\":0,\"witness\":null,\"answer\":null,"
            + "\"first_at_ms\":-1,\"lookups\":2,\"found\":2,\"not_found\":0,\"no_sig\":0}";

    @Test
    void online_split2_split3_equalSequential_normalised() throws Exception {
        Path multi = J8SSupport.bankSpec("multi", 3, 5, 9, 12);
        Path a3 = J8SSupport.j4Spec("A3Lock:spec,3,1");
        Object[][] fx = {
            {"F5", J8SSupport.BANK, multi, 4L, 4L, 3L},
            {"F10", J8SSupport.J4 + "A3Lock:never-grant,3,1", a3, 10L, 6L, 1L},
        };
        List<String> bad = new ArrayList<>();
        for (Object[] f : fx) {
            String program = (String) f[1];
            String spec = f[2].toString();
            J8SSupport.Out seq = J8SSupport.launch(program, true, "--spec", spec);
            for (String w : List.of("2", "3")) {
                J8SSupport.Out o = J8SSupport.launch(program, true, "--spec", spec, "--split", w, "--k", "1");
                String label = f[0] + " W=" + w;
                bad.addAll(J8SSupport.compare(label, seq, o, (long) f[3], (long) f[4], (long) f[5]));
                // Round 02/03 (Codex P3 rounds 01, 02): the ordered key list first (exactly once each, in print
                // order, on both sides), then presence, then values.
                if (!ONLINE_KEYS.equals(orderedKeys(seq))) {
                    bad.add(label + ": sequential block keys " + orderedKeys(seq) + ", expected " + ONLINE_KEYS);
                }
                if (!ONLINE_KEYS.equals(orderedKeys(o))) {
                    bad.add(label + ": split block keys " + orderedKeys(o) + ", expected " + ONLINE_KEYS);
                }
                java.util.Map<String, String> sb = seq.block();
                java.util.Map<String, String> ob = o.block();
                for (String k : MAXIMA) {
                    String x = sb.get(k);
                    String y = ob.get(k);
                    if (x == null || y == null || x.isEmpty() || y.isEmpty() || !x.equals(y)) {
                        bad.add(label + ": block " + k + " '" + y + "' vs sequential '" + x + "'");
                    }
                }
                for (String k : List.of("ran", "blocked", "completed", "graphs", "exported", "verdict", "mode",
                        "uncovered", "witness", "at", "first miss", "lookups")) {
                    if (seq.field(k) == null || o.field(k) == null) {
                        bad.add(label + ": field '" + k + "' missing: sequential " + seq.field(k) + ", split " + o.field(k));
                    }
                }
                if (o.field("items") == null) {
                    bad.add(label + ": split field 'items' missing");
                }
                for (String k : List.of("ran", "blocked", "completed", "graphs")) {
                    if (!String.valueOf(seq.field(k)).equals(String.valueOf(o.field(k)))) {
                        bad.add(label + ": " + k + " '" + o.field(k) + "' vs sequential '" + seq.field(k) + "'");
                    }
                }
                String es = seq.field("exported");
                String eo = o.field("exported");
                if (es == null || eo == null || !es.substring(0, es.lastIndexOf(" to "))
                        .equals(eo.substring(0, eo.lastIndexOf(" to ")))) {
                    bad.add(label + ": exported '" + eo + "' vs sequential '" + es + "'");
                }
                System.out.println("P3 online " + label + ": items " + o.field("items") + " graphs " + o.field("graphs")
                        + " uncovered " + o.field("uncovered") + " first miss " + o.field("first miss"));
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    static P3Commit seeded(String name) throws Exception {
        Path spec = J8SSupport.bankSpec("multi", 3, 5, 9, 12);
        SpecificationFile.Loaded loaded = SpecificationFile.load(spec, new OnlineBank("2").expected());
        P3Commit c = new P3Commit(P3Commit.freshDir(name), 3, true, loaded, System.currentTimeMillis());
        c.complete.add(P3Commit.D);
        return c;
    }

    static void nothingFolded(P3Commit c) throws Exception {
        assertEquals(Set.of(P3Commit.D), c.complete);
        assertTrue(c.blocked.isEmpty());
        assertTrue(c.committed.isEmpty());
        assertEquals(3, c.slotItem());
    }

    @Test
    void commit_invalidOnline_validOutcomes_refused_nothingFolded() throws Throwable {
        P3Commit c = seeded("online-invalid");
        JsonObject env = c.envelope(P3Commit.array(P3Commit.outcome(false, P3Commit.A),
                P3Commit.outcome(true, P3Commit.B)), 2, 1);
        JsonObject on = JsonParser.parseString(NO_MISS).getAsJsonObject();
        on.remove("lookups");
        env.add("online", on);
        c.publish(env);
        assertEquals("item 3: the online field lookups is missing, not an integer, or negative", c.commit());
        nothingFolded(c);
    }

    @Test
    void commit_validOnline_malformedLaterOutcome_refused_nothingFolded() throws Throwable {
        P3Commit c = seeded("online-malformed");
        String later = "{\"blocked\":{},\"digest\":\"" + P3Commit.B + "\"}";
        JsonObject env = c.envelope(P3Commit.array(P3Commit.outcome(false, P3Commit.A), P3Commit.raw(later)), 2, 0);
        env.add("online", JsonParser.parseString(NO_MISS));
        c.publish(env);
        assertEquals("item 3: malformed outcome " + P3Commit.raw(later), c.commit());
        nothingFolded(c);
    }

    @Test
    void commit_validOnline_validOutcomes_strippedOnlineKept() throws Throwable {
        P3Commit c = seeded("online-valid");
        JsonObject env = c.envelope(P3Commit.array(P3Commit.outcome(false, P3Commit.A),
                P3Commit.outcome(true, P3Commit.B)), 2, 1);
        env.add("online", JsonParser.parseString(NO_MISS));
        c.publish(env);
        assertNull(c.commit());
        assertEquals(Set.of(P3Commit.D, P3Commit.A), c.complete);
        assertEquals(Set.of(P3Commit.B), c.blocked);
        JsonObject kept = c.committed.get(3);
        assertNotNull(kept);
        assertFalse(kept.has("outcomes"));
        assertEquals(JsonParser.parseString(NO_MISS), kept.get("online"));
        assertEquals(new com.google.gson.JsonArray(), kept.get("summaries"));
        assertEquals(-1, c.slotItem());
    }

    @Test
    void malformedOutcome_afterCommittedMiss_partialReportAsBefore() throws Exception {
        Path spec = J8SSupport.bankSpec("abort-drop9", 9);
        String answer = J8SAbortOutputTest.sequentialAnswer(spec, 9);
        Path d = J8SApiTest.dir("p3-malformed-after-commit");
        String tag = UUID.randomUUID().toString();
        SplitCoordinator.Result r = J8SApiTest.run(d, spec, 1, 2, false, (x, id) -> id == 1
                ? List.of(J8SApiTest.JAVA, "-Dj8.tag=" + tag, "-cp", J8SApiTest.CP, J8P3FakeWorker.class.getName(),
                        x.toString(), "1", "malformed-null", "assigned/2.0.json")
                : J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag));
        SplitCoordinator.Online on = r.online();
        List<String> left = J8SApiTest.leftovers(tag);
        System.out.println("P3 malformed after commit: failure " + r.failure() + "; files "
                + J8SAbortOutputTest.committed(d) + "; online " + on + "; leftovers " + left);
        assertEquals("item 1: malformed outcome {\"blocked\":null,\"digest\":\"" + P3Commit.B + "\"}", r.failure());
        assertNotNull(on);
        assertTrue(on.miss());
        assertEquals(-1, on.firstMissIteration());
        assertEquals(J8SSupport.bankLine(9), on.witness());
        assertEquals(answer, on.answer());
        assertNull(on.uncoveredTotal());
        assertNull(on.uncoveredDistinct());
        assertTrue(on.lookups() == 7 || on.lookups() == 9, "lookups " + on.lookups());
        assertEquals(on.lookups() - 1, on.found());
        assertEquals(on.lookups(), on.found() + on.notFound() + on.noSig());
        assertEquals(List.of(), left);
    }
}
