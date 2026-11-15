package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Processes: spawn, join, and "who am I" — over {@link MpThread}, which reproduces
 * {@code JmcThread}'s task machinery (task ids, the {@code START}/{@code FINISH}/join events the
 * strategy uses to propagate causal clocks; spawn and join order the events of the spawning and
 * joining task after/before those of the process) and additionally hands a process's failure to
 * the task that awaits it, so that an {@code AssertionError} in a process reaches the checker.
 *
 * <p>A process's {@link Tid} comes from its handle: {@link Handle#tid()} after {@link #spawn}, or
 * {@link Pending#tid()} after {@link #create}, which names a process before it runs, so that processes
 * can address each other (create them all, then start each with a body that captures the others'
 * tids). {@link #self()} gives the calling process's own. Do not compute task ids: a task id is the
 * position of the task among all runtime task allocations of the execution (main is 1, the k-th
 * allocation counting from 1 is task k + 1, daemons and processes created by processes included), it is
 * reset for every execution, and a {@code Tid} is valid only within the execution that produced it
 * (ids are reused, so a stale {@code Tid} cannot be told from a current one). {@code Tid} itself is a
 * pure value in {@code org.mpi_sws.jmc.mp.events}.
 *
 * <p>{@link #assume(boolean)} blocks the calling process when its condition is false.
 */
public final class MpProcess {

    private MpProcess() {}

    /**
     * Runs a Must-API program's main body, then, <b>only if daemons were spawned</b>, lets them run
     * on until no task can move: after main returns, daemons keep running until each finishes or
     * blocks. The harness calls this instead of {@code body.run()}.
     *
     * <p>The final step is one blocking {@code MP_RECV_REQUEST_EVENT} on main, guard accepting
     * nothing, marked {@code drainDaemons}: not a message event, so it appears in no execution
     * graph or summary. The strategy answers it when no other task can move. A program without
     * daemons raises no extra event, so its event sequence is unchanged.
     *
     * @param body the program's main body
     * @throws IllegalStateException if called from a process thread
     * @throws HaltExecutionException if the strategy delivers a fault detected on another task
     */
    public static void runMain(Runnable body) {
        if (Thread.currentThread() instanceof MpThread) {
            // Main only: a process thread never runs a program's main body.
            throw new IllegalStateException("MpProcess.runMain is for the program's main task, not a process");
        }
        MpThread.resetDaemonCount();
        body.run();
        if (MpThread.daemonsCreated() > 0) {
            // Counted in MpThread's constructor, so a daemon made with new MpThread(body, true)
            // counts too.
            Long main = JmcRuntime.currentTask();
            Object answer = JmcRuntime.updateEventAndYield(
                    new JmcRuntimeEvent.Builder()
                            .type(JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT)
                            .taskId(main)
                            .param("model", CommunicationModel.P2P)
                            .param("guard", (Predicate<MpMessage>) m -> false)
                            .param("blocking", true)
                            .param("drainDaemons", true)
                            .build());
            if (answer instanceof MpFaultValue f) {
                throw HaltExecutionException.error(f.message());
            }
        }
    }

    /**
     * Spawns {@code body} as a daemon process and start it (as TraceForge's
     * {@code spawn_daemon}). A daemon has no role and is never visible; main need not await it, and
     * its handle is not awaitable. Its ending blocked on a receive does not make the execution
     * blocked; blocked any other way, it does. The program must run under {@link #runMain}.
     *
     * @param body the process body
     * @return a handle for addressing it
     */
    public static Handle spawnDaemon(Runnable body) {
        Objects.requireNonNull(body, "body");
        MpThread thread = new MpThread(body, true);
        thread.start();
        return new Handle(Tid.of(thread.getTaskId()), thread);
    }

    /**
     * Creates a process with a role without starting it: its id is allocated and its creation is
     * reported now (as by {@link #spawn}), so {@link Pending#tid()} can be used at once, as a send
     * destination or inside payloads; {@link Pending#start(Runnable)} gives it its body and starts it.
     * Only the creating task may start it; a process created and never started fails the execution at
     * its end.
     *
     * @param role the role name; the empty string for none
     * @return the not yet started process
     */
    public static Pending create(String role) {
        Objects.requireNonNull(role, "role");
        return new Pending(new MpThread(role, false), role, false);
    }

    /**
     * Creates a daemon process without starting it, as {@link #create} does for a process; started with
     * {@link Pending#start(Runnable)}, it behaves as one made by {@link #spawnDaemon}.
     *
     * @return the not yet started daemon
     */
    public static Pending createDaemon() {
        return new Pending(new MpThread("", true), "", true);
    }

    /**
     * Blocks the calling process unless {@code cond} holds: {@code assume(false)} stops the process at
     * this point with status {@code blocked} (as an assume does in the paper and in TraceForge); the
     * other processes continue and the execution is explored and exported as usual. {@code assume(true)}
     * returns at once. For a process other than main it is exactly a blocking receive that nothing can
     * match; a daemon's failed assume makes the execution blocked (an unmatched receive of a daemon does
     * not). Statements after a failed assume never run; Java cleanup ({@code finally}) still runs while
     * the process unwinds, and must not use this API.
     *
     * @param cond the condition
     * @throws HaltExecutionException if the strategy delivers a fault detected on another task
     */
    public static void assume(boolean cond) {
        if (cond) {
            return;
        }
        MpThread.exitIfStale();
        JmcRuntimeEvent request =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("model", CommunicationModel.P2P)
                        .param("guard", (Predicate<MpMessage>) m -> false)
                        .param("blocking", true)
                        .param("assume", true)
                        .build();
        // A HaltTaskException (the strategy blocked the caller) propagates, ending the process.
        Object answer = JmcRuntime.updateEventAndYield(request);
        MpThread.exitIfStale();
        if (answer instanceof MpFaultValue f) {
            throw HaltExecutionException.error(f.message());
        }
        throw new IllegalStateException("an assume(false) was answered");
    }

    /**
     * The identifier of the calling process.
     *
     * @return the current task's id
     */
    public static Tid self() {
        MpThread.exitIfStale();
        return Tid.of(JmcRuntime.currentTask());
    }

    /**
     * Spawns {@code body} as a new process and starts it.
     *
     * @param body the process body
     * @return a handle for addressing and joining it
     */
    public static Handle spawn(Runnable body) {
        return spawn("", body);
    }

    /**
     * Spawns {@code body} as a new process with a role and starts it. The role travels on the
     * creation event; an export identifies a visible process by this name, as TraceForge
     * identifies visible threads by declared name, not by position.
     *
     * @param role the role name; the empty string for none
     * @param body the process body
     * @return a handle for addressing and joining it
     */
    public static Handle spawn(String role, Runnable body) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(body, "body");
        MpThread thread = new MpThread(body, role);
        thread.start();
        return new Handle(Tid.of(thread.getTaskId(), role), thread);
    }

    /** A process created by {@link #create} or {@link #createDaemon} and not yet started. */
    public static final class Pending {
        private final MpThread thread;
        private final String role;
        private final boolean daemon;
        private boolean started;

        private Pending(MpThread thread, String role, boolean daemon) {
            this.thread = thread;
            this.role = role;
            this.daemon = daemon;
        }

        /**
         * The process's identifier, valid from creation on and only in this execution.
         *
         * @return the id
         * @throws IllegalStateException if the process belongs to an earlier execution
         */
        public Tid tid() {
            requireCurrent();
            return daemon ? Tid.of(thread.getTaskId()) : Tid.of(thread.getTaskId(), role);
        }

        /**
         * Gives the process its body and starts it, as {@link #spawn} starts a process.
         *
         * @param body the process body
         * @return a handle for joining it (not awaitable for a daemon)
         * @throws NullPointerException if {@code body} is null (the process can still be started)
         * @throws IllegalStateException if the process belongs to an earlier execution, the caller is not
         *     the task that created it, or it was already started
         */
        public Handle start(Runnable body) {
            Objects.requireNonNull(body, "body");
            requireCurrent();
            Long caller = JmcRuntime.currentTask();
            if (!Objects.equals(caller, thread.createdBy())) {
                throw new IllegalStateException("process " + label() + " was created by task " + thread.createdBy()
                        + "; only its creator may start it");
            }
            if (started) {
                throw new IllegalStateException("process " + label() + " was already started");
            }
            started = true;
            thread.install(body);
            thread.start();
            return new Handle(tid(), thread);
        }

        private void requireCurrent() {
            if (thread.epoch() != MpThread.currentEpoch()) {
                throw new IllegalStateException("process " + label() + " belongs to an earlier execution");
            }
        }

        private String label() {
            return role.isEmpty() ? "task " + thread.getTaskId() : role;
        }
    }

    /** A handle to a spawned process. */
    public static final class Handle {
        private final Tid tid;
        private final MpThread thread;

        private Handle(Tid tid, MpThread thread) {
            this.tid = tid;
            this.thread = thread;
        }

        /**
         * The process's identifier, for addressing sends to it; valid only in this execution.
         *
         * @return the id
         */
        public Tid tid() {
            return tid;
        }

        /**
         * Waits for the process to terminate — cooperatively, like every other JMC join.
         *
         * <p>May throw a {@code HaltExecutionException}: the strategy delivers a fault it detected
         * on another thread on main's next event, and the join is such an event. Like everything a
         * message-passing call throws, it is JMC control flow and must propagate (see
         * {@link Channel}). It also rethrows, on the awaiting thread, whatever ended the process's
         * body other than JMC control flow — an {@code AssertionError} in particular — so that a
         * failed process fails the iteration at the checker when main awaits it ({@link MpThread}).
         *
         * <p><b>Every spawned process must be awaited</b> (or must have terminated) before main
         * returns. JMC does not wait for spawned tasks: a process still running or parked when main
         * returns is abandoned, not blocked — it is woken with {@code null} at the iteration reset.
         * {@code mp-random} refuses this loudly at main's end. Such a woken thread ends without
         * events (see the iteration counter in {@link MpThread}), so it cannot reach the next
         * iteration or run. A daemon ({@link #spawnDaemon}) is not awaited.
         *
         * <p>A process that ended <em>blocked</em> (its blocking receive was blocked by the
         * strategy, Must's blocked label) makes its awaiter block too: this method does not return,
         * and the iteration ends blocked (as TraceForge's {@code Block(Join)}).
         * Awaiting a process that is blocked blocks the caller.
         * Statements after the await therefore run only when the process finished normally.
         *
         * <p>Named {@code await}, not {@code join}, on purpose: the agent's
         * {@code ThreadCallReplacerClassVisitor} rewrites <em>every</em> {@code INVOKEVIRTUAL}
         * named {@code join()V} on any receiver into a guarded call that casts the receiver to
         * {@code Thread}, and a class calling {@code Handle.join()} fails bytecode verification at
         * test discovery. No method of this API may be named {@code join} or
         * {@code yield}.
         */
        public void await() {
            try {
                thread.join1();
            } catch (InterruptedException e) {
                throw new IllegalStateException("interrupted while joining " + tid, e);
            }
        }
    }
}
