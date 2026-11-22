package org.mpi_sws.jmc.mp.j8s;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * J8S round 02 (tester): a worker wrapper for risk 16 ("a worker given a different spec file"). Before it
 * starts the real worker, it turns the last line end of the specification file named on its command line (the
 * file run.json names) into CRLF, which the reader accepts, so the worker's SHA-256 differs from the coordinator's; then it runs the worker and
 * exits with its status.
 *
 * <p>Usage: {@code J8SpecTamperWorker <specFile> <worker command...>}.
 */
public final class J8SpecTamperWorker {

    private J8SpecTamperWorker() {}

    public static void main(String[] args) throws Exception {
        Path spec = Path.of(args[0]);
        byte[] b = Files.readAllBytes(spec);
        if (b.length == 0 || b[b.length - 1] != '\n') {
            throw new IllegalStateException(spec + " does not end with a line end");
        }
        // The last line end becomes CRLF: still a valid file, with another SHA-256.
        byte[] t = Arrays.copyOf(b, b.length + 1);
        t[b.length - 1] = '\r';
        t[b.length] = '\n';
        Files.write(spec, t);
        List<String> cmd = Arrays.asList(args).subList(1, args.length);
        Process child = new ProcessBuilder(cmd).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(child::destroyForcibly));
        System.exit(child.waitFor());
    }
}
