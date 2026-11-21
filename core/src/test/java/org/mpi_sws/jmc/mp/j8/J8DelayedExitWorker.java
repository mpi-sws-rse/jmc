package org.mpi_sws.jmc.mp.j8;

import java.util.Arrays;
import java.util.List;

/**
 * J8 round 05 (tester): a test-side wrapper around a worker command. The coordinator watches this
 * process; it runs the real worker as a child and, if the child died by fault injection (exit status 3,
 * {@code SplitWorker.INJECTED_DEATH_STATUS}), stays alive {@code delayMillis} longer before exiting
 * with the same status. A worker that published item j and then "died" is therefore seen alive by the
 * coordinator long enough to be assigned the next item j' (the race of the lead's D-1 fix (b)), and its
 * death is observed with j' outstanding and never consumed by any worker.
 *
 * <p>Usage: {@code J8DelayedExitWorker <delayMillis> <worker command...>}.
 */
public final class J8DelayedExitWorker {

    private J8DelayedExitWorker() {}

    public static void main(String[] args) throws Exception {
        long delay = Long.parseLong(args[0]);
        List<String> cmd = Arrays.asList(args).subList(1, args.length);
        Process child = new ProcessBuilder(cmd).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(child::destroyForcibly));
        int status = child.waitFor();
        if (status == 3) {
            Thread.sleep(delay);
        }
        System.exit(status);
    }
}
