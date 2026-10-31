package org.mpi_sws.jmc.mp.j8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * J8 rounds 07–08 (tester): a wrapper around a worker command. It records which worker id the watched
 * process serves ({@code <runDir>/pids/<pid>} holds the id), so that a test's liveness probe can tell the
 * worker processes apart, and — the round-08 gate — it does not start the real worker until the test
 * releases it by creating {@code <runDir>/gate/<pid>} (or until the run's {@code STOP} appears, in which
 * case it exits 0 without starting it). It then exits with the real worker's status, at once.
 *
 * <p>Usage: {@code J8PidWorker <runDir> <workerId> <worker command...>}.
 */
public final class J8PidWorker {

    private J8PidWorker() {}

    public static void main(String[] args) throws Exception {
        Path run = Path.of(args[0]);
        String pid = Long.toString(ProcessHandle.current().pid());
        Files.createDirectories(run.resolve("pids"));
        Files.writeString(run.resolve("pids").resolve(pid), args[1]);
        Path gate = run.resolve("gate").resolve(pid);
        while (!Files.exists(gate)) {
            if (Files.exists(run.resolve("STOP"))) {
                System.exit(0);
            }
            Thread.sleep(10);
        }
        List<String> cmd = Arrays.asList(args).subList(2, args.length);
        Process child = new ProcessBuilder(cmd).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(child::destroyForcibly));
        System.exit(child.waitFor());
    }
}
