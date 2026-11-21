package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.strategies.must.split.GraphPart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests of {@link GraphPart}: the record round trip (a refusal's reason flattened), the merge in the sequential
 * order (first record per line decides, offsets added, the store's limit applied only at the merge, nothing passed on
 * once the store is full, a malformed part reported), and the zero limit.
 */
class GraphPartMergeTest {

    /** A store that keeps at most {@code limit} graphs and records what it was given. */
    private static final class Store implements MustStrategy.GraphSink {
        final long limit;
        final List<String> got = new ArrayList<>();
        long written;

        Store(long limit) {
            this.limit = limit;
        }

        @Override
        public boolean wants() {
            return written < limit;
        }

        @Override
        public void graph(int iteration, String line, String threads) {
            got.add("G " + iteration + " " + line + " " + threads);
            written++;
        }

        @Override
        public void refused(String line, String why) {
            got.add("R " + line + " " + why);
        }
    }

    private static Path part(Path dir, String name, long storeLimit, String... records) throws IOException {
        Path f = dir.resolve(name + ".part");
        GraphPart p = new GraphPart(f, storeLimit);
        for (String r : records) {
            String[] x = r.split(" ", 3);
            if (x[0].equals("G")) {
                if (p.wants()) {
                    p.graph(Integer.parseInt(x[1]), x[2], "[]");
                }
            } else {
                p.refused(x[1], x[2]);
            }
        }
        Path done = dir.resolve(name);
        p.endSegment(done);
        p.endSegment(null);
        return done;
    }

    @Test
    void refusedFirstOccurrence_decides_andTheLimitIsAppliedAtTheMerge() throws Exception {
        Path dir = WitnessSupport.fresh("graphpart-merge");
        // The root refuses A; the item has A (writable there) and then B. With a store limit of 1 the item's part
        // keeps both, and the merge passes on the refusal of A, then B, as the sequential run would.
        Path root = part(dir, "root", 1, "R A why\twith a tab");
        Path item = part(dir, "item", 1, "G 1 A", "G 2 B");
        Store s = new Store(1);
        GraphPart.merge(s, List.of(root, item), List.of(0, 5));
        assertEquals(List.of("R A why with a tab", "G 7 B []"), s.got);
    }

    @Test
    void nothingPassedOnOnceTheStoreIsFull() throws Exception {
        Path dir = WitnessSupport.fresh("graphpart-full");
        Path root = part(dir, "root", 1, "G 1 A");
        Path item = part(dir, "item", 1, "R C why", "G 2 D");
        Store s = new Store(1);
        GraphPart.merge(s, List.of(root, item), List.of(0, 3));
        assertEquals(List.of("G 1 A []"), s.got);
    }

    @Test
    void zeroLimit_keepsNothing_andAMalformedPartIsReported() throws Exception {
        Path dir = WitnessSupport.fresh("graphpart-zero");
        Path root = part(dir, "root", 0, "G 1 A");
        assertEquals("", Files.readString(root));
        Path bad = dir.resolve("bad");
        Files.writeString(bad, "X\tnot a record\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> GraphPart.merge(new Store(5), List.of(root, bad), List.of(0, 0)));
    }
}
