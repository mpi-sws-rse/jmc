package org.mpi_sws.jmc.api.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A process that is parked when its iteration fails is woken by the runtime's reset and unwinds on
 * its own thread, asynchronously. Its {@code finally} code (program code, no call into this package)
 * therefore runs after the checker has returned, concurrently with whatever the JVM runs next.
 *
 * <p>The run: main receives the holder's "ready", then awaits a process that throws, so the iteration
 * fails while the holder is parked on a receive nothing matches. The holder's {@code finally} sleeps
 * 500 ms (program state only) and then sets a flag. Expected: when {@code check} returns, every
 * process thread of the run has ended, so the holder is dead and its flag is set.
 */
class ProcessOutlivesIterationTest {

    private static final String FAILURE = "deliberate failure";
    private static final CommunicationModel M = CommunicationModel.ASYN;

    record Ready(int v) implements MpMessage {}

    record Never(int v) implements MpMessage {}

    static List<String> failingRun(String strategy) throws InterruptedException {
        AtomicReference<Thread> holder = new AtomicReference<>();
        AtomicBoolean cleanupDone = new AtomicBoolean();
        Runnable body = () -> {
            Tid main = MpProcess.self();
            MpProcess.spawn("holder", () -> {
                holder.set(Thread.currentThread());
                try {
                    Channel.send(M, main, new Ready(0));
                    Channel.recv(M, Never.class);
                } finally {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    cleanupDone.set(true);
                }
            });
            Channel.recv(M, Ready.class);
            MpProcess.spawn("failer", () -> {
                throw new IllegalStateException(FAILURE);
            }).await();
        };
        String thrown = "none";
        try {
            new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType(strategy).numIterations(1)
                    .seed(7L).reportPath("build/test-results/outlives-" + strategy).build())
                    .check(new JmcFunctionalTestTarget("outlives-" + strategy, body::run));
        } catch (Throwable t) {
            thrown = String.valueOf(t.getMessage()) + " | " + t.getCause();
        }
        boolean aliveAtReturn = holder.get() != null && holder.get().isAlive();
        boolean doneAtReturn = cleanupDone.get();
        if (holder.get() != null) {
            holder.get().join(10_000L);
        }
        assertTrue(thrown.contains(FAILURE), "the run fails with the deliberate failure: " + thrown);
        return List.of("holder alive when check returned: " + aliveAtReturn,
                "holder's cleanup done when check returned: " + doneAtReturn);
    }

    static final List<String> EXPECTED = List.of("holder alive when check returned: false",
            "holder's cleanup done when check returned: true");

    @Disabled("Fails today: a process parked when a failing iteration ends is woken asynchronously, so its "
            + "cleanup can still run after check() has returned. Enabled once the runtime waits for such "
            + "processes at the end of a run.")
    @Test
    @Timeout(120)
    void mpRandom_failingIteration_parkedProcessEndsBeforeCheckReturns() throws Exception {
        assertEquals(EXPECTED, failingRun("mp-random"));
    }

    @Disabled("Fails today: a process parked when a failing iteration ends is woken asynchronously, so its "
            + "cleanup can still run after check() has returned. Enabled once the runtime waits for such "
            + "processes at the end of a run.")
    @Test
    @Timeout(120)
    void must_failingIteration_parkedProcessEndsBeforeCheckReturns() throws Exception {
        assertEquals(EXPECTED, failingRun("must"));
    }
}
