package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * P3 tester round 01 (plan/jmc-must/P3-PROPOSAL.md rev 3 §2 B-lite.1, §4.2, §4.5): the coordinator's commit
 * boundary. Envelope validation, online validation and the decoding of every outcome precede any change to
 * the digest unions or to {@code committed}; the outcomes are in the item's outcome file; a result
 * with an undecodable outcome is refused whole ("item j: malformed outcome …") and contributes nothing.
 *
 * <p>Derived before the run. The {@code commit_*} tests drive the private commit step through {@link P3Commit}
 * (reflection; the unions and {@code committed} are locals of {@code SplitCoordinator.run}, no seam exposes
 * them), with the unions seeded as the root would seed them ({@code complete = {D}}, {@code blocked = {}}):
 * <ul>
 *   <li>valid result {@code [(false,A), (true,B), (false,A), (true,A)]}: answer {@code null}; complete
 *       {@code {D, A}}, blocked {@code {B, A}} (one digest in both, kept separate as the old merge did);
 *       {@code committed} holds item 3 without {@code outcomes} and with every other member of the published
 *       envelope unchanged; the slot is cleared (-1/-1); the outcome file still holds the four outcomes;</li>
 *   <li>a valid first outcome {@code (false,A)} then a malformed one (Gson cannot convert {@code blocked}:
 *       {@code null}, {@code {}}, missing, {@code []}, {@code [true,false]}): answer
 *       {@code "item 3: malformed outcome " + <element>}; unions unchanged (A not added), {@code committed}
 *       empty, the slot still 3/0. On 4ce59e76 this result was committed and failed the final merge;</li>
 *   <li>an outcome that is not an object, or has no digest: refused as a malformed outcome (it threw before
 *       the outcomes moved to their own file); nothing folded;</li>
 *   <li>an online object in a run without an online check, outcomes valid: refused by the existing message,
 *       nothing folded.</li>
 * </ul>
 * The end-to-end tests run {@code H1 Actors:token_server} counting at K 1 (R 1, N 2: J8 round 10), W 2, worker 0
 * {@link J8P3FakeWorker}, worker 1 real.
 */
class P3CommitTest {

    static final int ITEM = 3;

    static P3Commit seeded(String name) throws Exception {
        P3Commit c = new P3Commit(P3Commit.freshDir(name), ITEM, false, null, System.currentTimeMillis());
        c.complete.add(P3Commit.D);
        return c;
    }

    @Test
    void commit_valid_foldsUnions_stripsOutcomes_clearsSlot() throws Throwable {
        P3Commit c = seeded("commit-valid");
        JsonArray outs = P3Commit.array(P3Commit.outcome(false, P3Commit.A), P3Commit.outcome(true, P3Commit.B),
                P3Commit.outcome(false, P3Commit.A), P3Commit.outcome(true, P3Commit.A));
        JsonObject env = c.envelope(outs, 4, 2);
        c.publish(env);
        assertNull(c.commit(), "a valid result is committed");
        assertEquals(Set.of(P3Commit.D, P3Commit.A), c.complete, "complete union");
        assertEquals(Set.of(P3Commit.B, P3Commit.A), c.blocked, "blocked union");
        assertEquals(Set.of(ITEM), c.committed.keySet());
        JsonObject kept = c.committed.get(ITEM);
        assertFalse(kept.has("outcomes"), "the retained envelope carries no outcomes: " + kept);
        JsonObject expected = env.deepCopy();
        expected.remove("outcomes");
        assertEquals(expected, kept, "every other member kept");
        assertEquals(-1, c.slotItem());
        assertEquals(-1, c.slotAttempt());
        assertTrue(c.lastCommit[0] > 0, "commit time recorded");
        assertEquals(4, c.published().get("outcomes_count").getAsLong(), "the result declares its outcomes");
        assertEquals(4, java.nio.file.Files.readAllLines(c.dir.resolve(
                org.mpi_sws.jmc.strategies.must.split.SplitCoordinator.itemOutcomes(ITEM, 0))).size(),
                "the outcome file keeps them");
        // a second pass finds nothing outstanding and changes nothing
        assertNull(c.commit());
        assertEquals(Set.of(P3Commit.D, P3Commit.A), c.complete);
        assertEquals(1, c.committed.size());
    }

