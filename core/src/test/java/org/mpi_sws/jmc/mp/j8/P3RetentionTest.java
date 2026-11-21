package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.strategies.must.split.ItemResult;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;
import org.mpi_sws.jmc.strategies.must.split.SplitWorker;
import org.mpi_sws.jmc.strategies.must.split.StackFingerprint;
import org.mpi_sws.jmc.strategies.must.split.TypedGraphEncoding;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * P3 tester round 01 (plan/jmc-must/P3-PROPOSAL.md rev 3 §2 B-lite.2, §4.1, §4.5): a worker releases the replayed
 * root transcript and {@code run.json}'s copy once the boundary check has used them; the file keeps it, the
 * fingerprint is the one of the full transcript, a fresh replay (a restarted worker) verifies again, and a
 * fingerprint mismatch is still refused.
 *
 * <p>Set-up: a counting coordinator run of {@code A3Lock:never-grant,3,1} at K 1, W 1, real worker (R 1, N 5;
 * J8 step 1 t2) gives a {@code run.json}. Its bytes are copied into a fresh directory with {@code STOP} and no
 * assignment, and a {@link SplitWorker} replays the root there in this JVM (worker id 9; a worker that finds
 * {@code STOP} and nothing assigned ends at once), wrapped by {@link Spy}, which records the boundary's view,
 * its own digests of the replayed outcomes, and — by reflection on {@code rootOutcomes} and {@code run}
 * (private fields of SplitWorker) — the worker's two representations just before and just after
 * {@code atBoundary}.
 *
 * <p>Not covered (no seam, the objects are locals of {@code SplitCoordinator.run}): the coordinator's
 * {@code Root.outcomes} and its in-memory {@code runJson} after {@code run.json} is written; reported to the lead.
 */
class P3RetentionTest {

    static final String NEVER = P3BoundTest.NEVER;

    /** Forwards to the worker; records what the release must leave and must not keep. */
    static final class Spy implements MustStrategy.SplitControl {
        final SplitWorker w;
        final List<ItemResult.Outcome> digests = new ArrayList<>();
        MustStrategy.SplitView view;
        List<?> rootBefore;
        int rootBeforeSize = -1;
        List<?> rootAfter;
        boolean transcriptBefore;
        boolean transcriptAfter;
        boolean answered;

        Spy(SplitWorker w) {
            this.w = w;
        }

        @Override
        public int splitThreshold() {
            return w.splitThreshold();
        }

        @Override
        public void outcome(boolean blocked, GraphCanon canon, LongFunction<String> names) {
            digests.add(new ItemResult.Outcome(blocked, TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(canon,
                    names))));
            w.outcome(blocked, canon, names);
        }

        @Override
        public boolean atBoundary(MustStrategy.SplitView v) {
            view = v;
            rootBefore = (List<?>) field(w, "rootOutcomes");
            rootBeforeSize = rootBefore.size();
            transcriptBefore = ((JsonObject) field(w, "run")).has("transcript");
            try {
                answered = w.atBoundary(v);
                return answered;
            } finally {
                rootAfter = (List<?>) field(w, "rootOutcomes");
                transcriptAfter = ((JsonObject) field(w, "run")).has("transcript");
            }
        }

        @Override
        public void segmentDone(MustStrategy.Segment s) {
            w.segmentDone(s);
        }

