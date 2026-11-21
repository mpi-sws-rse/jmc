package org.mpi_sws.jmc.mp.fixtures.seq;

import org.mpi_sws.jmc.api.util.concurrent.JmcThread;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.JmcRuntime;

import java.util.Objects;

/**
 * Reference: a private copy of an earlier {@code MpProcess}, which spawned over {@link JmcThread}
 * directly. Used only to compare the event
 * sequence a strategy sees with the one {@code MpProcess} over {@code MpThread} produces.
 */
public final class JmcThreadProcess {

    private JmcThreadProcess() {}

    /**
     * Spawns a process over {@link JmcThread}, exactly as that earlier {@code MpProcess.spawn}.
     *
     * @param role the role
     * @param body the body
     * @return the handle
     */
    public static Handle spawn(String role, Runnable body) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(body, "body");
        JmcThread thread = new JmcThread(body);
        thread.start();
        return new Handle(Tid.of(thread.getTaskId(), role), thread);
    }

    /**
     * The current task, as that earlier {@code MpProcess.self}.
     *
     * @return the tid
     */
    public static Tid self() {
        return Tid.of(JmcRuntime.currentTask());
    }

    /** A spawned process. */
    public static final class Handle {
        private final Tid tid;
        private final JmcThread thread;

        private Handle(Tid tid, JmcThread thread) {
            this.tid = tid;
            this.thread = thread;
        }

        /**
         * The process id.
         *
         * @return the tid
         */
        public Tid tid() {
            return tid;
        }

        /** Joins, as that earlier {@code Handle.await}. */
        public void await() {
            try {
                thread.join1();
            } catch (InterruptedException e) {
                throw new IllegalStateException("interrupted while joining " + tid, e);
            }
        }
    }
}
