package org.mpi_sws.jmc.mp.j8;

import java.util.Arrays;
import java.util.List;

/**
 * J8 round 06 (tester): a test-side wrapper around a worker command that keeps the watched process
 * alive {@code delayMillis} after the real worker has ended normally (status 0, i.e. after STOP), to
 * show that the coordinator's {@code explore_ms} ends at the last commit, not at the workers' shutdown.
 *
 * <p>Usage: {@code J8HoldWorker <delayMillis> <worker command...>}.
 */
public final class J8HoldWorker {

    private J8HoldWorker() {}

    public static void main(String[] args) throws Exception {
        long delay = Long.parseLong(args[0]);
        List<String> cmd = Arrays.asList(args).subList(1, args.length);
        Process child = new ProcessBuilder(cmd).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(child::destroyForcibly));
        int status = child.waitFor();
        if (status == 0) {
            Thread.sleep(delay);
        }
        System.exit(status);
    }
}