    @Test
    void commit_malformedLaterOutcome_refusedWhole_nothingFolded() throws Throwable {
        List<String> bad = new ArrayList<>();
        for (String later : List.of("{\"blocked\":null,\"digest\":\"B\"}", "{\"blocked\":{},\"digest\":\"B\"}",
                "{\"digest\":\"B\"}", "{\"blocked\":[],\"digest\":\"B\"}",
                "{\"blocked\":[true,false],\"digest\":\"B\"}")) {
            JsonElement el = P3Commit.raw(later.replace("\"B\"", "\"" + P3Commit.B + "\""));
            P3Commit c = seeded("commit-malformed");
            c.publish(c.envelope(P3Commit.array(P3Commit.outcome(false, P3Commit.A), el), 2, 0));
            String answer = c.commit();
            System.out.println("P3 malformed " + later + " -> " + answer);
            String p = later + ": ";
            String want = "item " + ITEM + ": malformed outcome " + el;
            if (!want.equals(answer)) {
                bad.add(p + "answer " + answer + ", expected " + want);
            }
            if (!c.complete.equals(Set.of(P3Commit.D)) || !c.blocked.isEmpty()) {
                bad.add(p + "unions changed: " + c.complete + " / " + c.blocked);
            }
            if (!c.committed.isEmpty()) {
                bad.add(p + "committed " + c.committed.keySet());
            }
            if (c.slotItem() != ITEM || c.slotAttempt() != 0) {
                bad.add(p + "slot " + c.slotItem() + "/" + c.slotAttempt());
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    @Test
    void commit_nonObjectOrNoDigest_refused_nothingFolded() throws Throwable {
        List<String> bad = new ArrayList<>();
        for (String later : List.of("\"x\"", "7", "{\"blocked\":true}")) {
            P3Commit c = seeded("commit-nonobject");
            c.publish(c.envelope(P3Commit.array(P3Commit.outcome(false, P3Commit.A), P3Commit.raw(later)), 2, 0));
            Throwable thrown = null;
            String answer = null;
            try {
                answer = c.commit();
            } catch (RuntimeException e) {
                thrown = e;
            }
            System.out.println("P3 non-object " + later + " -> " + (thrown != null ? "threw " + thrown : answer));
            // Read from the outcome file line by line, these are malformed outcomes too (they used to throw).
            String want = "item " + ITEM + ": malformed outcome " + P3Commit.raw(later);
            if (thrown != null || !want.equals(answer)) {
                bad.add(later + ": " + (thrown != null ? "threw " + thrown : "answer " + answer) + ", expected " + want);
            }
            if (!c.complete.equals(Set.of(P3Commit.D)) || !c.blocked.isEmpty() || !c.committed.isEmpty()
                    || c.slotItem() != ITEM) {
                bad.add(later + ": state changed " + c.complete + " / " + c.blocked + " / " + c.committed.keySet());
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    @Test
    void commit_onlineObjectInCountingRun_refused_nothingFolded() throws Throwable {
        P3Commit c = seeded("commit-online-counting");
        JsonObject env = c.envelope(P3Commit.array(P3Commit.outcome(false, P3Commit.A)), 1, 0);
        env.add("online", JsonParser.parseString("{\"uncovered\":0,\"lines\":[],\"first_local\":0,\"witness\":null,"
                + "\"answer\":null,\"first_at_ms\":-1,\"lookups\":1,\"found\":1,\"not_found\":0,\"no_sig\":0}"));
        c.publish(env);
        assertEquals("item " + ITEM + ": the result carries an online object, but the run does not check online",
                c.commit());
        assertEquals(Set.of(P3Commit.D), c.complete);
        assertTrue(c.blocked.isEmpty());
        assertTrue(c.committed.isEmpty());
        assertEquals(ITEM, c.slotItem());
    }

    /** An item's committed outcomes, from its outcome file (attempt 0). */
    static JsonArray outcomes(Path d, int item) throws Exception {
        JsonArray a = new JsonArray();
        for (String line : java.nio.file.Files.readAllLines(d.resolve(
                org.mpi_sws.jmc.strategies.must.split.SplitCoordinator.itemOutcomes(item, 0)))) {
            a.add(JsonParser.parseString(line));
        }
        return a;
    }

    /** Adds outcomes to the unions with the old final merge's conversion. */
    static void fold(JsonArray outs, Set<String> complete, Set<String> blocked) {
        for (JsonElement e : outs) {
            JsonObject o = e.getAsJsonObject();
            (o.get("blocked").getAsBoolean() ? blocked : complete).add(o.get("digest").getAsString());
        }
    }

    static JsonObject read(Path p) throws Exception {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    static SplitCoordinator.Result tokenServer(Path d, String mode, String tag) throws Exception {
        String program = J8Launch.H1 + "Actors:token_server";
        Runnable prog = J8Step0Probe.instantiate(program);
        return SplitCoordinator.run(new SplitCoordinator.Config(program, prog, null, null, 1, 2, 300, d,
                (x, id) -> id == 0
                        ? List.of(J8Round06Test.JAVA, "-Dj8.tag=" + tag, "-cp", J8Round06Test.CP,
                                J8P3FakeWorker.class.getName(), x.toString(), "0", mode)
                        : J8Round10Test.realCounting(x, id, program, tag)));
    }

    /**
     * Guard (passes on 4ce59e76 and with P3): duplicate digests across root and items, one digest both
     * complete and blocked, and Gson's accepted conversions ({@code "true"}, {@code 1}) are counted as the old
     * end-of-run union counted them. Expected: complete = |root_c ∪ item0_c ∪ item1_c|, blocked likewise, with
     * the old conversion over run.json's transcript and both item files; hand facts: d0 and A in both unions,
     * B and C blocked, D complete; ran = R + 9 + item 1's iterations; blocked iterations = root's + 5 + item
     * 1's.
     */
    @Test
    void duplicates_rootAndItems_completeAndBlocked_countedAsTheOldUnion() throws Exception {
        String tag = UUID.randomUUID().toString();
        Path d = P3Commit.freshDir("dups");
        SplitCoordinator.Result r = tokenServer(d, "dups", tag);
        List<String> left = J8Round06Test.leftovers(tag);
        System.out.println("P3 dups: failure " + r.failure() + ", complete " + r.distinctComplete() + ", blocked "
                + r.distinctBlocked() + ", ran " + r.ran() + ", items " + r.itemIterations() + "; leftovers " + left);
        assertNull(r.failure(), r.failure());
        assertEquals(List.of(), left);
        JsonObject run = read(d.resolve("run.json"));
        JsonObject i0 = read(d.resolve("item-0.json"));
        JsonObject i1 = read(d.resolve("item-1.json"));
        assertEquals(9, i0.get("iterations").getAsInt(), "precondition: item 0 is the fake's");
        Set<String> c = new HashSet<>();
        Set<String> b = new HashSet<>();
        JsonArray transcript = run.getAsJsonArray("transcript");
        fold(transcript, c, b);
        fold(outcomes(d, 0), c, b);
        fold(outcomes(d, 1), c, b);
        String d0 = transcript.get(0).getAsJsonObject().get("digest").getAsString();
        assertTrue(c.contains(d0) && b.contains(d0), "d0 in both");
        assertTrue(c.contains(P3Commit.A) && b.contains(P3Commit.A), "A in both");
        assertTrue(b.contains(P3Commit.B) && b.contains(P3Commit.C) && !c.contains(P3Commit.C), "B, C blocked");
        assertTrue(c.contains(P3Commit.D) && !b.contains(P3Commit.D), "D complete");
        assertEquals(c.size(), r.distinctComplete(), "distinct complete as the old union");
        assertEquals(b.size(), r.distinctBlocked(), "distinct blocked as the old union");
        int rootBlocked = 0;
        for (JsonElement e : transcript) {
            rootBlocked += e.getAsJsonObject().get("blocked").getAsBoolean() ? 1 : 0;
        }
        assertEquals(run.get("r").getAsInt() + 9 + i1.get("iterations").getAsInt(), r.ran());
        assertEquals(rootBlocked + 5 + i1.get("blockedIterations").getAsInt(), r.blockedIterations());
    }

    /**
     * Change: a result whose second outcome has {@code "blocked":null} is refused at commit. Expected with P3:
     * failure {@code "item 0: malformed outcome {\"blocked\":null,\"digest\":\"bbb…\"}"}, no worker left. On
     * 4ce59e76 the result was committed and {@code SplitCoordinator.run} threw from the final merge
     * ({@code UnsupportedOperationException}), so this test fails there.
     */
    @Test
    void malformedOutcome_countingRun_refusedAtCommit() throws Exception {
        String tag = UUID.randomUUID().toString();
        Path d = P3Commit.freshDir("malformed-e2e");
        SplitCoordinator.Result r = tokenServer(d, "malformed-null", tag);
        List<String> left = J8Round06Test.leftovers(tag);
        System.out.println("P3 malformed e2e: failure " + r.failure() + "; leftovers " + left);
        assertNotNull(r.failure());
        assertEquals("item 0: malformed outcome {\"blocked\":null,\"digest\":\"" + P3Commit.B + "\"}", r.failure());
        assertEquals(0, r.distinctComplete());
        assertEquals(List.of(), left);
    }
}
