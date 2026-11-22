package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A split item's outcomes and a worker that runs out of memory. Row A3Lock:fifo,3,1 at K 1 (R 1, N 5, items
 * 2 4 3 10 10, 30 graphs; {@link J8Step1Test}), W 2.
 * <ul>
 *   <li>Each committed item's outcomes are in {@code outcomes-<j>.<a>.jsonl}, one line per iteration, as many as
 *       the result's {@code outcomes_count} and {@code iterations}; the result itself carries none.</li>
 *   <li>{@code oom:3:0}: the worker's fail marker is retryable; the item is retried once on a fresh JVM
 *       ({@code assigned/3.1.json}), the marker set aside as {@code retried-fail-3.0.json}; the run completes with
 *       the sequential export.</li>
 *   <li>{@code oom:3:0,oom:3:1}: the run aborts; the reason names item 3, attempt 1, its worker, the first
 *       attempt's failure and how many of the 5 items were committed.</li>
 *   <li>{@code fail:2:0} (not retryable): aborts at once, the reason naming item 2 and the committed count.</li>
 * </ul>
 */
class SplitItemMemoryTest {

    @Test
    void outcomes_inTheirOwnFile_onePerIteration() throws Exception {
        J8Launch.Out o = J8Step1Test.faulted("", 2);
        J8Step1Test.complete(o, "30 distinct complete, 0 distinct blocked; stack exhausted");
        assertEquals(J8Step1Test.A3FIFO_MD5, o.exportMd5());
        Path d = o.runDir();
        for (int j = 0; j < 5; j++) {
            JsonObject env = JsonParser.parseString(Files.readString(d.resolve("item-" + j + ".json")))
                    .getAsJsonObject();
            assertFalse(env.has("outcomes"), "item " + j + ": the result carries no outcomes");
            List<String> lines = Files.readAllLines(d.resolve(SplitCoordinator.itemOutcomes(j,
                    env.get("attempt").getAsInt())));
            assertEquals(env.get("iterations").getAsLong(), lines.size(), "item " + j);
            assertEquals(env.get("outcomes_count").getAsLong(), lines.size(), "item " + j);
            for (String line : lines) {
                assertTrue(line.matches("\\{\"blocked\":(true|false),\"digest\":\"[0-9a-f]{64}\"}"), line);
            }
        }
    }

    @Test
    void outOfMemory_retriedOnce_onAFreshWorker() throws Exception {
        J8Launch.Out o = J8Step1Test.faulted("oom:3:0", 2);
        J8Step1Test.complete(o, "30 distinct complete, 0 distinct blocked; stack exhausted");
        assertEquals(J8Step1Test.A3FIFO_MD5, o.exportMd5());
        assertTrue(Files.exists(o.runDir().resolve("assigned").resolve("3.1.json")), "attempt 1 assigned");
        Path setAside = o.runDir().resolve("retried-fail-3.0.json");
        assertTrue(Files.exists(setAside), "the retryable marker set aside");
        JsonObject m = JsonParser.parseString(Files.readString(setAside)).getAsJsonObject();
        assertTrue(m.get("retryable").getAsBoolean(), m.toString());
        assertEquals("java.lang.OutOfMemoryError: injected out of memory in item 3", m.get("reason").getAsString());
    }

    @Test
    void outOfMemoryTwice_aborts_namingTheItem() throws Exception {
        J8Launch.Out o = J8Step1Test.faulted("oom:3:0,oom:3:1", 2);
        J8Step1Test.aborted(o, "fail marker fail-3.1.json: \"java.lang.OutOfMemoryError: injected out of memory in"
                + " item 3\"; item 3 (attempt 1, worker ");
        String f = o.field("FAILED");
        assertTrue(f.contains(") failed, after attempt 0 failed with \"java.lang.OutOfMemoryError: injected out of"
                + " memory in item 3\"; "), f);
        assertTrue(f.matches(".* [0-4] of 5 items committed$"), f);
    }

    @Test
    void semanticFailure_notRetried_reasonNamesTheItem() throws Exception {
        J8Launch.Out o = J8Step1Test.faulted("fail:2:0", 2);
        J8Step1Test.aborted(o, "fail marker fail-2.0.json: \"injected semantic failure in item 2\"; item 2 (attempt"
                + " 0, worker ");
        assertTrue(o.field("FAILED").matches(".* [0-4] of 5 items committed$"), o.field("FAILED"));
        assertFalse(Files.exists(o.runDir().resolve("assigned").resolve("2.1.json")), "not retried");
    }
}
