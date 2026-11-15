package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * P3 tester round 02 (Codex code review P3 round 01, finding P2): the coordinator keeps neither in-memory form of
 * the root transcript (run.json's JSON array, the root's outcome list) once run.json is written.
 *
 * <p>Seam: the package-private {@code SplitCoordinator.transcriptReleaseProbe}
 * ({@code Consumer<List<WeakReference<Object>>>}), called once just before dispatch with weak references to the
 * two forms. This test lives in {@code org.mpi_sws.jmc.mp.j8s} with the other coordinator tests and sets the
 * field by reflection, so it compiles against any production version; on the round-01 production (no field) it
 * fails with {@link NoSuchFieldException}. Inside the probe it calls {@code System.gc()} up to 50 times, 20 ms
 * apart, until both references are cleared, and records the outcome; the probe is reset in a finally.
 *
 * <p>Fixture: OnlineBank:2, exporting, no specification, K 4, W 2, real workers. Derived (J8S round 01, F6 at
 * K 4: items 3, 2, 1, 0, item3's first iteration is sequential iteration 3): R 2, N 4 — a transcript of two
 * outcomes, so R &gt; 1. Expected: the probe ran once, both references cleared; run.json's transcript has R
 * entries, each {@code {"blocked": <boolean>, "digest": <64 hex>}}; the run completes with 12 distinct complete,
 * 0 blocked, 12 iterations, and its summary lines, sorted as the launcher sorts them, equal the body of the
 * sequential export of the same program.
 *
 * <p>Why this cannot be shown failing for the retention reason on round-01 production: that version has no seam,
 * so no reference to its local {@code transcript} array can be obtained from a test; whether the interpreter
 * keeps a dead local reachable is also not deterministic once the frame is compiled. The test fails there by the
 * missing seam, and passes now only if both forms are really unreachable.
 */
class P3CoordinatorReleaseTest {

    static Field probeField() throws ReflectiveOperationException {
        Field f = SplitCoordinator.class.getDeclaredField("transcriptReleaseProbe");
        f.setAccessible(true);
        return f;
    }

    @Test
    void coordinator_releasesRootTranscript_beforeDispatch_fileKeepsIt() throws Exception {
        Field f = probeField();
        int[] calls = {0};
        int[] gcs = {-1};
        boolean[] cleared = new boolean[2];
        int[] refs = {-1};
        Consumer<List<WeakReference<Object>>> probe = released -> {
            calls[0]++;
            refs[0] = released.size();
            int i = 0;
            while (i < 50 && !(released.get(0).get() == null && released.get(1).get() == null)) {
                System.gc();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                i++;
            }
            gcs[0] = i;
            cleared[0] = released.get(0).get() == null;
            cleared[1] = released.get(1).get() == null;
        };
        OnlineBank p = new OnlineBank("2");
        Path d = J8SApiTest.dir("p3-release");
        String tag = UUID.randomUUID().toString();
        SplitCoordinator.Result r;
        f.set(null, probe);
        try {
            r = SplitCoordinator.run(new SplitCoordinator.Config(J8SSupport.BANK + "|p2p", p, p.rolesInSpawnOrder(),
                    p.visibleRoles(), 4, 2, 300, d, (x, id) -> J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag)));
        } finally {
            f.set(null, null);
        }
        List<String> left = J8SApiTest.leftovers(tag);
        System.out.println("P3 release: calls " + calls[0] + ", references " + refs[0] + ", gc rounds " + gcs[0]
                + ", cleared json " + cleared[0] + " list " + cleared[1] + "; failure " + r.failure() + ", R " + r.r()
                + ", N " + r.n() + ", " + r.distinctComplete() + "/" + r.distinctBlocked() + ", ran " + r.ran()
                + "; leftovers " + left);
        assertNull(f.get(null), "probe reset");
        assertEquals(1, calls[0], "the probe ran once");
        assertEquals(2, refs[0], "two references: the JSON array and the outcome list");
        assertTrue(cleared[0], "run.json's transcript array released (after " + gcs[0] + " gc rounds)");
        assertTrue(cleared[1], "the root's outcome list released (after " + gcs[0] + " gc rounds)");
        assertNull(r.failure(), r.failure());
        assertEquals(List.of(), left);
        assertEquals(2, r.r(), "R (derived)");
        assertEquals(4, r.n(), "N (derived)");
        JsonObject run = JsonParser.parseString(Files.readString(d.resolve("run.json"))).getAsJsonObject();
        assertEquals(r.r(), run.get("r").getAsInt());
        JsonArray t = run.getAsJsonArray("transcript");
        assertEquals(r.r(), t.size(), "run.json keeps the full transcript");
        for (JsonElement e : t) {
            JsonObject o = e.getAsJsonObject();
            assertEquals(2, o.size(), o.toString());
            assertTrue(o.get("blocked").isJsonPrimitive() && o.get("blocked").getAsJsonPrimitive().isBoolean(),
                    o.toString());
            assertTrue(o.get("digest").getAsString().matches("[0-9a-f]{64}"), o.toString());
        }
        assertEquals(12, r.distinctComplete());
        assertEquals(0, r.distinctBlocked());
        assertEquals(12, r.ran());
        List<String> seq = J8SSupport.bankExport();
        List<String> body = new ArrayList<>(r.summaryLines());
        SummaryExporter.sortLines(body);
        assertEquals(seq.subList(1, seq.size()), body, "summary lines equal the sequential export's body");
    }
}
