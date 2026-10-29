package org.mpi_sws.jmc.strategies.mp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphEvent;
import org.mpi_sws.jmc.mp.summary.MpValueEncoder;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * {@code mp-random} plus a record of each iteration as an execution graph and its compact summary.
 * The summary format is the one shared with the TraceForge tool; see
 * {@code org.mpi_sws.jmc.mp.summary}, whose terms ("the format", visible roles) are used below.
 *
 * <p><b>How the graph is built, without touching the base class.</b> The events arrive through
 * {@link #updateEvent}: a send is added at once ({@link MpLabel.Send}, its {@code sendIndex}
 * counted per task exactly as the base counts it); a receive request and a nondet request are
 * remembered as <em>pending</em> with their program-order position. The <em>answers</em> are read
 * off the choices the base class returns from {@link #nextTask()}: an {@link MpDeliveryValue} for
 * a task with a pending receive closes it as a {@link MpLabel.Recv} reading the send
 * {@code (src, sendIndex)} names, or ⊥; a {@link PrimitiveValue} for a task with a pending nondet
 * closes it as a {@link MpLabel.Nondet}; a {@link SchedulingChoice#blockTask} closes a pending
 * receive as a {@link MpLabel.Block} (the format's rule for a receive still parked when the
 * strategy blocks it). A task has at most one open request, so the pairing is exact. Main's
 * events are recorded, but main carries no role and is never visible.
 *
 * <p><b>Spawn and join edges.</b> Thread-creation and join edges order events causally (the
 * in-porf order of TraceForge), so the recorder adds them as the graph's {@code hb} edges by
 * {@link ExecutionGraph}'s rule: on a child's {@code START_EVENT}, from the parent's causal
 * frontier (its last event, if it has one, and the last events of the tasks it joined since;
 * these stay pending while the parent has no event of its own yet) to the child's first event;
 * on a {@code JOIN_COMPLETE_EVENT}, from the joined task's last event (if any) to the joiner's
 * next event. An edge is kept only when both endpoints come to exist. For a program in the
 * format's shape (main spawns first, awaits all) no such edge orders two visible events; with
 * main sending after an await they do, on both sides alike.
 *
 * <p><b>The format's shape is enforced.</b> Main performs no send or receive at all, spawns every
 * process before its first await, awaits every process it spawned (a process not awaited could
 * hide a failure, since only {@link org.mpi_sws.jmc.api.mp.MpThread#join1()} rethrows it), and no
 * spawned process spawns or awaits another. A program that breaks this is refused: the iteration
 * is not exported and the run fails from {@link #resetIteration} with a message naming the rule.
 *
 * <p><b>Roles.</b> A process's role is the name it is spawned with,
 * {@code MpProcess.spawn(role, body)}, carried on its {@code TASK_CREATED_EVENT}; nothing depends
 * on creation or start order. The declared list is an unordered check: a process with no role,
 * with an undeclared role, or with a role already spawned, and a declared role never spawned, are
 * each refused with a message. Daemons have no role. {@code "main"} and names starting with
 * {@code "~"} (internal aliases) cannot be declared.
 *
 * <p><b>What is exported.</b> At {@link #resetIteration}, after the base class has reset (and if
 * it threw its latched fault, nothing is exported and the fault propagates), an iteration is
 * summarised and counted unless the checker's report records an error for it, the base class
 * faulted, or this class found the record inconsistent (an open request at the end, an unknown
 * send read, an undeclared process). An inconsistency is raised from {@code resetIteration} as a
 * {@link HaltExecutionException} so the run fails loudly. Summaries accumulate across iterations
 * with their multiplicity ({@link #summaries()}); writing the file is the launcher's job, which
 * knows the run's outcome for the header.
 *
 * <p>Every scheduling and delivery choice is still the base class's; this class only observes.
 */
public class MpRecordingStrategy extends MpRandomSchedulingStrategy {

    private static final Logger LOGGER = LogManager.getLogger(MpRecordingStrategy.class);

    /** A receive request waiting for its answer. */
    private record PendingRecv(EventKey key, CommunicationModel model, Predicate<MpMessage> guard, boolean blocking) {}

    /** A nondet request waiting for its answer. */
    private record PendingNd(EventKey key, int optionCount) {}

    /** The identity of a send as the delivery names it. */
    private record SendId(long src, long sendIndex) {}

    private final List<String> rolesInSpawnOrder;
    private final List<String> visibleRoles;

    // per iteration
    private final Map<Long, Integer> nextIdx = new HashMap<>();
    private final Map<Long, Long> sendCount = new HashMap<>();
    private final Map<Long, PendingRecv> pendingRecv = new HashMap<>();
    private final Map<Long, PendingNd> pendingNd = new HashMap<>();
    private final Map<SendId, EventKey> sendKeys = new HashMap<>();
    private final List<GraphEvent> events = new ArrayList<>();
    private final Map<EventKey, EventKey> rf = new HashMap<>();
    private final Set<EventKey> bottoms = new HashSet<>();
    private final Set<Long> started = new TreeSet<>();
    /** Per task, the key of its last event so far (requests included). */
    private final Map<Long, EventKey> lastKey = new HashMap<>();
    /** Per task, the events the task's next event must be hb-after (spawn/join sources). */
    private final Map<Long, List<EventKey>> hbInto = new HashMap<>();
    private final List<ExecutionGraph.Edge> hb = new ArrayList<>();
    /** Whether main has completed an await in this iteration (no spawn may follow). */
    private boolean mainAwaited;
    /** The processes main has awaited (completed joins) in this iteration. */
    private final Set<Long> awaited = new HashSet<>();
    /** Whether main raised an await of a process that ended blocked (such an await blocks). */
    private boolean mainBlockedOnAwait;
    /** Daemon processes (no role, never awaited). */
    private final Set<Long> daemons = new HashSet<>();
    /** Each process's role, from its creation event; the empty string if it has none. */
    private final Map<Long, String> roleOf = new HashMap<>();
    /** The first reason this iteration's record is unusable, or {@code null} if consistent. */
    private String inconsistency;
    /** The report of the current check, used to see whether the checker recorded an error. */
    private JmcModelCheckerReport report;

    // across iterations
    private final LinkedHashMap<Summary, Integer> summaries = new LinkedHashMap<>();
    private int exportedIterations;
    private int skippedIterations;
    private ExecutionGraph lastGraph;

    /**
     * Creates the recording strategy.
     *
     * @param seed the random seed (the base class's)
     * @param reportPath where the base class writes its traces
     * @param rolesInSpawnOrder the declared roles, an unordered check (see the class doc)
     * @param visibleRoles the roles whose events are summarised; a subset of the former
     */
    public MpRecordingStrategy(
            Long seed, String reportPath, List<String> rolesInSpawnOrder, List<String> visibleRoles) {
        super(seed, reportPath);
        this.rolesInSpawnOrder = List.copyOf(rolesInSpawnOrder);
        this.visibleRoles = List.copyOf(visibleRoles);
        if (!this.rolesInSpawnOrder.containsAll(this.visibleRoles)) {
            throw new IllegalArgumentException(
                    "visible roles " + visibleRoles + " are not all spawned roles " + rolesInSpawnOrder);
        }
        if (new HashSet<>(this.rolesInSpawnOrder).size() != this.rolesInSpawnOrder.size()) {
            throw new IllegalArgumentException("roles are not distinct: " + rolesInSpawnOrder);
        }
        for (String r : this.rolesInSpawnOrder) {
            MustStrategy.checkDeclaredRole(r);
        }
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) throws HaltExecutionException {
        super.initIteration(iteration, report);
        this.report = report;
        clearIteration();
    }

    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        super.updateEvent(event);
        long task = event.getTaskId();
        switch (event.getType()) {
            case TASK_CREATED_EVENT ->
            {
                // A daemon has no role; a process's role is the name it was spawned with, not
                // its position.
                if (optParam(event, "createdTask") instanceof Long c) {
                    if (Boolean.TRUE.equals(optParam(event, "daemon"))) {
                        daemons.add(c);
                    }
                    roleOf.put(c, optParam(event, "role") instanceof String r ? r : "");
                }
            }
            case START_EVENT ->
            {
                started.add(task);
                if (task != 1L && !daemons.contains(task)) {
                    String role = roleOf.getOrDefault(task, "");
                    if (role.isEmpty()) {
                        inconsistent("process " + task + " has no role; an exported program spawns every"
                                + " process with MpProcess.spawn(role, body)");
                    } else if (!rolesInSpawnOrder.contains(role)) {
                        inconsistent("process " + task + " has role " + role + ", which is not declared "
                                + rolesInSpawnOrder);
                    } else if (started.stream().anyMatch(t -> t != task && role.equals(roleOf.get(t))
                            && !daemons.contains(t))) {
                        inconsistent("role " + role + " is spawned more than once");
                    }
                }
                Long parent = event.getParam("startedBy");
                if (task != 1L && parent != null && parent != 1L) {
                    inconsistent("process " + task + " was spawned by process " + parent
                            + "; the format allows spawning by main only");
                }
                if (task != 1L && mainAwaited) {
                    inconsistent("process " + task + " was spawned after main's first await; the"
                            + " format requires main to spawn every process first");
                }
                if (task != 1L && parent != null) {
                    // The child's first event is hb-after the parent's causal frontier at the
                    // spawn: the parent's last event, and every join the parent completed since
                    // (still pending on the parent, which may have no event of its own yet);
                    // this is ExecutionGraph's rule for a parent without an event.
                    List<EventKey> frontier = new ArrayList<>();
                    if (lastKey.containsKey(parent)) {
                        frontier.add(lastKey.get(parent));
                    }
                    frontier.addAll(hbInto.getOrDefault(parent, List.of()));
                    if (!frontier.isEmpty()) {
                        hbInto.computeIfAbsent(task, t -> new ArrayList<>()).addAll(frontier);
                    }
                }
            }
            case JOIN_REQUEST_EVENT ->
            {
                if (task != 1L) {
                    inconsistent("process " + task + " awaits another process; the format allows"
                            + " awaiting by main only");
                }
            }
            case JOIN_COMPLETE_EVENT ->
            {
                Long joined = event.getParam("joinedTask");
                if (task == 1L) {
                    mainAwaited = true;
                    if (joined != null) {
                        awaited.add(joined);
                    }
                }
                if (joined != null && lastKey.containsKey(joined)) {
                    hbInto.computeIfAbsent(task, t -> new ArrayList<>()).add(lastKey.get(joined));
                }
            }
            case MP_SEND_EVENT ->
            {
                if (task == 1L) {
                    inconsistent("main performs a send; the format requires main to perform no"
                            + " message-passing event");
                }
                CommunicationModel model = event.getParam("model");
                Tid dst = event.getParam("dst");
                MpMessage value = event.getParam("value");
                long index = sendCount.merge(task, 1L, Long::sum) - 1;
                EventKey key = nextKey(task);
                events.add(GraphEvent.of(key, new MpLabel.Send(model, dst, value)));
                sendKeys.put(new SendId(task, index), key);
            }
            case MP_RECV_REQUEST_EVENT ->
            {
                if (task == 1L && optParam(event, "drainDaemons") != null) {
                    // Main's final request (MpProcess.runMain); not a message event, no key.
                    return;
                }
                if (task == 1L && optParam(event, "awaitOfBlocked") != null) {
                    // MpThread.join1 awaiting a process that ended blocked; not a message event
                    // of main. Awaiting a blocked process blocks the caller, and the resulting
                    // Block closes this request like any blocked receive.
                    mainBlockedOnAwait = true;
                } else if (task == 1L) {
                    inconsistent("main performs a receive; the format requires main to perform no"
                            + " message-passing event");
                }
                if (pendingRecv.containsKey(task) || pendingNd.containsKey(task)) {
                    inconsistent("task " + task + " requested a receive with a request still open");
                }
                CommunicationModel model = event.getParam("model");
                Predicate<MpMessage> guard = event.getParam("guard");
                Boolean blocking = event.getParam("blocking");
                pendingRecv.put(task, new PendingRecv(nextKey(task), model, guard, blocking));
            }
            case ND_EVENT ->
            {
                if (pendingRecv.containsKey(task) || pendingNd.containsKey(task)) {
                    inconsistent("task " + task + " requested a nondet with a request still open");
                }
                Integer optionCount = event.getParam("optionCount");
                pendingNd.put(task, new PendingNd(nextKey(task), optionCount));
            }
            default ->
            {
            }
        }
    }

    /**
     * Delegates to the base class and closes the open request of the chosen task, if the choice
     * answers one (see the class doc). The choice is returned unchanged.
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        SchedulingChoice<?> choice = super.nextTask();
        if (choice == null) {
            return null;
        }
        Long task = choice.getTaskId();
        if (task == null) {
            return choice;
        }
        if (choice.isBlockTask()) {
            PendingRecv p = pendingRecv.remove(task);
            if (p == null) {
                inconsistent("task " + task + " blocked without an open receive");
            } else {
                events.add(GraphEvent.of(p.key(), new MpLabel.Block()));
            }
            return choice;
        }
        Object value = choice.getValue();
        if (value instanceof MpDeliveryValue d) {
            PendingRecv p = pendingRecv.remove(task);
            if (p == null) {
                inconsistent("task " + task + " was answered " + d + " without an open receive");
                return choice;
            }
            events.add(GraphEvent.receive(p.key(), new MpLabel.Recv(p.model(), p.blocking()), p.guard()));
            if (d.isBottom()) {
                bottoms.add(p.key());
            } else {
                EventKey send = sendKeys.get(new SendId(d.src().raw(), d.sendIndex()));
                if (send == null) {
                    inconsistent("task " + task + " reads " + d + ", a send this record does not know");
                } else {
                    rf.put(p.key(), send);
                }
            }
        } else if (value instanceof PrimitiveValue pv && pendingNd.containsKey(task)) {
            PendingNd p = pendingNd.remove(task);
            events.add(GraphEvent.of(p.key(), new MpLabel.Nondet(p.optionCount(), pv.asInteger())));
        } else if (value instanceof MpFaultValue f) {
            inconsistent("fault delivered to task " + task + ": " + f.message());
        }
        return choice;
    }

    /**
     * Lets the base class reset, then exports the iteration unless it must be skipped (an error
     * recorded by the checker, a fault from the base class, or an inconsistent record). The
     * per-iteration state is cleared in every case.
     *
     * @param iteration the iteration that ended
     * @throws HaltExecutionException if the record was inconsistent and the checker has recorded
     *     no error of its own for this iteration; a failure thrown by the base class is rethrown
     */
    @Override
    public void resetIteration(int iteration) {
        boolean errorRecorded = report != null && report.getErrorIteration() != null
                && report.getErrorIteration() == iteration;
        RuntimeException fromBase = null;
        try {
            super.resetIteration(iteration);
        } catch (RuntimeException e) {
            fromBase = e;
        }
        String problem = inconsistency;
        try {
            if (fromBase == null && !errorRecorded && problem == null) {
                Set<Long> notAwaited = new TreeSet<>(started);
                notAwaited.remove(1L);
                notAwaited.removeAll(awaited);
                notAwaited.removeAll(daemons);
                if (!pendingRecv.isEmpty() || !pendingNd.isEmpty()) {
                    problem = "iteration ended with open requests of tasks "
                            + pendingRecv.keySet() + " " + pendingNd.keySet();
                } else if (!notAwaited.isEmpty() && !mainBlockedOnAwait) {
                    // The format requires main to await every process; a process that was not
                    // awaited may have failed without anyone rethrowing its failure
                    // (MpThread.join1 does), so the iteration is not trusted.
                    problem = "process(es) " + notAwaited + " were never awaited by main; the"
                            + " format requires main to await every process";
                } else {
                    try {
                        record();
                    } catch (IllegalArgumentException e) {
                        problem = e.getMessage();
                    }
                }
            }
            if (fromBase != null || errorRecorded || problem != null) {
                skippedIterations++;
                LOGGER.debug("Iteration {} not exported: {}", iteration,
                        fromBase != null ? fromBase.getMessage() : errorRecorded ? "error recorded" : problem);
            }
        } finally {
            clearIteration();
        }
        if (fromBase != null) {
            throw fromBase;
        }
        if (problem != null && !errorRecorded) {
            throw HaltExecutionException.error("mp-recording: " + problem);
        }
    }

    /**
     * Builds the execution graph of the finished iteration, checks the declared roles, summarises
     * it and adds it to {@link #summaries}.
     *
     * @throws IllegalArgumentException if a declared role was never spawned, or a message payload
     *     names a daemon or a task that never started
     */
    private void record() {
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        for (GraphEvent e : events) {
            b.add(e);
        }
        rf.forEach(b::readsFrom);
        bottoms.forEach(b::readsBottom);
        for (ExecutionGraph.Edge e : hb) {
            b.hb(e.from(), e.to());
        }
        ExecutionGraph g = b.build();
        Map<Long, String> roleOfTask = new HashMap<>();
        for (long t : started) {
            // A daemon gets an internal name outside every declared role; it is never encoded.
            roleOfTask.put(t, t == 1L ? "main" : daemons.contains(t) ? "~daemon " + t : roleOf.get(t));
        }
        // Checked against explicitly named non-daemon processes only.
        Set<String> spawnedRoles = new HashSet<>();
        for (long t : started) {
            if (t != 1L && !daemons.contains(t)) {
                spawnedRoles.add(roleOf.get(t));
            }
        }
        for (String r : rolesInSpawnOrder) {
            if (!spawnedRoles.contains(r)) {
                throw new IllegalArgumentException("declared role " + r + " was never spawned");
            }
        }
        MpValueEncoder encoder = new MpValueEncoder(tid -> {
            if (daemons.contains(tid.raw())) {
                throw new IllegalArgumentException("payload names daemon " + tid
                        + "; a thread id is encoded as a role name and a daemon has none");
            }
            String r = roleOfTask.get(tid.raw());
            if (r == null) {
                throw new IllegalArgumentException("payload names task " + tid + ", which never started");
            }
            return r;
        });
        Summary s = SummaryExporter.summarize(g, roleOfTask, visibleRoles, encoder);
        summaries.merge(s, 1, Integer::sum);
        exportedIterations++;
        lastGraph = g;
    }

    /**
     * Allocates the key of {@code task}'s next event (its program-order position) and turns the
     * pending spawn/join sources of that task into {@code hb} edges ending at the new event.
     */
    private EventKey nextKey(long task) {
        int idx = nextIdx.merge(task, 1, Integer::sum) - 1;
        EventKey key = new EventKey(task, idx);
        lastKey.put(task, key);
        List<EventKey> sources = hbInto.remove(task);
        if (sources != null) {
            for (EventKey from : sources) {
                hb.add(new ExecutionGraph.Edge(from, key));
            }
        }
        return key;
    }

    /** Records the first reason this iteration cannot be exported; later ones are only logged. */
    private void inconsistent(String why) {
        if (inconsistency == null) {
            inconsistency = why;
        }
        LOGGER.error("mp-recording: {}", why);
    }

    /** Clears all per-iteration state; the cross-iteration summaries are kept. */
    private void clearIteration() {
        nextIdx.clear();
        sendCount.clear();
        pendingRecv.clear();
        pendingNd.clear();
        sendKeys.clear();
        events.clear();
        rf.clear();
        bottoms.clear();
        started.clear();
        lastKey.clear();
        hbInto.clear();
        hb.clear();
        mainAwaited = false;
        awaited.clear();
        mainBlockedOnAwait = false;
        daemons.clear();
        roleOf.clear();
        inconsistency = null;
    }

    /**
     * The summaries recorded so far with their multiplicities, in first-seen order.
     *
     * @return an unmodifiable view
     */
    public Map<Summary, Integer> summaries() {
        return Collections.unmodifiableMap(summaries);
    }

    /**
     * Iterations that were summarised.
     *
     * @return the count
     */
    public int exportedIterations() {
        return exportedIterations;
    }

    /**
     * Iterations that were not summarised (an error, a fault or an inconsistent record).
     *
     * @return the count
     */
    public int skippedIterations() {
        return skippedIterations;
    }

    /**
     * The graph of the last iteration that was summarised — for tests.
     *
     * @return the graph, or {@code null} before the first export
     */
    public ExecutionGraph lastGraph() {
        return lastGraph;
    }

    /**
     * The visible roles, as given.
     *
     * @return the roles
     */
    public List<String> visibleRoles() {
        return visibleRoles;
    }

    /** A parameter that an event may lack entirely (events built without parameters have none). */
    private static Object optParam(JmcRuntimeEvent event, String key) {
        Map<String, Object> ps = event.getParams();
        return ps == null ? null : ps.get(key);
    }
}
