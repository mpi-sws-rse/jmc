package org.mpi_sws.jmc.mp.fixtures.seq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Two defects of {@code JmcThread}'s lifecycle, which {@code MpThread} does not share.
 *
 * <p>A stale thread: a {@code JmcThread} parked when its iteration fails is woken by the runtime's
 * reset with {@code null}; nothing tells it that its iteration is over, so its {@code finally} in
 * {@code run()} reports a {@code FINISH_EVENT} and joins under its old task id into whatever run is
 * current by then. The holder's own {@code finally} waits until the next run's main releases it, so
 * the late report lands in the next run deterministically.
 *
 * <p>A failing thread: an {@code Error} thrown by the body leaves {@code run()} after the {@code
 * FINISH_EVENT} and the join, and the uncaught-exception handler then reports a {@code HALT_EVENT}
 * for the finished task from the dying thread, while another task is running.
 */
class JmcThreadLifecycleTest {

    private static final long SEED = 20261027L;
    private static final String FAILURE = "deliberate failure";
    private static final CommunicationModel M = CommunicationModel.ASYN;

    record Ready(int v) implements MpMessage {}

    record Never(int v) implements MpMessage {}

    record Ping(Tid from, int v) implements MpMessage {}

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "jmcthread-lifecycle-check");
        t.setDaemon(true);
        return t;
    });

    static Future<JmcModelCheckerReport> start(String name, EventLogStrategy s, Runnable body) {
        return POOL.submit(() -> new JmcModelChecker(new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").strategyConstructor(c -> s).numIterations(1).seed(7L)
                .reportPath("build/test-results/jmcthread-lifecycle/" + name).build())
                .check(new JmcFunctionalTestTarget(name, body::run)));
    }

    static String outcome(Future<JmcModelCheckerReport> f, long seconds) {
        try {
            JmcModelCheckerReport r = f.get(seconds, TimeUnit.SECONDS);
            return "total " + r.getTotalIterations() + " blocked " + r.getBlockedIterations();
        } catch (TimeoutException e) {
            return "TIMEOUT after " + seconds + " s";
        } catch (ExecutionException e) {
            return "failed: " + e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    static void awaitQuietly(CountDownLatch l) {
        try {
            l.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Disabled("Fails today: a JmcThread has no iteration counter, so a thread left over from a failed run "
            + "reports its FINISH event and join into the next run. Enabled once JmcThread executions are "
            + "scoped to their run.")
    @Test
    @Timeout(180)
    void staleJmcThread_reportsNothingIntoTheNextRun() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> holder = new AtomicReference<>();
        EventLogStrategy first = new EventLogStrategy(7L, "build/test-results/jmcthread-lifecycle/holding");
        Future<JmcModelCheckerReport> run1 = start("holding", first, () -> {
            Tid main = MpProcess.self();
            JmcThreadProcess.spawn("holder", () -> {
                holder.set(Thread.currentThread());
                try {
                    Channel.send(M, main, new Ready(0));
                    Channel.recv(M, Never.class);
                } finally {
                    awaitQuietly(release);
                }
            });
            Channel.recv(M, Ready.class);
            MpProcess.spawn("failer", () -> {
                throw new IllegalStateException(FAILURE);
            }).await();
        });
        String o1 = outcome(run1, 60);
        assertTrue(o1.contains(FAILURE), "the holding run fails with the deliberate failure: " + o1);

        EventLogStrategy next = new EventLogStrategy(11L, "build/test-results/jmcthread-lifecycle/next");
        Future<JmcModelCheckerReport> run2 = start("next", next, () -> {
            release.countDown();
            try {
                holder.get().join(10_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Tid main = MpProcess.self();
            MpProcess.spawn("pinger", () -> Channel.send(M, main, new Ping(MpProcess.self(), 1))).await();
            Channel.recv(M, Ping.class);
        });
        String o2 = outcome(run2, 60);
        release.countDown();
        String holderName = holder.get().getName();
        List<String> log = next.log();
        List<String> threads = next.threads();
        List<String> fromHolder = new ArrayList<>();
        for (int i = 0; i < log.size(); i++) {
            if (holderName.equals(threads.get(i))) {
                fromHolder.add(log.get(i));
            }
        }
        System.out.println("STALE-JT run2 " + o2 + " log " + log + " threads " + threads);
        assertEquals(List.of("events of the stale holder in the next run: []", "next run: total 1 blocked 0"),
                List.of("events of the stale holder in the next run: " + fromHolder, "next run: " + o2));
    }

    @Disabled("Fails today: an Error thrown by a JmcThread body leaves run() after FINISH, and the uncaught-"
            + "exception handler then reports HALT from the dying thread. Enabled once HALT is reported before"
            + " FINISH.")
    @Test
    void failingJmcThread_noEventAfterItsFinish() throws Exception {
        EventSequenceTest.Result jt = EventSequenceTest.run(new SeqJtFail(), 1, SEED);
        List<String> log = jt.log();
        int finish = log.indexOf("it=0 FINISH_EVENT task=3");
        assertTrue(finish >= 0, "t2 (task 3) finishes: " + log);
        List<String> after = log.subList(finish + 1, log.size()).stream()
                .filter(l -> l.matches("it=0 \\S+ task=3( .*)?")).toList();
        assertEquals(List.of(), after, "no event of task 3 after its FINISH_EVENT: " + log);
    }
}
