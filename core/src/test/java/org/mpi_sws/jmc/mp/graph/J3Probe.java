package org.mpi_sws.jmc.mp.graph;

import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Runs {@link J3PorfTest}'s matrix and random graphs in a fresh JVM, so that {@link
 * ExecutionGraph#CHECK} is fixed by this JVM's flags; prints {@code CHECK}, whether {@code -ea} is
 * on, one {@code FAIL} line per failing graph (a {@code porf check:} message is the property's own
 * check firing) and {@code DONE failed=N}.
 */
public final class J3Probe {

    private J3Probe() {}

    public static void main(String[] args) {
        boolean ea = false;
        assert ea = true;
        System.out.println("CHECK " + ExecutionGraph.CHECK);
        System.out.println("EA " + ea);
        int failed = 0;
        for (Map.Entry<String, Supplier<ExecutionGraph>> e : J3PorfTest.matrix().entrySet()) {
            try {
                System.out.println("GRAPH " + e.getKey() + " " + J3PorfTest.check(e.getKey(), e.getValue().get()));
            } catch (Throwable t) {
                failed++;
                System.out.println("FAIL " + e.getKey() + ": " + t);
            }
        }
        Random r = new Random(311);
        for (int i = 0; i < 4000; i++) {
            try {
                J3PorfTest.check("random " + i, J3PorfTest.randomGraph(r));
            } catch (Throwable t) {
                failed++;
                if (failed < 20) {
                    System.out.println("FAIL random " + i + ": " + t);
                }
            }
        }
        System.out.println("DONE failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
