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
 * <p>{@link #self()} is the only runtime-dependent way to obtain a {@link Tid};
 * {@code Tid} itself is a pure value in {@code org.mpi_sws.jmc.mp.events}.
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

    /** A handle to a spawned process. */
    public static final class Handle {
        private final Tid tid;
        private final MpThread thread;

        private Handle(Tid tid, MpThread thread) {
            this.tid = tid;
            this.thread = thread;
        }

        /**
         * The process's identifier, for addressing sends to it.
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
