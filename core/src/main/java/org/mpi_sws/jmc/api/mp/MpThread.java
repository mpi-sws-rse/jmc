package org.mpi_sws.jmc.api.mp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * The thread a Must process runs on: {@link org.mpi_sws.jmc.api.util.concurrent.JmcThread}'s
 * runtime handshake, reproduced exactly, plus one thing {@code JmcThread} lacks — a failure of the
 * process body is handed to whoever awaits the process.
 *
 * <p>The handshake, identical to {@code JmcThread}'s so that a strategy sees the same events in the
 * same order: the task id is minted at construction ({@link JmcRuntime#addNewTask()}) and the
 * creating task recorded; {@link #start()} pauses the parent, starts the JVM thread and waits;
 * {@link #run()} reports {@code START_EVENT} with {@code startedBy}, yields under the new task id,
 * runs the body, and in a {@code finally} reports {@code FINISH_EVENT} and joins the task;
 * {@link #join1()} reports {@code JOIN_REQUEST_EVENT} and, after the JVM join,
 * {@code JOIN_COMPLETE_EVENT}, each through a yield.
 *
 * <p>The difference: {@code JmcThread.run} catches {@code Exception} only and logs it, and an
 * {@code Error} such as an {@code AssertionError} leaves to an uncaught-exception handler whose
 * {@code HALT_EVENT} nothing consumes — the checker never learns that a process failed. Here {@code
 * run()} catches every {@link Throwable}; the two JMC control-flow signals (re-execution requested,
 * task blocked) are logged as before, and anything else is stored as the process's <em>failure</em>
 * and rethrown by {@link #join1()} on the joiner's thread, right after the {@code JOIN_COMPLETE}
 * yield. The joiner is main (a program must await every process it spawns), where the checker
 * records it as the iteration's error. Nothing escapes {@code run()}, so no uncaught-exception
 * handler is installed and no {@code HALT_EVENT} is reported.
 *
 * <p><b>Awaiting a process that ended blocked</b> (as in Must's semantics and TraceForge's
 * join): the awaiter does not return. {@link #join1()} then raises, instead of
 * {@code JOIN_COMPLETE_EVENT}, a blocking {@code MP_RECV_REQUEST_EVENT} whose guard accepts
 * nothing, marked {@code awaitOfBlocked}; the strategy parks it as it parks any blocking receive
 * with nothing to read and blocks it when nothing else can run, and the {@code HaltTaskException}
 * that blocks it propagates.
 *
 * <p><b>Creation is reported</b>: the constructor raises {@code TASK_CREATED_EVENT} on the
 * creator's thread with {@code createdTask}, an event type that only this class raises (and only
 * the message-passing strategies consume), so that a strategy can name the task when it is created
 * (as TraceForge names a thread at its spawn), before any event can carry its {@code Tid}. It is a
 * notification, not a scheduling point (no yield). Apart from it and the blocked await, the event
 * sequence is {@code JmcThread}'s.
 *
 * <p><b>Daemons</b>: the creation event carries {@code daemon}; a daemon is not awaitable
 * ({@link #join1()} refuses before raising any event). See {@link MpProcess#spawnDaemon}.
 *
 * <p><b>A failure is an error of the iteration when it happens</b> (as TraceForge ends the
 * exploration on a panic in any thread, joined or not). {@code FINISH_EVENT}
 * carries {@code failed} and {@code failure} (the throwable's {@code toString()}); the strategies
 * (mp-random, must) latch it as the iteration's fault and hand it to main on main's next event, so
 * a program cannot catch a process failure at an await. The rethrow in {@link #join1()} remains for
 * a joiner that reaches it first.
 *
 * <p>No method of this class is named {@code join} or {@code yield}: the agent's
 * {@code ThreadCallReplacerClassVisitor} rewrites every {@code join()V} call site.
 */
public final class MpThread extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(MpThread.class);

    private final Long taskId;
    private final Long createdBy;
    /** The iteration this process belongs to: {@link #EPOCH} when it was created. */
    private final long epoch;
    private final Runnable body;

    /**
     * Whether this is a daemon process (as TraceForge's {@code spawn_daemon}): not awaitable, and
     * main need not await it. Reported on the creation event.
     */
    private final boolean daemon;

    /** The role the process was spawned with, reported on the creation event; empty for none. */
    private final String role;

    /** Daemons created since {@link #resetDaemonCount()}, counted on every creation path. */
    private static final AtomicInteger DAEMONS_CREATED = new AtomicInteger();

    /**
     * The iteration counter. The message-passing strategies advance it first
     * thing in {@code resetIteration}, before the runtime's task manager wakes every task still
     * paused with {@code null}. A process thread woken that way belongs to an iteration that is
     * over (it was parked when the iteration ended with an error), and the wake can land after the
     * next iteration, or the next run in the same JVM, has started: its events would then reach the
     * new iteration's strategy under a task id the new iteration has reused (a failed {@code
     * FINISH_EVENT} failing an unrelated run; a scheduler resuming a task that does not exist; a
     * hang). Such a thread now ends silently: every runtime call of this package checks the counter
     * first ({@link #exitIfStale()}), and {@link #run()} raises no {@code FINISH_EVENT} and no join
     * for it.
     *
     * <p>That alone is not enough: a stale thread wakes inside the runtime's own wait,
     * which removes the task's future <em>by id</em> before control returns here; if the next
     * iteration had already reused the id, it would remove the new task's future. So no process is
     * created while a process thread of an earlier iteration is still alive ({@link #ALIVE},
     * {@link #awaitOlderThreads}): ids are reused only after every stale thread has finished its
     * cleanup.
     *
     * <p>Limit: a stale thread unwinds with {@link Stale}, but Java still runs the body's
     * {@code finally} code. Calls into this package from there end it again; code that touches only
     * the program's own state, blocks or loops cannot be stopped — the next process creation then
     * fails loudly after {@link #STALE_WAIT_MILLIS} instead of hanging.
     */
    private static final AtomicLong EPOCH = new AtomicLong();

    /** Process threads started and not yet returned from {@link #run()}; guarded by itself. */
    private static final Set<MpThread> ALIVE = new HashSet<>();

    /** How long a process creation waits for the threads of earlier iterations to end. */
    private static final long STALE_WAIT_MILLIS = 30_000L;

    /** What ended the body, other than JMC control flow; rethrown to the joiner by {@link #join1()}. */
    private volatile Throwable failure;

    /** Whether the body ended because the strategy blocked the task (Must's blocked label). */
    private volatile boolean endedBlocked;

    /**
     * Creates the thread for a process body; mints the task id now, as {@code JmcThread} does.
     *
     * @param body the process body
     */
    public MpThread(Runnable body) {
        this(body, false, "");
    }

    /**
     * Creates the thread for a process body with a role; mints the task id now. The
     * role travels on the creation event; an export identifies a process by it, not by position.
     *
     * @param body the process body
     * @param role the role name; the empty string for none
     */
    public MpThread(Runnable body, String role) {
        this(body, false, role);
    }

    /**
     * Creates the thread for a process body, a daemon or not; mints the task id now.
     *
     * @param body the process body
     * @param daemon whether the process is a daemon: main need not await it, it is not awaitable,
     *     and its ending blocked on a receive does not make the execution blocked
     */
    public MpThread(Runnable body, boolean daemon) {
        this(body, daemon, "");
    }

    private MpThread(Runnable body, boolean daemon, String role) {
        super();
        this.body = Objects.requireNonNull(body, "body");
        this.daemon = daemon;
        this.role = Objects.requireNonNull(role, "role");
        exitIfStale();
        this.epoch = EPOCH.get();
        awaitOlderThreads(epoch);
        if (daemon) {
            DAEMONS_CREATED.incrementAndGet();
        }
        this.taskId = JmcRuntime.addNewTask();
        this.createdBy = JmcRuntime.currentTask();
        // Tell the strategy now, on the creator's thread, so that it can name the task by
        // its creation path before the program uses its Tid (a send to it, a payload naming it).
        JmcRuntime.updateEvent(
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.TASK_CREATED_EVENT)
                        .taskId(createdBy)
                        .param("createdTask", taskId)
                        .param("daemon", daemon)
                        .param("role", role)
                        .build());
    }

    /**
     * The JMC task id of this process.
     *
     * @return the id
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * Starts the process under JMC's control, exactly as {@code JmcThread.start()}: pauses the
     * current task, starts the JVM thread, and waits until the runtime resumes the parent.
     */
    @Override
    public void start() {
        exitIfStale();
        Long current = JmcRuntime.currentTask();
        JmcRuntime.pause(current);
        boolean added;
        synchronized (ALIVE) {
            // false for a thread already running (a second start): its registration stays
            added = ALIVE.add(this);
        }
        try {
            super.start();
        } catch (RuntimeException | Error e) {
            if (added) {
                // No harness will run to remove it: a failed start must not hold up later
                // creations.
                synchronized (ALIVE) {
                    ALIVE.remove(this);
                    ALIVE.notifyAll();
                }
            }
            throw e;
        }
        JmcRuntime.wait(current);
        exitIfStale();
    }

    /** The harness; see the class comment. */
    @Override
    public void run() {
        try {
            harness();
        } finally {
            synchronized (ALIVE) {
                ALIVE.remove(this);
                ALIVE.notifyAll();
            }
        }
    }

    private void harness() {
        if (epoch != EPOCH.get()) {
            LOGGER.debug("Process {} started after its iteration ended; ending without events", taskId);
            return;
        }
        JmcRuntimeEvent start =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.START_EVENT)
                        .taskId(taskId)
                        .param("startedBy", createdBy)
                        .build();
        try {
            JmcRuntime.updateEvent(start);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to start task: {}", e.getMessage());
        }
        boolean stale = false;
        try {
            JmcRuntime.yield(taskId);
            exitIfStale();
            body.run();
        } catch (Stale e) {
            stale = true;
        } catch (Throwable e) {
            if (e instanceof HaltExecutionException h && h.isReexecutionNeeded()) {
                LOGGER.debug("Re-execution needed, throwing HaltExecutionException");
            } else if (e instanceof HaltTaskException h && h.isBlocked()) {
                LOGGER.debug("Blocked task execution, throwing HaltTaskException");
                endedBlocked = true;
            } else {
                LOGGER.error("Exception running the process: {}", e.toString());
                failure = e;
            }
        }
        if (stale || epoch != EPOCH.get()) {
            // The iteration this process belonged to is over: report nothing to the next one.
            LOGGER.debug("Process {} outlived its iteration; ending without events", taskId);
            return;
        }
        JmcRuntimeEvent finish =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.FINISH_EVENT)
                        .taskId(taskId)
                        .param("failed", failure != null)
                        .param("failure", String.valueOf(failure))
                        .build();
        try {
            JmcRuntime.updateEvent(finish);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to finish task : {}", e.getMessage());
        }
        JmcRuntime.join(taskId);
    }

    /**
     * Awaits the process, exactly as {@code JmcThread.join1()}, then rethrows its failure, if any,
     * on the calling thread.
     *
     * @throws InterruptedException if the JVM join is interrupted
     */
    public void join1() throws InterruptedException {
        exitIfStale();
        if (daemon) {
            throw new IllegalStateException("process " + taskId + " is a daemon, which is not awaitable");
        }
        Long requestingTask = JmcRuntime.currentTask();
        JmcRuntimeEvent request =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT)
                        .taskId(requestingTask)
                        .param("waitingTask", taskId)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(request);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to join task : {}", e.getMessage());
        }
        exitIfStale();
        super.join(0L);
        exitIfStale();
        if (endedBlocked) {
            awaitBlocked(requestingTask);
        }
        JmcRuntimeEvent completed =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.JOIN_COMPLETE_EVENT)
                        .taskId(requestingTask)
                        .param("joinedTask", taskId)
                        .build();
        try {
            JmcRuntime.updateEventAndYield(completed);
        } catch (HaltTaskException e) {
            LOGGER.error("Failed to complete join task : {}", e.getMessage());
        }
        exitIfStale();
        Throwable f = failure;
        if (f != null) {
            if (f instanceof Error er) {
                throw er;
            }
            if (f instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("process " + taskId + " failed", f);
        }
    }

    /**
     * The awaiter of a process that ended blocked blocks too (class comment). Raises a blocking
     * receive that nothing can match and lets the block end the awaiter; never returns normally.
     */
    private void awaitBlocked(Long requestingTask) {
        exitIfStale();
        JmcRuntimeEvent request =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT)
                        .taskId(requestingTask)
                        .param("model", CommunicationModel.P2P)
                        .param("guard", (Predicate<MpMessage>) m -> false)
                        .param("blocking", true)
                        .param("awaitOfBlocked", taskId)
                        .build();
        // A HaltTaskException (the strategy blocked the awaiter) propagates, ending the awaiter.
        Object answer = JmcRuntime.updateEventAndYield(request);
        exitIfStale();
        if (answer instanceof MpFaultValue f) {
            throw HaltExecutionException.error(f.message());
        }
        throw new IllegalStateException("contract violation: the strategy resumed task " + requestingTask
                + " (answer " + answer + ") past its await of process " + taskId + ", which ended blocked");
    }

    /**
     * The throwable that ended the body, if any — for tests.
     *
     * @return the failure, or {@code null}
     */
    public Throwable failure() {
        return failure;
    }

    /**
     * Advances the iteration counter ({@link #EPOCH}); the message-passing strategies call it first
     * thing in {@code resetIteration}, before the runtime wakes the tasks still paused.
     */
    public static void newEpoch() {
        EPOCH.incrementAndGet();
    }

    /**
     * Waits until no process thread of an iteration before {@code current} is alive (class field
     * {@link #EPOCH}): its cleanup may still remove a task future by an id this iteration is about
     * to reuse. Normally returns at once; after an iteration that ended with a paused process it
     * waits for that thread's silent exit.
     *
     * @throws IllegalStateException if a thread of an earlier iteration is still alive after
     *     {@link #STALE_WAIT_MILLIS}: a body's {@code finally} code blocked or looped
     */
    private static void awaitOlderThreads(long current) {
        long deadline = System.currentTimeMillis() + STALE_WAIT_MILLIS;
        synchronized (ALIVE) {
            while (true) {
                List<Long> older = new ArrayList<>();
                for (MpThread t : ALIVE) {
                    if (t.epoch < current) {
                        older.add(t.taskId);
                    }
                }
                if (older.isEmpty()) {
                    return;
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new IllegalStateException("process thread(s) " + older + " of an earlier iteration"
                            + " are still running " + STALE_WAIT_MILLIS + " ms after it ended; a process"
                            + " body's finally code must not block or loop");
                }
                try {
                    ALIVE.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for the processes of an"
                            + " earlier iteration to end", e);
                }
            }
        }
    }

    /**
     * Ends the calling thread, by throwing {@link Stale}, if it is a process thread whose iteration
     * is over; does nothing on main or on a current process. Called after every runtime call of
     * this package that can wake a thread, and before every call that raises an event or changes
     * this package's shared state.
     */
    static void exitIfStale() {
        if (Thread.currentThread() instanceof MpThread t && t.epoch != EPOCH.get()) {
            throw new Stale(t.taskId);
        }
    }

    /**
     * Unwinds a process thread that outlived its iteration. An {@code Error}, so that a body
     * catching {@code Exception} does not swallow it; {@link #run()} catches it and ends without
     * events.
     */
    static final class Stale extends Error {
        private static final long serialVersionUID = 1L;

        Stale(Long taskId) {
            super("process " + taskId + " outlived its iteration", null, false, false);
        }
    }

    /** Resets the daemon count; {@link MpProcess#runMain} calls it before the body. */
    static void resetDaemonCount() {
        DAEMONS_CREATED.set(0);
    }

    /**
     * The daemons created since the last reset, through any constructor.
     *
     * @return the count
     */
    static int daemonsCreated() {
        return DAEMONS_CREATED.get();
    }

    /**
     * Whether this process is a daemon.
     *
     * @return the flag
     */
    public boolean isMpDaemon() {
        return daemon;
    }

    /**
     * The role this process was spawned with.
     *
     * @return the role; empty for none
     */
    public String role() {
        return role;
    }

    @Override
    public String toString() {
        return "MpThread-" + taskId;
    }
}
