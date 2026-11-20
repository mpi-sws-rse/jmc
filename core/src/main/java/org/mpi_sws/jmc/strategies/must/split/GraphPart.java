package org.mpi_sws.jmc.strategies.must.split;

import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One segment's part of a split run's graph store: the graph of each summary at its first occurrence within the
 * segment (the root, or one item), with the iteration counted within the segment. A part keeps every first
 * occurrence (none with a store limit of 0): which of them the store keeps depends on the earlier segments, so the
 * limit is applied only when the parts are merged ({@link #merge}).
 * One record per line, fields separated by tabs (no field holds a raw tab or line break: JSON escapes them in
 * strings, and a refusal's reason is flattened):
 * <ul>
 *   <li>{@code G <iteration> <line> <threads>}: a graph;</li>
 *   <li>{@code R <line> <reason>}: a summary whose graph could not be written.</li>
 * </ul>
 * The coordinator merges the parts in the sequential order (the root, then items {@code N − 1} … {@code 0}),
 * keeping the first record per line.
 */
public final class GraphPart implements MustStrategy.GraphSink {

    private final Path file;
    private final long limit;
    private BufferedWriter out;
    private long kept;

    /**
     * A part written to {@code file}, which must not exist.
     *
     * @param file the part file
     * @param storeLimit the store's limit: 0 keeps nothing, anything else every first occurrence
     * @throws IOException if the file cannot be created
     */
    public GraphPart(Path file, long storeLimit) throws IOException {
        this.file = file;
        this.limit = storeLimit == 0 ? 0 : Long.MAX_VALUE;
        this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    @Override
    public boolean wants() {
        return kept < limit;
    }

    @Override
    public void graph(int iteration, String line, String threads) {
        write("G\t" + iteration + "\t" + line + "\t" + threads + "\n");
        kept++;
    }

    @Override
    public void refused(String line, String why) {
        write("R\t" + line + "\t" + why.replaceAll("[\\t\\r\\n]", " ") + "\n");
    }

    private void write(String record) {
        try {
            out.write(record);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the graph part " + file, e);
        }
    }

    /**
     * Ends the segment: the part is flushed and closed, then moved to {@code target} (atomically, replacing a
     * leftover of an earlier attempt) or, with {@code target} null, deleted; a fresh part is opened at the same
     * path for the next segment.
     *
     * @param target where the finished part goes, or {@code null} to drop it
     * @throws IOException if the part cannot be moved or reopened
     */
    public void endSegment(Path target) throws IOException {
        out.close();
        if (target == null) {
            Files.delete(file);
        } else {
            Files.move(file, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        kept = 0;
    }

    /**
     * Closes the part and leaves it where it is (the root's part, read by the coordinator).
     *
     * @throws IOException if it cannot be closed
     */
    public void close() throws IOException {
        out.close();
    }

    /**
     * A merged graph record.
     *
     * @param iteration the iteration within the part's segment, or -1 for a refusal
     * @param line the summary line
     * @param payload the threads array, or the refusal's reason
     */
    public record Record(int iteration, String line, String payload) {
        /** Whether the record is a refusal. */
        public boolean refused() {
            return iteration < 0;
        }
    }

    /**
     * Merges finished parts in the order given into {@code store}, as a sequential run would have written it: the
     * first record per summary line decides (a later one of the same line is never used), its iteration is moved by
     * the part's offset, and, as the sequential run stops walking once its store is full, neither a graph nor a
     * refusal is passed on once {@code store.wants()} is false. Every part is read to its end, so a malformed part
     * is always reported.
     *
     * @param store the receiver
     * @param parts the parts, in the sequential order
     * @param offsets per part, the iterations of the run before its segment
     * @throws IOException if a part cannot be read or is malformed
     */
    public static void merge(MustStrategy.GraphSink store, List<Path> parts, List<Integer> offsets)
            throws IOException {
        if (parts.size() != offsets.size()) {
            throw new IllegalArgumentException("one offset per part");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < parts.size(); i++) {
            int offset = offsets.get(i);
            read(parts.get(i), rec -> {
                if (!seen.add(rec.line()) || !store.wants()) {
                    return;
                }
                if (rec.refused()) {
                    store.refused(rec.line(), rec.payload());
                } else {
                    store.graph(offset + rec.iteration(), rec.line(), rec.payload());
                }
            });
        }
    }

    /**
     * Reads the records of a finished part in order.
     *
     * @param file the part
     * @param each receives every record
     * @throws IOException if the part cannot be read or is malformed
     */
    public static void read(Path file, Consumer<Record> each) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String rec;
            while ((rec = in.readLine()) != null) {
                String[] f = rec.split("\t", -1);
                if (f.length == 4 && f[0].equals("G")) {
                    each.accept(new Record(Integer.parseInt(f[1]), f[2], f[3]));
                } else if (f.length == 3 && f[0].equals("R")) {
                    each.accept(new Record(-1, f[1], f[2]));
                } else {
                    throw new IOException(file + ": a malformed graph record");
                }
            }
        }
    }
}
