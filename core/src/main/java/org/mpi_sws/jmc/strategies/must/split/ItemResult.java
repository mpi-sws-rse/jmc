package org.mpi_sws.jmc.strategies.must.split;

import org.mpi_sws.jmc.mp.summary.Summary;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * The result of one segment of a split run: the root (index -1) or one item.
 *
 * @param index the item index in the snapshot, -1 for the root
 * @param iterations iterations that ran
 * @param blockedIterations of those, iterations whose graph is blocked
 * @param outcomes each counted iteration's outcome, in the order the iterations ran
 * @param summaries summaries with multiplicities in first-occurrence order; {@code null} without
 *     export
 * @param names the process names met, {@code "m"} included
 */
public record ItemResult(int index, int iterations, int blockedIterations, List<Outcome> outcomes,
                         Map<Summary, Integer> summaries, SortedSet<String> names) {

    /**
     * One counted iteration's outcome.
     *
     * @param blocked whether its graph is blocked
     * @param digest the hex SHA-256 of the graph's outcome encoding ({@link TypedGraphEncoding#outcome})
     */
    public record Outcome(boolean blocked, String digest) {}
}
