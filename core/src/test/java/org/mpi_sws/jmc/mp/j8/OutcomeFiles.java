package org.mpi_sws.jmc.mp.j8;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** For the fake workers: an item's outcome file, as a real worker writes it (one outcome's JSON per line). */
public final class OutcomeFiles {

    private OutcomeFiles() {}

    /**
     * Writes {@code outcomes-<item>.<attempt>.jsonl} in {@code dir}.
     *
     * @param dir the run directory
     * @param item the item
     * @param attempt the attempt
     * @param outcomes the outcomes, each written as its compact JSON
     * @return the number written, the envelope's {@code outcomes_count}
     */
    public static long write(Path dir, int item, int attempt, JsonArray outcomes) {
        StringBuilder b = new StringBuilder();
        for (JsonElement e : outcomes) {
            b.append(e).append('\n');
        }
        try {
            Path tmp = dir.resolve(SplitCoordinator.itemOutcomes(item, attempt) + ".tmp-fake");
            Files.writeString(tmp, b, StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve(SplitCoordinator.itemOutcomes(item, attempt)),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return outcomes.size();
    }
}
