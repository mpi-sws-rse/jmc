package org.mpi_sws.jmc.mp.programs.seq;

import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tester observer (J1 round 04): {@code mp-random} unchanged, plus a log of every event the strategy
 * sees as {@code it=<n> <TYPE> task=<id> [startedBy=..] [waitingTask=..] [joinedTask=..] [dst=..]}
 * and the iteration boundaries, and the name of the Java thread that delivered it.
 */
public final class EventLogStrategy extends MpRandomSchedulingStrategy {

    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private final List<String> threads = Collections.synchronizedList(new ArrayList<>());
    private volatile int iteration;

    /**
     * Creates the observer.
     *
     * @param seed the seed
     * @param reportPath the report path
     */
    public EventLogStrategy(Long seed, String reportPath) {
        super(seed, reportPath);
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) throws HaltExecutionException {
        this.iteration = iteration;
        log.add("it=" + iteration + " INIT");
        threads.add(Thread.currentThread().getName());
        super.initIteration(iteration, report);
    }

    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        StringBuilder sb = new StringBuilder("it=").append(iteration).append(' ').append(event.getType())
                .append(" task=").append(event.getTaskId());
        for (String k : List.of("startedBy", "waitingTask", "joinedTask")) {
            Object v = event.getParams() == null ? null : event.getParams().get(k);
            if (v != null) {
                sb.append(' ').append(k).append('=').append(v);
            }
        }
        Object dst = event.getParams() == null ? null : event.getParams().get("dst");
        if (dst instanceof Tid t) {
            sb.append(" dst=").append(t.raw());
        }
        log.add(sb.toString());
        threads.add(Thread.currentThread().getName());
        super.updateEvent(event);
    }

    @Override
    public void resetIteration(int iteration) {
        log.add("it=" + iteration + " RESET");
        threads.add(Thread.currentThread().getName());
        super.resetIteration(iteration);
    }

    /**
     * The log so far.
     *
     * @return a copy
     */
    public List<String> log() {
        synchronized (log) {
            return new ArrayList<>(log);
        }
    }

    /**
     * The delivering thread per log entry.
     *
     * @return a copy
     */
    public List<String> threads() {
        synchronized (threads) {
            return new ArrayList<>(threads);
        }
    }
}
