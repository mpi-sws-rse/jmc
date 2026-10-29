package org.mpi_sws.jmc.strategies.mp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.consistency.TaskClocks;
import org.mpi_sws.jmc.mp.consistency.VectorClock;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.semantics.Buffers;
import org.mpi_sws.jmc.mp.semantics.Delivery;
import org.mpi_sws.jmc.mp.semantics.Pending;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValue;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The random sampling strategy for message-passing programs, registered as {@code "mp-random"}:
 * the {@link RandomSchedulingStrategy} plus ownership of the communication state and an answer
 * for every message-passing event.
 *
 * <p>The strategy, not the API, owns the following state, which is rebuilt every iteration:
 * <ul>
 *   <li>the pending messages, in a {@link Buffers} created {@link Buffers#withClocks() with clocks}
 *       (this strategy does not merge states, so clocks cost nothing, and enabling them
 *       unconditionally makes causally-delivered ({@code cd}) models correct without knowing the
 *       program's set of models);</li>
 *   <li>the per-task vector clocks ({@link TaskClocks}), joined on spawn ({@code START_EVENT}'s
 *       {@code startedBy}), on join ({@code JOIN_COMPLETE_EVENT}'s {@code joinedTask}), on every
 *       delivery, and ticked on every emission;</li>
 *   <li>the per-task send counter that gives each send its {@code sendIndex}; the index is
 *       assigned here, never by the API.</li>
 * </ul>
 *
 * <p>How it answers:
 * <ul>
 *   <li>{@code MP_SEND_EVENT}: emit into the buffers, then re-examine any receive parked on that
 *       destination.</li>
 *   <li>{@code MP_RECV_REQUEST_EVENT}: compute the candidate set with
 *       {@link Delivery#deliverable} and, for a non-blocking receive, add {@code ⊥} <em>whether or
 *       not anything is pending</em>; pick one uniformly at random with the seeded generator and
 *       hand it back as an {@link MpDeliveryValue} on the task's next scheduling choice. A
 *       blocking receive with an empty candidate set is <b>parked</b>: the task stays out of
 *       scheduling until a send to it arrives and the request is re-resolved <em>with an
 *       answer</em>; a task whose request is open is always either parked or answered. If nothing
 *       else can run while receives are parked, the parked tasks are blocked
 *       ({@link SchedulingChoice#blockTask}), as in the Must algorithm's blocked executions, in
 *       <b>descending task id</b>. This way every spawned process unwinds ({@code FINISH_EVENT},
 *       join) before main is blocked and the iteration ends with no task left paused; a paused
 *       task would be woken with {@code null} at reset and its late {@code FINISH_EVENT} would
 *       land in the next iteration. The iteration is counted as <b>blocked</b> in the report the
 *       first time this happens ({@link JmcModelCheckerReport#setBlockedIterations}), which is
 *       what {@code @JmcExpectExecutions} subtracts.</li>
 *   <li>{@code ND_EVENT}: a uniformly random index, as a {@link PrimitiveValue}.</li>
 * </ul>
 *
 * <p><b>After a block.</b> A blocked task counts as finished under JMC's conventions: whoever
 * awaits it proceeds, and if main is the one blocked the checker ends the iteration. The rest of
 * the program therefore runs on after a block, and assertions placed after {@code Handle.await()}
 * of a blocked process are not evidence about the protocol; only the blocked-iteration count is.
 * Which parked receiver is blocked first is a semantic choice: if receiver {@code R} is parked
 * and the only sender to {@code R} awaits another parked task {@code T}, blocking {@code T} first
 * lets {@code R} be answered, while blocking {@code R} first loses that behaviour. Descending id
 * is the order that keeps the runtime sound; the behaviours it loses are a sampling limitation of
 * this strategy, like its not rescheduling a blocked receive across iterations. The exhaustive
 * Must strategy treats blocked executions systematically.
 *
 * <p><b>Daemons.</b> A daemon process (see {@code MpProcess.spawnDaemon}) is never awaited. A
 * daemon blocked on a receive does not make the iteration count as blocked. After main's body
 * returns, main raises a final request ({@code drainDaemons}) that is answered only when no other
 * task can move; once it is answered, daemons are exempt from the rule that spawned processes
 * must be awaited.
 *
 * <p><b>Guards.</b> A receive guard is evaluated here, inside
 * {@link #updateEvent(JmcRuntimeEvent)}, on the thread that triggered the evaluation: the
 * requester, or a later sender whose message re-resolves a parked receive. A guard that performs
 * an instrumented access (a read of a field of an instrumented class, or any JMC API call)
 * re-enters the runtime from inside the strategy, which is unsound: the evaluating thread would
 * pause mid-resolve. Such a guard is detected and reported as a {@link HaltExecutionException},
 * thrown at the point of detection and, because a spawned task's exceptions are only logged by
 * {@code JmcThread.run}, <em>latched</em> so that it fails the run whichever thread evaluated the
 * guard. Delivery to main is single-shot: the fault is thrown on main's next event, or, if main
 * is itself parked, main is resumed with an {@link MpFaultValue} that the API turns into the
 * exception on main's thread. The latch is released only when the <em>checker</em> has recorded
 * the error for the iteration, never on delivery: at {@link #resetIteration(int)}, a latched
 * fault that the report does not show (main halted without an event of its own, or main
 * <b>caught</b> the delivered exception and went on) is thrown from there as a last resort. That
 * throw skips the runtime's own task reset, which the checker's tear-down performs instead. This
 * is why the exceptions thrown by {@code Channel.recv} are JMC control flow and must propagate.
 * A faulted iteration is not counted as blocked. A guard must read only its argument and
 * captured locals or final fields.
 *
 * <p><b>Every spawned process must be awaited.</b> JMC ends the iteration when main returns and
 * does not wait for spawned tasks; a process still started-and-not-finished at main's
 * {@code FINISH_EVENT} would be woken with {@code null} at reset and its late events would
 * corrupt the next iteration (observed as a hang or a false guard fault). The strategy therefore
 * refuses it: main's {@code FINISH_EVENT} with live processes throws a
 * {@link HaltExecutionException} naming them. The Must algorithm would treat such an execution as
 * blocked and explore on; that would require the checker to yield after main's end.
 *
 * <p>The choice among candidates is the only decision this class makes; the semantics in
 * {@code org.mpi_sws.jmc.mp.semantics} only computes the set of allowed choices. Replacing the
 * random pick with "the first candidate" would change what is explored.
 *
 * <p>This strategy <em>samples</em>. It is not exhaustive and does not reschedule a blocked
 * receive across iterations; exhaustive exploration is done by the Must strategy. The
 * message-passing answers are recorded in the trace, but replay of an {@code mp-random} run is
 * not supported.
 *
 * <p>Not final, so a test may subclass it and override {@link #resetIteration(int)} to snapshot
 * {@link #buffers()} and {@link #clocks()} at the end of an iteration before they are cleared,
 * which is the supported way to observe the strategy's state.
 */
public class MpRandomSchedulingStrategy extends RandomSchedulingStrategy
        implements MpAwareStrategy {

    private static final Logger LOGGER = LogManager.getLogger(MpRandomSchedulingStrategy.class);

    /** A blocking receive waiting for a message: what it asked for. */
    private record ParkedReceive(CommunicationModel model, Predicate<MpMessage> guard) {}

    private Buffers buffers = Buffers.withClocks();
    private TaskClocks clocks = TaskClocks.EMPTY;
    private final Map<Long, Long> sendCount = new HashMap<>();
    private final Map<Long, SchedulingChoiceValue> answers = new HashMap<>();
    private final TreeMap<Long, ParkedReceive> parked = new TreeMap<>();

    /**
     * The report of the current check, kept from {@link #initIteration} to count blocked
     * iterations.
     */
    private JmcModelCheckerReport report;

    /** Blocked iterations so far in this check; written to the report as it grows. */
    private int blockedIterations;

    /** Whether the current iteration has already been counted as blocked. */
    private boolean iterationBlocked;

    /** Set while a guard is being evaluated; an event arriving meanwhile is a re-entrant guard. */
    private boolean resolving;

    /** A fault detected this iteration, held until the checker records it (see class doc). */
    private HaltExecutionException fault;

    /**
     * Whether {@link #fault} has been handed to main once, either by throwing it on main's next
     * event or by resuming a parked main with an {@link MpFaultValue}.
     */
    private boolean delivered;

    /** Tasks that reported {@code START_EVENT} and not yet {@code FINISH_EVENT}, main excluded. */
    private final Set<Long> live = new TreeSet<>();

    /** Daemon processes of this iteration (the creation event's {@code daemon} parameter). */
    private final Set<Long> daemons = new TreeSet<>();

    /**
     * Tasks parked on an await of a blocked process; their block is not that of a receive
     * (see the {@code awaitOfBlocked} parameter of the request event).
     */
    private final Set<Long> awaitBlocked = new TreeSet<>();

    /** Whether main is parked on its final request ({@code drainDaemons}). */
    private boolean mainDraining;

    /** Whether main's final request ({@code drainDaemons}) has been answered. */
    private boolean drained;

    /**
     * Creates the strategy.
     *
     * @param seed the random seed
     * @param reportPath where traces are written
     */
    public MpRandomSchedulingStrategy(Long seed, String reportPath) {
        super(seed, reportPath);
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report)
            throws HaltExecutionException {
        super.initIteration(iteration, report);
        this.report = report;
        iterationBlocked = false;
        if (iteration == 0) {
            blockedIterations = 0;
        }
    }

    /**
     * Handles one event. Tracker bookkeeping goes to the superclass; the message-passing events,
     * and the spawn/join events that carry clock information, are handled as the class doc
     * describes. A re-entrant event, one arriving while a guard is being evaluated, is a fault.
     *
     * @param event the event
     * @throws HaltTaskException never from this class; propagated from the superclass
     * @throws HaltExecutionException on a re-entrant guard; on main's next event after a fault
     *     detected on another task's thread; or on main's {@code FINISH_EVENT} or
     *     {@code ASSUME_EVENT(false)} while a spawned process is still live
     */
    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        long task = event.getTaskId();
        JmcRuntimeEvent.Type type = event.getType();
        if (resolving) {
            fail("a receive guard performed an instrumented access (" + type + " from task " + task
                    + " while a guard was being evaluated); a guard may read only its argument and "
                    + "captured locals or final fields", task == 1L);
        }
        if (fault != null && !delivered && task == 1L) {
            throw deliverFault();
        }
        super.updateEvent(event);
        if (type == JmcRuntimeEvent.Type.TASK_CREATED_EVENT) {
            if (Boolean.TRUE.equals(optParam(event, "daemon")) && optParam(event, "createdTask") instanceof Long c) {
                daemons.add(c);
            }
        } else if (type == JmcRuntimeEvent.Type.START_EVENT) {
            Long startedBy = event.getParam("startedBy");
            if (startedBy != null) {
                clocks = clocks.spawn(startedBy, task);
            }
            if (task != 1L) {
                live.add(task);
            }
        } else if (type == JmcRuntimeEvent.Type.FINISH_EVENT) {
            live.remove(task);
            if (task == 1L) {
                requireNoLiveProcess("returned");
            } else if (Boolean.TRUE.equals(optParam(event, "failed"))) {
                // A process failure is an error of the iteration when it happens, whether or not
                // the process is awaited: latched on the failing thread, delivered to main like
                // any latched fault.
                latch("process " + task + " failed: " + optParam(event, "failure"));
            }
        } else if (type == JmcRuntimeEvent.Type.ASSUME_EVENT) {
            // assume(false) halts the task with a HaltTaskException after this event; for main
            // that ends the iteration without a FINISH_EVENT, so the abandoned-process check
            // must run here.
            if (task == 1L && Boolean.FALSE.equals(event.getParam("result"))) {
                requireNoLiveProcess("halted on assume(false)");
            }
        } else if (type == JmcRuntimeEvent.Type.JOIN_COMPLETE_EVENT) {
            Long joined = event.getParam("joinedTask");
            if (joined != null) {
                clocks = clocks.join(task, joined);
            }
        } else if (type == JmcRuntimeEvent.Type.MP_SEND_EVENT) {
            CommunicationModel model = event.getParam("model");
            Tid dst = event.getParam("dst");
            MpMessage value = event.getParam("value");
            long index = sendCount.merge(task, 1L, Long::sum) - 1;
            Envelope envelope = new Envelope(Tid.of(task), index, dst, model, value);
            TaskClocks.Emission emission = clocks.emit(task);
            clocks = emission.clocks();
            buffers = buffers.emit(envelope, emission.stamp());
            LOGGER.debug("Task {} emitted {}", task, envelope);
            retryParked(dst.raw());
        } else if (type == JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT) {
            if (optParam(event, "awaitOfBlocked") != null) {
                awaitBlocked.add(task);
            }
            if (task == 1L && optParam(event, "drainDaemons") != null) {
                mainDraining = true;
            }
            CommunicationModel model = event.getParam("model");
            Predicate<MpMessage> guard = event.getParam("guard");
            Boolean blocking = event.getParam("blocking");
            if (!resolve(task, model, guard, blocking)) {
                parked.put(task, new ParkedReceive(model, guard));
                LOGGER.debug("Task {} parked: nothing deliverable under {}", task, model);
            }
        } else if (type == JmcRuntimeEvent.Type.ND_EVENT) {
            Integer optionCount = event.getParam("optionCount");
            int chosen = random.nextInt(optionCount);
            answers.put(task, new PrimitiveValue(chosen));
            LOGGER.debug("Task {} nondet over {} options: chose {}", task, optionCount, chosen);
        }
    }

    /**
     * Resolves a receive request: samples among the deliverable messages (and {@code ⊥} for a
     * non-blocking receive) and records the answer for {@code task}.
     *
     * @return whether an answer was recorded; {@code false} for a blocking receive with nothing to
     *     read, which the caller keeps (or puts) in {@link #parked}
     */
    private boolean resolve(long task, CommunicationModel model, Predicate<MpMessage> guard, boolean blocking) {
        List<Pending> candidates = evaluateGuard(task, model, guard);
        int options = candidates.size() + (blocking ? 0 : 1);
        if (options == 0) {
            return false;
        }
        int k = options == 1 ? 0 : random.nextInt(options);
        if (k == candidates.size()) {
            answers.put(task, MpDeliveryValue.bottom());
            LOGGER.debug("Task {} non-blocking receive reads ⊥ ({} pending candidates)", task, candidates.size());
            return true;
        }
        Pending chosen = candidates.get(k);
        Envelope e = chosen.envelope();
        buffers = buffers.remove(e);
        VectorClock stamp = chosen.clock();
        clocks = clocks.deliver(task, stamp);
        answers.put(task, MpDeliveryValue.of(e.src(), e.sendIndex(), e.value()));
        LOGGER.debug("Task {} reads {} (of {} candidates)", task, e, candidates.size());
        return true;
    }

    /** {@link Delivery#deliverable} under the re-entrancy watch described in the class doc. */
    private List<Pending> evaluateGuard(long task, CommunicationModel model, Predicate<MpMessage> guard) {
        resolving = true;
        try {
            return Delivery.deliverable(buffers, model, Tid.of(task), guard);
        } finally {
            resolving = false;
        }
    }

    /**
     * After a send to {@code dst}: if a blocking receive of {@code dst} is parked, try it again.
     * The receive leaves {@link #parked} only once it has an answer.
     */
    private void retryParked(long dst) {
        ParkedReceive pr = parked.get(dst);
        if (pr != null && resolve(dst, pr.model(), pr.guard(), true)) {
            parked.remove(dst);
        }
    }

    /**
     * Records {@code message} as this iteration's fault and throws it (see the class doc).
     *
     * @param message the fault
     * @param onMain whether the throw itself reaches main (the call is on main's thread or on the
     *     checker's, for main's {@code FINISH_EVENT}), so that it counts as the single-shot
     *     delivery
     */
    private void fail(String message, boolean onMain) {
        HaltExecutionException f = HaltExecutionException.error(message);
        if (fault == null) {
            fault = f;
            delivered = onMain;
        }
        LOGGER.error("mp-random fault: {}", message);
        throw f;
    }

    /**
     * Records {@code message} as this iteration's fault without throwing: the caller is a process's
     * thread, whose {@code FINISH_EVENT} must complete; main receives the fault on its next event.
     *
     * @param message the fault
     */
    private void latch(String message) {
        if (fault == null) {
            fault = HaltExecutionException.error(message);
            delivered = false;
        }
        LOGGER.error("mp-random fault: {}", message);
    }

    /** A parameter that an event may lack entirely (events built without parameters have none). */
    private static Object optParam(JmcRuntimeEvent event, String key) {
        Map<String, Object> ps = event.getParams();
        return ps == null ? null : ps.get(key);
    }

    /**
     * The abandoned-process rule (see the class doc): main may not end the iteration while a
     * spawned process is still live.
     *
     * @param how what main did, for the message
     */
    private void requireNoLiveProcess(String how) {
        if (!liveForRule().isEmpty()) {
            fail("main " + how + " while process(es) " + live + " were still live"
                    + (parked.isEmpty() ? "" : " (parked: " + parked.keySet() + ")")
                    + "; every spawned process must be awaited (or have terminated) before main"
                    + " returns — JMC does not wait for spawned tasks", true);
        }
    }

    /**
     * The live processes the abandoned-process rule counts: daemons are exempt once main's final
     * request has been answered.
     */
    private Set<Long> liveForRule() {
        if (!drained) {
            return live;
        }
        Set<Long> l = new TreeSet<>(live);
        l.removeAll(daemons);
        return l;
    }

    /**
     * The spawned processes that have started and not finished — exposed for tests.
     *
     * @return the live task ids, main excluded
     */
    public Set<Long> liveProcesses() {
        return Set.copyOf(live);
    }

    /** Whether the checker has recorded an error for {@code iteration} on the report. */
    private boolean errorRecordedFor(int iteration) {
        return report != null && report.getErrorIteration() != null
                && report.getErrorIteration() == iteration;
    }

    /** Marks the latched fault as handed to main once, and returns it. */
    private HaltExecutionException deliverFault() {
        delivered = true;
        return fault;
    }

    /**
     * Picks the next task among the active tasks that are not parked on a receive, attaching the
     * pending message-passing answer if it has one. If only parked tasks remain, blocks the parked
     * task with the highest id — main last — and counts the iteration as blocked.
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        Set<Long> runnable = new TreeSet<>(getActiveTasks());
        runnable.removeAll(parked.keySet());
        if (runnable.isEmpty()) {
            if (parked.isEmpty()) {
                return null;
            }
            long blocked = parked.lastKey();
            parked.remove(blocked);
            if (blocked == 1L && fault != null && !delivered) {
                // Main is parked and a fault is latched: resume main with the fault instead of
                // blocking it, so the run fails on main's thread rather than ending as a
                // "blocked" iteration that the checker never hears about.
                LOGGER.debug("Main parked with a latched fault; delivering the fault");
                return SchedulingChoice.task(1L, new MpFaultValue(deliverFault().getMessage()));
            }
            if (blocked == 1L && mainDraining) {
                // Main's final request after its body: every other task has finished or
                // blocked, so it is answered, not blocked.
                mainDraining = false;
                drained = true;
                return SchedulingChoice.task(1L, new PrimitiveValue(0));
            }
            boolean daemonOnReceive = daemons.contains(blocked) && !awaitBlocked.contains(blocked);
            if (!iterationBlocked && fault == null && !daemonOnReceive) {
                // A daemon blocked on a receive does not make the iteration blocked.
                iterationBlocked = true;
                blockedIterations++;
                if (report != null) {
                    report.setBlockedIterations(blockedIterations);
                }
            }
            LOGGER.debug("No runnable task; blocking parked receive of task {}", blocked);
            return SchedulingChoice.blockTask(blocked);
        }
        List<Long> order = new ArrayList<>(runnable);
        long task = order.size() == 1 ? order.get(0) : order.get(random.nextInt(order.size()));
        SchedulingChoiceValue answer = answers.remove(task);
        if (answer != null) {
            SchedulingChoice<?> choice = SchedulingChoice.task(task, answer);
            curTrace.addChoice(choice);
            return choice;
        }
        return makeSchedulingChoice(task);
    }

    @Override
    public void resetIteration(int iteration) {
        // First thing, before the runtime wakes the tasks still paused: starts a new epoch in
        // MpThread, so that late events of tasks left over from the previous iteration are ignored.
        MpThread.newEpoch();
        super.resetIteration(iteration);
        buffers = Buffers.withClocks();
        clocks = TaskClocks.EMPTY;
        sendCount.clear();
        answers.clear();
        if (!parked.isEmpty() && fault == null) {
            // Defensive: the FINISH_EVENT check in updateEvent refuses this shape before it gets
            // here.
            LOGGER.warn("Iteration {} ended with {} receive(s) still parked: {}", iteration,
                    parked.size(), parked.keySet());
        }
        parked.clear();
        resolving = false;
        boolean recorded = errorRecordedFor(iteration);
        if (fault == null && !liveForRule().isEmpty() && !recorded) {
            // Main ended the iteration by a path this strategy could not intercept (a
            // HaltTaskException from a source other than a receive or assume) while spawned
            // processes were still live: the abandoned-process hang. Refuse it here rather than
            // let the reset wake the leaked tasks into the next iteration. Skipped when the
            // checker has already recorded an error for this iteration, so a real failure is
            // never masked by this one.
            fault = HaltExecutionException.error("main ended (blocked or halted) while process(es) "
                    + live + " were still live and could not be unwound; every spawned process must"
                    + " be awaited or have terminated before main ends");
            if (iterationBlocked) {
                // The block that ended main was not a blocked execution in the Must sense but
                // this deadlock; do not report the failed iteration as blocked.
                blockedIterations--;
                if (report != null) {
                    report.setBlockedIterations(blockedIterations);
                }
            }
        }
        live.clear();
        daemons.clear();
        awaitBlocked.clear();
        mainDraining = false;
        drained = false;
        HaltExecutionException f = fault;
        fault = null;
        delivered = false;
        if (f != null && !recorded) {
            // Last resort: the checker did not record this iteration's fault — main halted without
            // an event of its own, or main caught the delivered exception and carried on. Throwing
            // here makes the checker report it; JmcRuntime.resetIteration then skips its own
            // taskManager.reset() and clearSyncLocks() — the tear-down resets the task manager,
            // and the sync locks stay stale only for a later check in the same JVM.
            throw f;
        }
    }

    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        report.setBlockedIterations(blockedIterations);
    }

    /**
     * The number of iterations of this check so far in which some receive was blocked.
     *
     * @return the blocked-iteration count, as written to the report
     */
    public int blockedIterations() {
        return blockedIterations;
    }

    /**
     * The pending messages — exposed for tests and diagnostics; never for the API.
     *
     * @return the current buffers
     */
    public Buffers buffers() {
        return buffers;
    }

    /**
     * The task clocks — exposed for tests and diagnostics.
     *
     * @return the current clocks
     */
    public TaskClocks clocks() {
        return clocks;
    }
}
