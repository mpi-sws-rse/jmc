package org.mpi_sws.jmc.mp.j8;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SPLIT tester round 01: a stand-in worker that never runs an item. It records its mode in
 * {@code <runDir>/wexit/<pid>} and then: {@code now} exits 1 at once; {@code wait} exits 1 when
 * {@code <runDir>/exit-<id>} or {@code STOP} exists; {@code hang} never exits by itself (killed by the
 * coordinator's abort). Usage: {@code J8WaitExitWorker <runDir> <id> <mode>}.
 */
public final class J8WaitExitWorker {

    private J8WaitExitWorker() {}

    public static void main(String[] args) throws Exception {
        Path run = Path.of(args[0]);
        String pid = Long.toString(ProcessHandle.current().pid());
        Files.createDirectories(run.resolve("wexit"));
        Path tmp = run.resolve("wexit").resolve(pid + ".tmp");
        Files.writeString(tmp, args[2]);
        Files.move(tmp, run.resolve("wexit").resolve(pid), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        switch (args[2]) {
            case "now" -> System.exit(1);
            case "wait" -> {
                while (!Files.exists(run.resolve("exit-" + args[1])) && !Files.exists(run.resolve("STOP"))) {
                    Thread.sleep(10);
                }
                System.exit(1);
            }
            default -> {
                while (true) {
                    Thread.sleep(1000);
                }
            }
        }
    }
}
