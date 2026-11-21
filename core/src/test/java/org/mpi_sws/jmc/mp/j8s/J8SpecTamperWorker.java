package org.mpi_sws.jmc.mp.j8s;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

/**
 * J8S round 02 (tester): a worker wrapper for risk 16 ("a worker given a different spec file"). Before it
 * starts the real worker, it appends a newline to the specification file named on its command line (the
 * file run.json names), so the worker's SHA-256 differs from the coordinator's; then it runs the worker and
 * exits with its status.
 *
 * <p>Usage: {@code J8SpecTamperWorker <specFile> <worker command...>}.
 */
public final class J8SpecTamperWorker {

    private J8SpecTamperWorker() {}

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[0]), "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        List<String> cmd = Arrays.asList(args).subList(1, args.length);
        Process child = new ProcessBuilder(cmd).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(child::destroyForcibly));
        System.exit(child.waitFor());
    }
}
