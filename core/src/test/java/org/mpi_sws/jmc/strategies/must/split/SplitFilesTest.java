package org.mpi_sws.jmc.strategies.must.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mpi_sws.jmc.strategies.mp.MpOutcome;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * The split run's files: results are written and read as streams ({@link SplitCoordinator#writeAtomically},
 * {@link SplitCoordinator#readJson}) with the read failures classified as before, and an item's outcome file is
 * checked and folded line by line ({@link SplitCoordinator#foldOutcomes}). A file of more than 2^31-1
 * characters is too big for a unit test; the stream is exercised across its buffer instead (values of several
 * megabytes, the buffer being 8 KB).
 */
class SplitFilesTest {

    @TempDir
    Path dir;

    static final String A = "a".repeat(64);
    static final String B = "b".repeat(64);

    @Test
    void writeAndRead_streamed_acrossTheBuffer() {
        JsonObject o = new JsonObject();
        JsonArray big = new JsonArray();
        for (int i = 0; i < 100_000; i++) {
            big.add("{\"blocked\":false,\"digest\":\"" + A + "\"}");
        }
        o.add("lines", big);
        o.addProperty("text", "é".repeat(3_000_000));
        Path f = dir.resolve("big.json");
        SplitCoordinator.writeAtomically(f, o);
        assertEquals(o, SplitCoordinator.readJson(f));
    }

    @Test
    void read_failuresClassifiedAsBefore() throws Exception {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> SplitCoordinator.readJson(dir.resolve("none.json")));
        assertEquals(MpOutcome.REFUSED, SplitCoordinator.abortOutcome(missing), "unreadable: refused");
        Path badUtf8 = dir.resolve("bad-utf8.json");
        Files.write(badUtf8, new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xff, '"', '}'});
        IllegalStateException enc = assertThrows(IllegalStateException.class, () -> SplitCoordinator.readJson(badUtf8));
        assertEquals(MpOutcome.REFUSED, SplitCoordinator.abortOutcome(enc), "not UTF-8: refused, as readString did");
        for (String text : new String[] {"{\"a\":", "{\"a\":1}}", "[1]", ""}) {
            Path p = dir.resolve("malformed.json");
            Files.writeString(p, text, StandardCharsets.UTF_8);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> SplitCoordinator.readJson(p),
                    text);
            assertEquals(MpOutcome.INCOMPLETE, SplitCoordinator.abortOutcome(e), text + ": " + e);
        }
    }

    Path outcomes(String... lines) throws Exception {
        Path p = dir.resolve(SplitCoordinator.itemOutcomes(4, 0));
        Files.writeString(p, lines.length == 0 ? "" : String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return p;
    }

    static String line(boolean blocked, String digest) {
        return "{\"blocked\":" + blocked + ",\"digest\":\"" + digest + "\"}";
    }

    @Test
    void fold_checksThenAdds_lineByLine() throws Exception {
        Path p = outcomes(line(false, A), line(true, B), line(false, A));
        SplitCoordinator.Unions u = new SplitCoordinator.Unions(new HashSet<>(), new HashSet<>());
        assertNull(SplitCoordinator.foldOutcomes(p, 3, null));
        assertEquals(Set.of(), u.complete(), "a check adds nothing");
        assertNull(SplitCoordinator.foldOutcomes(p, 3, u));
        assertEquals(Set.of(A), u.complete());
        assertEquals(Set.of(B), u.blocked());
        assertNull(SplitCoordinator.foldOutcomes(outcomes(), 0, null), "an item of no iterations");
    }

    @Test
    void fold_refusals() throws Exception {
        assertEquals("the outcome file outcomes-4.0.jsonl holds 2 outcomes, the result declares 3",
                SplitCoordinator.foldOutcomes(outcomes(line(false, A), line(true, B)), 3, null));
        assertEquals("the outcome file outcomes-4.0.jsonl holds 2 outcomes, the result declares 1",
                SplitCoordinator.foldOutcomes(outcomes(line(false, A), line(true, B)), 1, null));
        assertEquals("malformed digest " + line(false, "A".repeat(64)),
                SplitCoordinator.foldOutcomes(outcomes(line(false, A), line(false, "A".repeat(64))), 2, null));
        assertEquals("malformed outcome {\"blocked\":null,\"digest\":\"" + A + "\"}",
                SplitCoordinator.foldOutcomes(outcomes("{\"blocked\":null,\"digest\":\"" + A + "\"}"), 1, null));
        assertEquals("malformed outcome {\"blocked\":true",
                SplitCoordinator.foldOutcomes(outcomes("{\"blocked\":true"), 1, null), "a truncated line");
        Files.delete(dir.resolve(SplitCoordinator.itemOutcomes(4, 0)));
        assertEquals("the outcome file outcomes-4.0.jsonl is missing",
                SplitCoordinator.foldOutcomes(dir.resolve(SplitCoordinator.itemOutcomes(4, 0)), 0, null));
        assertTrue(SplitCoordinator.itemOutcomes(4, 1).endsWith("4.1.jsonl"));
    }
}
