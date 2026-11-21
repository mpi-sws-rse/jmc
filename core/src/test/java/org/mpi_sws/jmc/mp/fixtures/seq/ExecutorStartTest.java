package org.mpi_sws.jmc.mp.fixtures.seq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.api.util.concurrent.JmcExecutorService;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * The start events of tasks run by {@link JmcExecutorService}. A submitted task runs through
 * {@code JmcThread.runWithoutJoin()}, without {@code start()}, so its start event names the task
 * that created it. The worker threads are plain JVM threads and report no start event. The
 * expected events are main's and the two submitted tasks', whose starter is their creator; they
 * are the events this path reported before a thread's start event named its starter rather than
 * its creator, and that change must leave them as they were.
 */
class ExecutorStartTest {

    @Test
    @Timeout(120)
    void executorTasks_startEventsUnchanged() throws Exception {
        EventLogStrategy s = new EventLogStrategy(1L, "build/test-results/executor-start");
        new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .strategyConstructor(c -> s).numIterations(1).seed(1L)
                .reportPath("build/test-results/executor-start").build())
                .check(new JmcFunctionalTestTarget("executor", () -> {
                    ExecutorService executor = new JmcExecutorService(2);
                    try {
                        Future<Integer> a = executor.submit(() -> 1);
                        Future<Integer> b = executor.submit(() -> 2);
                        assertEquals(3, a.get() + b.get());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    } finally {
                        executor.shutdown();
                    }
                }));
        List<String> starts = s.log().stream().filter(l -> l.contains("START_EVENT")).toList();
        assertEquals(List.of("it=0 START_EVENT task=1 startedBy=1", "it=0 START_EVENT task=2 startedBy=1",
                "it=0 START_EVENT task=3 startedBy=1"), starts, String.valueOf(s.log()));
    }
}