        @Override
        public OptionalInt nextItem() {
            return w.nextItem();
        }
    }

    static Object field(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A coordinator run (counting, K 1, W 1); its run directory. */
    static Path coordinatorRun() throws Exception {
        String tag = UUID.randomUUID().toString();
        Path d = P3Commit.freshDir("retention-run");
        SplitCoordinator.Result r = SplitCoordinator.run(new SplitCoordinator.Config(NEVER,
                J8Step0Probe.instantiate(NEVER), null, null, 1, 1, 300, d,
                (x, id) -> J8Round10Test.realCounting(x, id, NEVER, tag)));
        assertNull(r.failure(), r.failure());
        assertEquals(18, r.distinctComplete());
        assertEquals(10, r.distinctBlocked());
        assertEquals(List.of(), J8Round06Test.leftovers(tag));
        return d;
    }

    /** A fresh directory with the given run.json bytes and STOP. */
    static Path replayDir(byte[] runJson, String name) throws Exception {
        Path f = P3Commit.freshDir(name);
        Files.createDirectories(f);
        Files.write(f.resolve("run.json"), runJson);
        Files.writeString(f.resolve("STOP"), "{}");
        return f;
    }

    /** Replays the root with an in-JVM worker 9; returns the spy (a failure is recorded, not thrown). */
    static Spy replay(Path f) throws Exception {
        SplitWorker w = new SplitWorker(f, 9);
        Spy spy = new Spy(w);
        MustStrategy s = new MustStrategy(1L, "build/test-results/jmc-report");
        s.setSplitControl(spy);
        try {
            P3BoundTest.check(s, J8Step0Probe.instantiate(NEVER), NEVER);
        } catch (RuntimeException e) {
            System.out.println("P3 replay in " + f + ": " + e);
        }
        return spy;
    }

    static List<ItemResult.Outcome> transcript(JsonObject run) {
        List<ItemResult.Outcome> out = new ArrayList<>();
        for (JsonElement e : run.getAsJsonArray("transcript")) {
            JsonObject o = e.getAsJsonObject();
            out.add(new ItemResult.Outcome(o.get("blocked").getAsBoolean(), o.get("digest").getAsString()));
        }
        return out;
    }

    /**
     * Change: after {@code atBoundary} the worker's {@code rootOutcomes} is empty and a different list than the
     * one {@code segmentDone(-1)} gave it (replaced, not cleared in place: that list still holds R outcomes),
     * and its {@code run} object has no {@code transcript}; just before, both held the transcript (R 1). The
     * replay verified ({@code verified}). {@code run.json} on disk is byte-identical before and after and still
     * holds the R-entry transcript. On 4ce59e76 {@code rootOutcomes} keeps R entries and {@code run} keeps
     * the transcript.
     */
    @Test
    void worker_releasesRootTranscript_afterBoundary_fileKeepsIt() throws Exception {
        Path d = coordinatorRun();
        byte[] bytes = Files.readAllBytes(d.resolve("run.json"));
        JsonObject run = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        int r = run.get("r").getAsInt();
        assertEquals(r, run.getAsJsonArray("transcript").size(), "the transcript has R entries");
        assertTrue(r >= 1);
        Path f = replayDir(bytes, "retention-replay");
        Spy spy = replay(f);
        System.out.println("P3 retention: R " + r + ", before " + spy.rootBeforeSize + "/" + spy.transcriptBefore
                + ", after " + (spy.rootAfter == null ? null : spy.rootAfter.size()) + "/" + spy.transcriptAfter
                + ", answered " + spy.answered + ", verified " + field(spy.w, "verified"));
        assertTrue(spy.answered, "the boundary check passed");
        assertEquals(Boolean.TRUE, field(spy.w, "verified"));
        assertEquals(r, spy.rootBeforeSize, "the replayed transcript reached the check");
        assertTrue(spy.transcriptBefore, "run.json's copy was in memory before the check");
        assertNotNull(spy.rootAfter);
        assertTrue(spy.rootAfter.isEmpty(), "rootOutcomes released");
        assertNotSame(spy.rootBefore, spy.rootAfter, "replaced, not cleared in place");
        assertEquals(r, spy.rootBefore.size(), "the old list was not cleared in place");
        assertFalse(spy.transcriptAfter, "run's transcript removed after the check");
        assertTrue(((List<?>) field(spy.w, "rootOutcomes")).isEmpty(), "still released at the end of the run");
        assertFalse(((JsonObject) field(spy.w, "run")).has("transcript"));
        assertEquals(J8Launch.md5(bytes), J8Launch.md5(Files.readAllBytes(f.resolve("run.json"))),
                "run.json unchanged on disk");
        assertEquals(J8Launch.md5(bytes), J8Launch.md5(Files.readAllBytes(d.resolve("run.json"))));
        assertFalse(Files.exists(f.resolve("worker-9.fail.json")));
    }

    /**
     * Guard: the fingerprint recomputed from run.json's full transcript and the boundary view equals run.json's
     * fingerprint and the one from the replay's own digests (which equal the transcript, in order); a second,
     * fresh worker on the same file (a restart) replays and verifies again.
     */
    @Test
    void worker_fingerprintFromFullTranscript_restartVerifies() throws Exception {
        Path d = coordinatorRun();
        byte[] bytes = Files.readAllBytes(d.resolve("run.json"));
        JsonObject run = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        Path f = replayDir(bytes, "retention-fp");
        Spy first = replay(f);
        assertTrue(first.answered);
        assertNotNull(first.view);
        String program = run.get("program").getAsString();
        int k = run.get("k").getAsInt();
        List<ItemResult.Outcome> disk = transcript(run);
        assertEquals(disk, first.digests, "the replay's outcomes are run.json's transcript");
        String fromDisk = StackFingerprint.of(program, k, first.view, disk);
        String fromReplay = StackFingerprint.of(program, k, first.view, first.digests);
        System.out.println("P3 fingerprint: run.json " + run.get("fingerprint").getAsString() + ", from disk " + fromDisk
                + ", from replay " + fromReplay);
        assertEquals(run.get("fingerprint").getAsString(), fromDisk);
        assertEquals(fromDisk, fromReplay);
        Spy second = replay(f);
        assertTrue(second.answered, "a restarted worker verifies");
        assertEquals(Boolean.TRUE, field(second.w, "verified"));
        assertEquals(disk, second.digests);
        assertFalse(Files.exists(f.resolve("worker-9.fail.json")));
    }

    /**
     * Guard: run.json with a fingerprint whose first hex digit is changed: the replay is refused, the worker
     * writes {@code worker-9.fail.json} whose reason names the fingerprint, and it is not verified.
     */
    @Test
    void worker_fingerprintMismatch_refused() throws Exception {
        Path d = coordinatorRun();
        JsonObject run = JsonParser.parseString(Files.readString(d.resolve("run.json"))).getAsJsonObject();
        String fp = run.get("fingerprint").getAsString();
        String other = (fp.charAt(0) == '0' ? "1" : "0") + fp.substring(1);
        run.addProperty("fingerprint", other);
        Path f = replayDir(run.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "retention-mismatch");
        Spy spy = replay(f);
        assertFalse(spy.answered);
        assertEquals(Boolean.FALSE, field(spy.w, "verified"));
        Path fail = f.resolve("worker-9.fail.json");
        assertTrue(Files.exists(fail), "fail marker written");
        String reason = JsonParser.parseString(Files.readString(fail)).getAsJsonObject().get("reason").getAsString();
        System.out.println("P3 mismatch: " + reason);
        assertTrue(reason.contains("fingerprint " + fp + " differs from " + other), reason);
    }
}
