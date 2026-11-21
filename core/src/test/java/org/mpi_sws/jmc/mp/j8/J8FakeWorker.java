package org.mpi_sws.jmc.mp.j8;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * J8 round 06 (tester): a fake worker for the coordinator's M2 test. It waits for the first assignment
 * naming its id, publishes {@code item-<j>.json} with the given malformed content (temp file and
 * rename, as a worker does), and then stays alive until it is killed.
 *
 * <p>Usage: {@code J8FakeWorker <runDir> <workerId> <garbage|emptyobject>}.
 */
public final class J8FakeWorker {

    private J8FakeWorker() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String id = args[1];
        String content = args[2].equals("garbage") ? "this is not json {" : "{}";
        Path assigned = dir.resolve("assigned");
        while (true) {
            if (Files.isDirectory(assigned)) {
                try (Stream<Path> files = Files.list(assigned)) {
                    for (Path p : (Iterable<Path>) files::iterator) {
                        String f = p.getFileName().toString();
                        if (!f.endsWith(".json")) {
                            continue;
                        }
                        String a = Files.readString(p, StandardCharsets.UTF_8).replace(" ", "");
                        if (a.contains("\"worker\":" + id + ",") || a.contains("\"worker\":" + id + "}")) {
                            String item = f.substring(0, f.indexOf('.'));
                            Path tmp = dir.resolve("item-" + item + ".json.tmp-fake");
                            Files.writeString(tmp, content, StandardCharsets.UTF_8);
                            Files.move(tmp, dir.resolve("item-" + item + ".json"), StandardCopyOption.ATOMIC_MOVE);
                            Thread.sleep(Long.MAX_VALUE);
                        }
                    }
                }
            }
            Thread.sleep(50);
        }
    }
}
