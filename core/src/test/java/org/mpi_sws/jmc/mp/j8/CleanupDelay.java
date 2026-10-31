package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;

/**
 * J8 cleanup-delay fixture (the MpStaleProcessTest shape, fault-free): sleeper chooses n = 3 - chooseIndex(3) (index 0 first: n = 3)
 * and receives n values; a and b send one each. With n = 3 the third receive blocks, so the
 * iteration ends with sleeper paused in the runtime; the next iteration's first MpThread
 * constructor waits (MpThread.awaitOlderThreads) until the stale sleeper has left its finally,
 * which sleeps {@code delayMillis} only when the body did not complete. Argument: delayMillis.
 */
public final class CleanupDelay implements Runnable {
    record V(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;
    private final long delayMillis;

    public CleanupDelay(String... args) {
        this.delayMillis = args.length > 0 ? Long.parseLong(args[0].trim()) : 0L;
    }

    @Override
    public void run() {
        long delay = delayMillis;
        MpProcess.Handle sleeper = MpProcess.spawn("sleeper", () -> {
            boolean done = false;
            try {
                int n = 3 - Nondet.chooseIndex(3);
                for (int i = 0; i < n; i++) {
                    Channel.recv(M, V.class);
                }
                done = true;
            } finally {
                if (!done && delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(M, sleeper.tid(), new V(1)));
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(M, sleeper.tid(), new V(2)));
        a.await();
        b.await();
        sleeper.await();
    }
}
