package org.mpi_sws.jmc.mp.j8s;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * J8S round 04 (tester): a fake worker that waits until a file whose name starts with the given prefix
 * exists in the run directory (another worker's spec snapshot copy), then writes its own startup fail marker
 * {@code worker-<id>.fail.json} (reason {@code tester abort while worker 1 snapshots}) and exits 1. Gives up
 * after 120 s.
 *
 * <p>Usage: {@code J8SnapshotAbortWorker <runDir> <workerId> <prefix>}.
 */
public final class J8SnapshotAbortWorker {

    private J8SnapshotAbortWorker() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String prefix = args[2];
        long until = System.currentTimeMillis() + 120_000;
        boolean seen = false;
        while (!seen && System.currentTimeMillis() < until) {
            try (Stream<Path> s = Files.list(dir)) {
                seen = s.anyMatch(p -> p.getFileName().toString().startsWith(prefix));
            }
            if (!seen) {
                Thread.sleep(5);
            }
        }
        Path tmp = dir.resolve("worker-" + args[1] + ".fail.json.tmp-fake");
        Files.writeString(tmp, "{\"reason\":\"tester abort while worker 1 snapshots (seen " + seen + ")\"}");
        Files.move(tmp, dir.resolve("worker-" + args[1] + ".fail.json"), StandardCopyOption.ATOMIC_MOVE);
        System.exit(1);
    }
}
