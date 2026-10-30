package org.mpi_sws.jmc.strategies.must;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.consistency.MpConsistency;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.mp.summary.MpValueEncoder;
import org.mpi_sws.jmc.mp.summary.SetIndex;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValue;
import org.mpi_sws.jmc.strategies.tracker.TrackActiveTasksStrategy;
import org.mpi_sws.jmc.strategies.tracker.TrackTasks;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The Must strategy ({@code "must"}): Algorithm 1 of the Must paper (optimal DPOR for
 * message-passing programs) running over JMC's runtime. It is structured like
 * {@code TrustStrategy}, and its decisions follow the Rust implementation in TraceForge
 * ({@code must.rs}). "Line n" below refers to line n of Algorithm 1.
 *
 * <p><b>One iteration = one graph.</b> {@link #initIteration} pops the exploration stack: a
 * {@link Forward} item re-points a receive to an alternative send or ⊥ (line 7), a
 * {@link Nondet} item sets a choice to another value (line 6), a {@link Backward} item is the
 * restricted graph of a backward revisit (line 13); the result is checked with
 * {@link MpConsistency#check} (line 16; an inconsistent graph is dropped) and becomes the
 * <em>guide</em>. An empty stack ends the run ({@link HaltCheckerException#ok()}).
 *
 * <p>Tasks are identified inside the graph by a <em>stable id</em> derived from the task's creation
 * path (the creator's name plus the index of the creation), so that ids agree across iterations
 * although the runtime numbers tasks afresh each time; main is task 1.
 *
 * <p><b>Replay.</b> Each task has a position in the guide; {@link #updateEvent} matches every
 * raised event against the task's next guide node (a mismatch is a nondeterministic program) and
 * answers it from the guide: a receive reading a send is answered once that send has been raised
 * in this iteration (until then the task is parked), a ⊥ receive at once, a nondet with its index.
 * Replayed nodes keep their stamps. A task past its guide runs free and <em>extends</em> the graph;
 * only free events generate alternatives.
 *
 * <p><b>Free events.</b> A send is appended and its backward revisits computed (lines 10–13 with
 * {@code RevisitCondition}, lines 17–22; a receive is maximal when it reads the least of its
 * options by {@code (tid, index)}; a nondet is maximal when it chose index 0). A receive gets its
 * options — the unread sends to it under its model that its guard accepts, so-minimal (p2p: the
 * earliest per sender; asyn: all), by {@code (tid, index)} — plus ⊥ if non-blocking; a
 * non-blocking receive explores ⊥ first and pushes the sends (as {@code visit_rfs} in
 * {@code must.rs} does); a blocking one explores the least send and pushes the rest; a blocking
 * receive with no option is parked. A nondet explores index 0 and pushes the rest. When nothing
 * is runnable, a parked receive with options now is re-issued as a fresh receive; otherwise every
 * parked task gets a {@link MpLabel.Block} and is blocked (descending id), and the graph is a
 * blocked complete graph.
 *
 * <p><b>Scheduling.</b> Lowest-id runnable, unparked task first (FIFO). The explored set does not
 * depend on it; the seed is accepted for the factory's signature and unused.
 *
 * <p><b>At the end of an iteration</b> without a recorded error, the graph's {@code GraphCanon} is
 * counted (complete and blocked separately), and — when roles were given — summarised with
 * {@link SummaryExporter}: each process is given the role it was spawned with, which must be one
 * of the declared roles, and the program must have the shape described in the package
 * documentation. {@link #exhausted()} says whether the stack ran empty, i.e. the enumeration is
 * complete.
 *
 * <p><b>Online mode</b>: after {@link #checkAgainst}, every summary is also queried against the
 * specification's {@link SetIndex} ({@code covers}: a held summary of the same signature with
 * counts pointwise {@code ≤} the query's, as in TraceForge's stateful engine with its Set index).
 * The first uncovered summary is kept ({@link #uncovered()}) and the exploration stops at the next
 * {@link #initIteration}, as TraceForge's {@code stop_at_first_report} does; {@link #exhausted()}
 * is then true only if the stack was already empty. Blocked graphs are queried too: they are
 * completed executions on both sides.
 */
public class MustStrategy extends TrackActiveTasksStrategy {

    private static final Logger LOGGER = LogManager.getLogger(MustStrategy.class);

    /** A stack item: a snapshot and the change to apply on pop. */
    private sealed interface Item permits Forward, Nondet, Backward {}

    /**
     * Line 7: re-issue the receive of {@code task} on {@code g} (the snapshot before it) reading
     * {@code send} ({@code null} = ⊥); {@code hbIn} are the receive's spawn/join predecessors.
     */
    private record Forward(
            Graph g, long task, MpLabel.Recv label, Predicate<MpMessage> guard, EventKey send, Set<EventKey> hbIn)
            implements Item {}

    /**
     * Line 6: the nondet of {@code task} on {@code g} (the snapshot before it), with
     * {@code optionCount} options, taking the value {@code chosen}.
     */
    private record Nondet(Graph g, long task, int optionCount, int chosen, Set<EventKey> hbIn) implements Item {}

    /** Line 13: the restricted graph with the revisit applied. */
    private record Backward(Graph g) implements Item {}

    /**
     * A parked receive (or a request parked like one). {@code waitingFor} names the send it reads
     * once the program re-raises it in this iteration (TraceForge's "stuck" state: the dependency
     * is in the graph but has not executed yet); {@code null} means a free blocking receive with
     * nothing deliverable (a blocked receive in the paper's sense, re-issued when nothing else
     * runs). {@code guided} says whether answering advances the task's guide position (a guided
     * receive's node was not yet counted as produced).
     */
    private record Parked(
            CommunicationModel model,
            Predicate<MpMessage> guard,
            boolean blocking,
            EventKey waitingFor,
            boolean guided) {}

    /** The exploration stack of alternatives still to explore (the top is the head). */
    private final Deque<Item> stack = new ArrayDeque<>();
    /** The current iteration's graph: the guide while replaying, then extended by free events. */
    private Graph graph = new Graph();
    /** Whether the stack ran empty, i.e. the enumeration is complete. */
    private boolean exhausted;
    /** The specification's index, queried at each completed graph; {@code null} for none. */
    private SetIndex spec;
    /** The first summary the specification does not cover, or {@code null}. */
    private Summary uncovered;
    /** The iteration (1-based) whose graph gave {@link #uncovered}; 0 for none. */
    private int uncoveredIteration;
    /** The index's answer for {@link #uncovered}. */
    private SetIndex.Covers uncoveredAnswer;
    /** Set by the first uncovered summary: the next {@link #initIteration} stops the run. */
    private boolean stopRequested;
    /** Online-mode counters: queries, and their answers (no signature / found / not found). */
    private long lookups;
    private long signatureMisses;
    private long found;
    private long notFound;

    // per iteration
    /** The number of guide events of each task (stable id), i.e. the size of its replay. */
    private final Map<Long, Integer> guideLen = new HashMap<>();
    /** How many events of each task have been produced (or replayed) in this iteration. */
    private final Map<Long, Integer> pos = new HashMap<>();
    /** The actual message of each send raised in this iteration (before normalisation). */
    private final Map<EventKey, MpMessage> currentValue = new HashMap<>();
    /** The answer to hand to a task (stable id) when it is next scheduled. */
    private final Map<Long, SchedulingChoiceValue> answers = new HashMap<>();
    /** Tasks (stable id) waiting for an answer that cannot be given yet. */
    private final TreeMap<Long, Parked> parked = new TreeMap<>();
    /** The tasks that raised their start event. */
    private final Set<Long> started = new TreeSet<>();
    /** The tasks main has awaited. */
    private final Set<Long> awaited = new HashSet<>();
    /** Whether main has awaited a process (after which no process may be spawned). */
    private boolean mainAwaited;
    /** Whether a Block that counts as blocking has already been counted in this iteration. */
    private boolean iterationBlocked;
    /**
     * Set once {@link #initIteration} has produced a graph to run; the checker's reset after the
     * final {@code HaltCheckerException.ok()} finds it false and does nothing.
     */
    private boolean active;
    /** The first fault (error message) detected in this iteration, or {@code null}. */
    private String fault;
    /** The checker's report, given at {@link #initIteration}. */
    private JmcModelCheckerReport report;
    /** Iterations that ended with a blocked graph. */
    private int blockedIterations;
    /**
     * Runtime task id to stable id (the id of the task's creation-path name), and back. The second
     * map is replaced, not cleared, at each iteration, because receive guards raised earlier keep
     * reading the map of their own iteration.
     */
    private final Map<Long, Long> stableOfRuntime = new HashMap<>();
    private Map<Long, Long> runtimeOfStable = new HashMap<>();
    /** Children started so far per stable parent: the spawn index of the next child. */
    private final Map<Long, Integer> childCount = new HashMap<>();
    /** Per stable task, the spawn/join predecessors of its next event (consumed by that event). */
    private final Map<Long, Set<EventKey>> pending = new HashMap<>();
    /** Daemon processes (stable ids), from the creation event's {@code daemon} parameter. */
    private final Set<Long> daemons = new HashSet<>();
    /** Tasks parked on an await of a blocked process; their Block is not a receive's. */
    private final Set<Long> awaitBlockedTasks = new HashSet<>();
    /**
     * Main is parked on its final request (raised by {@code MpProcess.runMain} as
     * {@code drainDaemons} when daemons were spawned).
     */
    private boolean mainDraining;
    /** Main raised its final request in this iteration. */
    private boolean drainRaised;
    /** Each task's role, the name it was spawned with, from its creation event; empty for none. */
    private final Map<Long, String> roleOfStable = new HashMap<>();
    /** A process failed and the fault has not yet been handed to main. */
    private boolean failureToMain;
    /** Main ended blocked on an await of a process that itself ended blocked. */
    private boolean mainBlockedOnAwait;

    // across iterations
    /**
     * Creation-path names and their stable ids (kept across iterations); main is {@code "m"}, id 1,
     * and a task created by name {@code p} as its i-th creation is {@code p + "/" + i}.
     */
    private final Map<String, Long> idOfName = new HashMap<>();
    private final Map<Long, String> nameOfId = new HashMap<>();
    private long nextStableId = 2;
    /** Canonical forms of the distinct complete graphs seen. */
    private final Set<GraphCanon> completeGraphs = new LinkedHashSet<>();
    /** Canonical forms of the distinct blocked graphs seen. */
    private final Set<GraphCanon> blockedGraphs = new LinkedHashSet<>();
    /** Summaries of the explored executions with their multiplicities. */
    private final LinkedHashMap<Summary, Integer> summaries = new LinkedHashMap<>();
    /** The declared roles, or {@code null} when nothing is exported. */
    private final List<String> rolesInSpawnOrder;
    /** The roles whose events appear in the summaries; a subset of the declared roles. */
    private final List<String> visibleRoles;
    private int exportedIterations;
    private int skippedIterations;
    /** Iterations that ran. */
    private int iterations;

    // ---------------------------------------------------------------- measurements

    /** {@link System#nanoTime()} when the exploration started (iteration 0); 0 before. */
    private long exploreStartNanos;
    /** Cumulative time spent turning graphs into summaries (in {@link #export}, lookups excluded). */
    private long summariseNanos;
    /** Cumulative time spent in specification lookups. */
    private long lookupNanos;
    /** The duration of the lookup inside the current {@link #export}, subtracted from its summarise time. */
    private long lastLookupNanos;
    /** Graphs measured for the shape counters (every counted iteration). */
    private long measuredGraphs;
    /** Sum and maximum of the events per measured graph. */
    private long sumGraphEvents;
    private int maxGraphEvents;
    /** Maximum send and receive events (all threads) per measured graph. */
    private int maxMessageEvents;
    /** Sum and maximum of the send and receive events of visible roles per summarised graph. */
    private long sumVisibleEvents;
    private int maxVisibleEvents;
    /** Graphs summarised for the visible-event counters. */
    private long summarisedGraphs;
    /** Maximum UTF-8 size of a summary line without its multiplicity. */
    private int maxSummaryBytes;
    /** The largest single order-count entry of any summary. */
    private int maxPorfCount;
    /** The largest number of non-zero order-count entries in one summary. */
    private int maxPorfPairs;
    /** Wall time, since the start of the exploration, at which the first uncovered summary was found. */
    private long firstMissNanos = -1;

    /**
     * The factory's constructor: explores and counts, exports nothing.
     *
     * @param seed unused (FIFO scheduling); accepted for the factory's signature
     * @param reportPath unused; accepted for the factory's signature
     */
    public MustStrategy(Long seed, String reportPath) {
        this(seed, reportPath, null, null);
    }

    /**
     * The exporting constructor: also summarises every explored execution.
     *
     * @param seed unused
     * @param reportPath unused
     * @param rolesInSpawnOrder the declared roles: the name every process main spawns is given by
     *     {@code MpProcess.spawn(role, body)}, carried on its creation event. The list is only an
     *     unordered check that each role is spawned exactly once; {@code null} for no export
     * @param visibleRoles the roles summarised; a subset of {@code rolesInSpawnOrder}
     * @throws IllegalArgumentException if the roles are not distinct, a visible role is not
     *     declared, or a role is reserved ({@link #checkDeclaredRole})
     */
    public MustStrategy(
            Long seed, String reportPath, List<String> rolesInSpawnOrder, List<String> visibleRoles) {
        super(List.of(new TrackTasks()));
        idOfName.put("m", 1L);
        nameOfId.put(1L, "m");
        this.rolesInSpawnOrder = rolesInSpawnOrder == null ? null : List.copyOf(rolesInSpawnOrder);
        this.visibleRoles = visibleRoles == null ? null : List.copyOf(visibleRoles);
        if (this.rolesInSpawnOrder != null
                && (this.visibleRoles == null || !this.rolesInSpawnOrder.containsAll(this.visibleRoles))) {
            throw new IllegalArgumentException("visible roles must be among the spawned roles");
        }
        if (this.rolesInSpawnOrder != null
                && new HashSet<>(this.rolesInSpawnOrder).size() != this.rolesInSpawnOrder.size()) {
            // As the recorder: an invisible role may still name a task inside a visible payload.
            throw new IllegalArgumentException("roles are not distinct: " + rolesInSpawnOrder);
        }
        if (this.rolesInSpawnOrder != null) {
            for (String r : this.rolesInSpawnOrder) {
                checkDeclaredRole(r);
            }
        }
    }

    // ---------------------------------------------------------------- iteration lifecycle

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) throws HaltCheckerException {
        super.initIteration(iteration, report);
        this.report = report;
        clearIteration();
        if (stopRequested) {
            // The first uncovered summary was found in the iteration that just ended. If no work
            // was left anyway, the enumeration is complete all the same ("exhausted").
            exhausted = stack.isEmpty();
            LOGGER.debug("must: stopping after an uncovered summary in iteration {}", uncoveredIteration);
            throw HaltCheckerException.ok();
        }
        if (iteration == 0) {
            graph = new Graph();
            active = true;
            exploreStartNanos = System.nanoTime();
            return;
        }
        while (true) {
            Item item = stack.pollFirst();
            if (item == null) {
                exhausted = true;
                LOGGER.debug("must: exploration stack empty after {} iterations", iterations);
                throw HaltCheckerException.ok();
            }
            Graph g = apply(item);
            Optional<MpConsistency.Violation> v = MpConsistency.check(g.toExecutionGraph());
            if (v.isPresent()) {
                LOGGER.debug("must: dropping inconsistent alternative {}: {}", item, v.get());
                continue;
            }
            graph = g;
            for (Map.Entry<Long, List<Graph.Node>> e : g.tasks().entrySet()) {
                guideLen.put(e.getKey(), e.getValue().size());
            }
            LOGGER.debug("must: iteration {} guided by {} events", iteration, g.size());
            active = true;
            return;
        }
    }

    private Graph apply(Item item) {
        if (item instanceof Backward b) {
            return b.g();
        }
        if (item instanceof Forward f) {
            Graph g = f.g().copy();
            Graph.Node r = g.append(f.task(), f.label(), f.guard(), f.hbIn());
            if (f.send() == null) {
                g.setBottom(r.key);
            } else {
                g.setRf(r.key, f.send());
            }
            return g;
        }
        Nondet n = (Nondet) item;
        Graph g = n.g().copy();
        g.append(n.task(), new MpLabel.Nondet(n.optionCount(), n.chosen()), null, n.hbIn());
        return g;
    }

    @Override
    public void resetIteration(int iteration) {
        // First thing, before the runtime wakes the tasks still paused: process threads left over
        // from this iteration must not report into the next one (see MpThread.newEpoch).
        MpThread.newEpoch();
        if (!active) {
            // No iteration ran (the run ended in initIteration); nothing to count or export.
            super.resetIteration(iteration);
            clearIteration();
            return;
        }
        iterations++;
        boolean errorRecorded = report != null && report.getErrorIteration() != null
                && report.getErrorIteration() == iteration;
        String problem = fault;
        try {
            super.resetIteration(iteration);
            if (!errorRecorded && problem == null) {
                if (!parked.isEmpty()) {
                    problem = "iteration ended with parked tasks " + parked.keySet();
                } else if (unconsumedGuide() != null) {
                    problem = "replay drift: " + unconsumedGuide();
                } else {
                    GraphCanon canon = graph.toExecutionGraph().canon();
                    measureShape();
                    if (countsAsBlocked()) {
                        blockedGraphs.add(canon);
                    } else {
                        completeGraphs.add(canon);
                    }
                    if (rolesInSpawnOrder != null) {
                        try {
                            export();
                        } catch (IllegalArgumentException e) {
                            problem = e.getMessage();
                        }
                    }
                }
            }
            if (errorRecorded || problem != null) {
                skippedIterations++;
            }
        } finally {
            clearIteration();
        }
        if (problem != null && !errorRecorded) {
            throw HaltExecutionException.error("must: " + problem);
        }
    }

    private void clearIteration() {
        stableOfRuntime.clear();
        // A fresh map, not a cleared one: guards raised in this iteration keep reading it.
        runtimeOfStable = new HashMap<>();
        stableOfRuntime.put(1L, 1L);
        runtimeOfStable.put(1L, 1L);
        childCount.clear();
        pending.clear();
        roleOfStable.clear();
        daemons.clear();
        awaitBlockedTasks.clear();
        mainDraining = false;
        drainRaised = false;
        failureToMain = false;
        mainBlockedOnAwait = false;
        guideLen.clear();
        pos.clear();
        currentValue.clear();
        answers.clear();
        parked.clear();
        started.clear();
        awaited.clear();
        mainAwaited = false;
        iterationBlocked = false;
        active = false;
        fault = null;
    }

    @Override
    public void teardown(JmcModelCheckerReport report) {
        super.teardown(report);
        if (report != null) {
            report.setBlockedIterations(blockedIterations);
        }
    }

    // ---------------------------------------------------------------- events

    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        if (failureToMain && event.getTaskId() == 1L) {
            // A process failed: the iteration's error reaches main on its next event (a failure in
            // any process is an error of the iteration).
            failureToMain = false;
            throw HaltExecutionException.error("must: " + fault);
        }
        super.updateEvent(event);
        long runtimeTask = event.getTaskId();
        Long stable = stableOfRuntime.get(runtimeTask);
        if (stable == null && event.getType() == JmcRuntimeEvent.Type.START_EVENT) {
            // A task not created by MpThread (e.g. a JmcThread): outside the Must API, so the
            // iteration is an error. It is still named here, at START, so that it is scheduled and
            // the iteration runs to its end and reports the fault instead of hanging.
            fail("task " + runtimeTask + " was not created by MpThread; the Must-API spawns only MpThreads");
            Long by = event.getParam("startedBy");
            Long creator = by == null ? null : stableOfRuntime.get(by);
            name(runtimeTask, creator == null ? 1L : creator);
            stable = stableOfRuntime.get(runtimeTask);
        }
        if (stable == null) {
            fail("task " + runtimeTask + " raised " + event.getType() + " but was never created by MpThread");
            return;
        }
        long task = stable;
        if (event.getType() == JmcRuntimeEvent.Type.TASK_CREATED_EVENT) {
            // Raised by MpThread's constructor, on the creator's thread: name the new task now, so
            // that every Tid the program can hold has its name before any event uses it.
            Long created = optParam(event, "createdTask") instanceof Long c ? c : null;
            if (created == null) {
                fail("TASK_CREATED_EVENT of task " + runtimeTask + " without createdTask");
            } else {
                name(created, task);
                roleOfStable.put(stableOfRuntime.get(created),
                        optParam(event, "role") instanceof String r ? r : "");
                if (Boolean.TRUE.equals(optParam(event, "daemon"))) {
                    daemons.add(stableOfRuntime.get(created));
                }
            }
            return;
        }
        if (event.getType() == JmcRuntimeEvent.Type.START_EVENT && runtimeTask != 1L) {
            inheritSpawnFrontier(task, event.getParam("startedBy"));
        }
        switch (event.getType()) {
            case START_EVENT ->
            {
                started.add(task);
                Long parent = event.getParam("startedBy");
                if (task != 1L && parent != null && parent != 1L) {
                    shape("process " + task + " was spawned by process " + parent);
                }
                if (task != 1L && mainAwaited) {
                    shape("process " + task + " was spawned after main's first await");
                }
            }
            case JOIN_REQUEST_EVENT ->
            {
                if (task != 1L) {
                    shape("process " + task + " awaits another process");
                }
            }
            case JOIN_COMPLETE_EVENT ->
            {
                Long joinedRuntime = event.getParam("joinedTask");
                Long joined = joinedRuntime == null ? null : stableOfRuntime.get(joinedRuntime);
                if (task == 1L) {
                    mainAwaited = true;
                    if (joined != null) {
                        awaited.add(joined);
                    }
                }
                if (joined != null) {
                    // The join edge: the joined task's last event, plus the joins (or inherited
                    // spawn frontier) pending on it since, as TraceForge's End event carries.
                    EventKey last = lastProduced(joined);
                    Set<EventKey> into = pending.computeIfAbsent(task, t -> new HashSet<>());
                    if (last != null) {
                        into.add(last);
                    }
                    into.addAll(pending.getOrDefault(joined, Set.of()));
                }
            }
            case MP_SEND_EVENT -> onSend(task, event);
            case MP_RECV_REQUEST_EVENT -> onRecv(task, event);
            case ND_EVENT -> onNondet(task, event);
            case FINISH_EVENT ->
            {
                if (task == 1L && !daemons.isEmpty() && !drainRaised) {
                    // Main returned normally without runMain's final request, so daemons would
                    // outlive the iteration. A main that ended blocked raises no FINISH (the
                    // checker catches the block), and no daemon outlives it.
                    fail("daemon(s) were spawned but main did not end through MpProcess.runMain;"
                            + " daemons would outlive the iteration");
                }
                if (task != 1L && Boolean.TRUE.equals(optParam(event, "failed"))) {
                    // A process failure is an error of the iteration when it happens, awaited or
                    // not (TraceForge ends the exploration on a panic in any thread). Latched
                    // here, on the failing thread; handed to main next.
                    boolean first = fault == null;
                    fail("process " + nameOfId.get(task) + " (task " + runtimeTask + ") failed: "
                            + optParam(event, "failure"));
                    failureToMain |= first;
                }
                if (replaying(task)) {
                    // The guide has events this task never raised (TraceForge validates the end
                    // event the same way): the graph would contain events that did not execute.
                    fail("replay drift: task " + task + " finished at guide position "
                            + pos.getOrDefault(task, 0) + " of " + guideLen.get(task));
                }
            }
            default ->
            {
            }
        }
    }

    /** A task whose guide is not fully consumed at the end of the iteration, or {@code null}. */
    private String unconsumedGuide() {
        for (Map.Entry<Long, Integer> e : guideLen.entrySet()) {
            int at = pos.getOrDefault(e.getKey(), 0);
            if (at < e.getValue()) {
                return "task " + e.getKey() + " ended at guide position " + at + " of " + e.getValue();
            }
        }
        return null;
    }

    /**
     * Names a created task by its creation path (as TraceForge names a thread at its spawn): the
     * creator's name and the index of this creation among the creator's creations in this
     * iteration. Registers the stable id both ways; the same path gets the same id in every
     * iteration.
     *
     * @param runtimeTask the new task's runtime id
     * @param creator the creator's stable id
     */
    private void name(long runtimeTask, long creator) {
        int index = childCount.merge(creator, 1, Integer::sum) - 1;
        String name = nameOfId.get(creator) + "/" + index;
        Long id = idOfName.get(name);
        if (id == null) {
            id = nextStableId++;
            idOfName.put(name, id);
            nameOfId.put(id, name);
        }
        if (runtimeOfStable.containsKey(id) || stableOfRuntime.containsKey(runtimeTask)) {
            fail("two tasks named " + name + " in one iteration");
        }
        stableOfRuntime.put(runtimeTask, id);
        runtimeOfStable.put(id, runtimeTask);
    }

    /**
     * A started task's first event comes after the starter's spawn frontier: the starter's last
     * event and the joins it completed since. That set becomes the task's {@code pending}
     * predecessors.
     *
     * @param task the started task (stable id)
     * @param startedByRuntime the runtime id of the starter; {@code null} means main
     */
    private void inheritSpawnFrontier(long task, Long startedByRuntime) {
        Long parent = startedByRuntime == null ? Long.valueOf(1L) : stableOfRuntime.get(startedByRuntime);
        if (parent == null) {
            fail("task " + nameOfId.get(task) + " was started by task " + startedByRuntime + ", which has no name");
            return;
        }
        Set<EventKey> frontier = new HashSet<>(pending.getOrDefault(parent, Set.of()));
        EventKey last = lastProduced(parent);
        if (last != null) {
            frontier.add(last);
        }
        pending.put(task, frontier);
    }

    /** The last event a task has produced in this iteration, or {@code null}. */
    private EventKey lastProduced(long task) {
        int at = pos.getOrDefault(task, 0);
        return at > 0 ? new EventKey(task, at - 1) : null;
    }

    /** The spawn/join predecessors of a task's next event; they are consumed by that event. */
    private Set<EventKey> takePending(long task) {
        Set<EventKey> p = pending.remove(task);
        return p == null ? Set.of() : Set.copyOf(p);
    }

    /** A replayed node must have the spawn/join predecessors the replay computes. */
    private void checkPending(long task, Graph.Node n) throws HaltExecutionException {
        Set<EventKey> now = takePending(task);
        if (!now.equals(n.hbIn)) {
            throw drift(task, n, "an event whose spawn/join predecessors are " + now + ", not " + n.hbIn);
        }
    }

    /**
     * A runtime {@code Tid} as the graph names it: its stable id (every MpThread is named at
     * creation). An unnamed task is a fault and maps to a negative id.
     */
    private Tid stableTid(Tid runtime) {
        Long id = stableOfRuntime.get(runtime.raw());
        if (id == null) {
            fail("a send names task " + runtime.raw() + ", which was not created by MpThread in this iteration");
            return Tid.of(-runtime.raw());
        }
        return Tid.of(id);
    }

    /**
     * A stored {@code Tid} as an iteration numbered it at runtime, through that iteration's map
     * {@code ids}; a task that iteration did not have maps to a negative id no task has.
     */
    private static Tid runtimeTid(Tid stored, Map<Long, Long> ids) {
        Long rt = ids.get(stored.raw());
        return Tid.of(rt != null ? rt : -stored.raw());
    }

    /**
     * The program's guard, made to test stored (normalised) values: the program compares
     * payload {@code Tid}s with the runtime ids of the iteration that raised the receive, so a
     * stored value is mapped back through that iteration's ids before the guard sees it. The map is
     * the iteration's own (a fresh one per iteration): a guard raised earlier is still evaluated in
     * later iterations, by the consistency check of a popped alternative and by revisits.
     */
    private Predicate<MpMessage> onStored(Predicate<MpMessage> guard) {
        Map<Long, Long> ids = runtimeOfStable;
        return m -> guard.test((MpMessage) mapTids(m, t -> runtimeTid(t, ids)));
    }

    /**
     * A message value with every {@code Tid} inside a record, list, set or optional replaced by its
     * stable id, so that values compare and export independently of runtime numbering. A value
     * that cannot be rebuilt is returned as is.
     */
    private Object normalize(Object v) {
        return mapTids(v, this::stableTid);
    }

    /** {@code v} with {@code f} applied to every {@code Tid} in a record, list, set or optional. */
    private static Object mapTids(Object v, java.util.function.UnaryOperator<Tid> f) {
        if (v instanceof Tid t) {
            return f.apply(t);
        }
        if (v instanceof Record r) {
            RecordComponent[] cs = r.getClass().getRecordComponents();
            Object[] args = new Object[cs.length];
            Class<?>[] types = new Class<?>[cs.length];
            boolean changed = false;
            try {
                for (int i = 0; i < cs.length; i++) {
                    cs[i].getAccessor().setAccessible(true);
                    Object x = cs[i].getAccessor().invoke(r);
                    Object y = mapTids(x, f);
                    changed |= y != x;
                    args[i] = y;
                    types[i] = cs[i].getType();
                }
                if (!changed) {
                    return v;
                }
                Constructor<?> c = r.getClass().getDeclaredConstructor(types);
                c.setAccessible(true);
                return c.newInstance(args);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return v;
            }
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            boolean changed = false;
            for (Object x : l) {
                Object y = mapTids(x, f);
                changed |= y != x;
                out.add(y);
            }
            return changed ? Collections.unmodifiableList(out) : v;
        }
        if (v instanceof Set<?> set) {
            Set<Object> out = new LinkedHashSet<>();
            boolean changed = false;
            for (Object x : set) {
                Object y = mapTids(x, f);
                changed |= y != x;
                out.add(y);
            }
            return changed ? Collections.unmodifiableSet(out) : v;
        }
        if (v instanceof Optional<?> o && o.isPresent()) {
            Object y = mapTids(o.get(), f);
            return y != o.get() ? Optional.of(y) : v;
        }
        return v;
    }

    /**
     * Only the p2p and asyn delivery rules are implemented ({@link #deliverable}); an event of any
     * other model (causal delivery, mailbox) is refused rather than explored under the wrong rule.
     *
     * @throws HaltExecutionException if the model is unsupported
     */
    private void refuseUnsupported(long task, CommunicationModel model) throws HaltExecutionException {
        if (model != CommunicationModel.P2P && model != CommunicationModel.ASYN) {
            throw halt("task " + task + " uses model " + model + ", which must does not support (p2p and asyn only)");
        }
    }

    /** Whether the task still has guide events to replay. */
    private boolean replaying(long task) {
        return pos.getOrDefault(task, 0) < guideLen.getOrDefault(task, 0);
    }

    /** The next guide node of a replaying task. */
    private Graph.Node guideNode(long task) {
        return graph.task(task).get(pos.getOrDefault(task, 0));
    }

    /** Counts one more event of the task as produced. */
    private void advance(long task) {
        pos.merge(task, 1, Integer::sum);
    }

    /**
     * A send event. While replaying it must match the guide node (else the program is
     * nondeterministic and the iteration fails) and wakes the receives waiting for it; a free send
     * is appended to the graph and its backward revisits are computed.
     */
    private void onSend(long task, JmcRuntimeEvent event) throws HaltExecutionException {
        CommunicationModel model = event.getParam("model");
        Tid dst = event.getParam("dst");
        MpMessage value = event.getParam("value");
        if (task == 1L) {
            shape("main performs a send");
        }
        refuseUnsupported(task, model);
        Tid dstStable = stableTid(dst);
        MpMessage stored = (MpMessage) normalize(value);
        if (replaying(task)) {
            Graph.Node n = guideNode(task);
            if (!(n.label instanceof MpLabel.Send s) || s.model() != model || !s.dst().equals(dstStable)
                    || !s.value().equals(stored)) {
                throw drift(task, n, "send(" + model + ", " + dst + ", " + value + ")");
            }
            checkPending(task, n);
            currentValue.put(n.key, value);
            advance(task);
            retryGuidedParked(n.key);
            return;
        }
        Graph.Node s = graph.append(task, new MpLabel.Send(model, dstStable, stored), null, takePending(task));
        currentValue.put(s.key, value);
        advance(task);
        backwardRevisits(s);
    }

    /**
     * A receive request (or a request that is parked like one: an await of a blocked process, or
     * main's final request). While replaying, a receive is answered from the guide node: ⊥ at
     * once, a send once that send has been raised in this iteration, else parked. A free receive
     * goes to {@link #issueReceive}.
     */
    private void onRecv(long task, JmcRuntimeEvent event) throws HaltExecutionException {
        CommunicationModel model = event.getParam("model");
        Predicate<MpMessage> guard = event.getParam("guard");
        Predicate<MpMessage> storedGuard = onStored(guard);
        boolean blocking = Boolean.TRUE.equals(event.getParam("blocking"));
        if (optParam(event, "awaitOfBlocked") != null) {
            // MpThread.join1 awaiting a process that ended blocked. Nothing can answer it, so
            // awaiting a blocked process blocks the caller: it is parked like a free blocking
            // receive with nothing to read and blocked when nothing else runs. Not a message
            // event of main.
            if (replaying(task)) {
                throw drift(task, guideNode(task), "an await of a blocked process");
            }
            if (task == 1L) {
                mainBlockedOnAwait = true;
            }
            awaitBlockedTasks.add(task);
            parked.put(task, new Parked(model, guard, true, null, false));
            return;
        }
        if (optParam(event, "drainDaemons") != null) {
            // Main's final request after its body (MpProcess.runMain, raised only when daemons
            // were spawned). Not a message event; answered when no other task can move.
            if (task != 1L) {
                throw halt("task " + task + " raised main's final request (drainDaemons)");
            }
            mainDraining = true;
            drainRaised = true;
            parked.put(task, new Parked(model, guard, true, null, false));
            return;
        }
        if (task == 1L) {
            shape("main performs a receive");
        }
        refuseUnsupported(task, model);
        if (replaying(task)) {
            Graph.Node n = guideNode(task);
            if (!(n.label instanceof MpLabel.Recv r) || r.model() != model || r.blocking() != blocking) {
                throw drift(task, n, "recv(" + model + ", blocking=" + blocking + ")");
            }
            checkPending(task, n);
            if (n.bottom) {
                answers.put(task, MpDeliveryValue.bottom());
                advance(task);
            } else if (n.rf != null && currentValue.containsKey(n.rf)) {
                // The committed message must still match: the guard raised now is the program's,
                // the one in the snapshot was the earlier iteration's.
                if (!guard.test(currentValue.get(n.rf))) {
                    throw drift(task, n, "recv whose guard rejects the committed message " + currentValue.get(n.rf));
                }
                answers.put(task, delivery(n.rf));
                advance(task);
            } else {
                parked.put(task, new Parked(model, storedGuard, blocking, n.rf, true));
            }
            return;
        }
        issueReceive(task, model, storedGuard, blocking);
    }

    /**
     * A free receive (line 7). Computes the receive's options ({@link #deliverable}); a
     * non-blocking receive explores ⊥ and pushes a {@link Forward} item per option; a blocking
     * receive explores the least option and pushes the others, or is parked if it has none.
     */
    private void issueReceive(long task, CommunicationModel model, Predicate<MpMessage> guard, boolean blocking) {
        List<EventKey> options = deliverable(graph, task, model, guard, null);
        MpLabel.Recv label = new MpLabel.Recv(model, blocking);
        if (!blocking) {
            Graph before = graph.copy();
            Set<EventKey> hbIn = takePending(task);
            Graph.Node r = graph.append(task, label, guard, hbIn);
            graph.setBottom(r.key);
            answers.put(task, MpDeliveryValue.bottom());
            advance(task);
            for (int i = options.size() - 1; i >= 0; i--) {
                stack.push(new Forward(before, task, label, guard, options.get(i), hbIn));
            }
            return;
        }
        if (options.isEmpty()) {
            parked.put(task, new Parked(model, guard, true, null, false));
            return;
        }
        Graph before = graph.copy();
        Set<EventKey> hbIn = takePending(task);
        Graph.Node r = graph.append(task, label, guard, hbIn);
        EventKey chosen = options.get(0);
        graph.setRf(r.key, chosen);
        advance(task);
        for (int i = options.size() - 1; i >= 1; i--) {
            stack.push(new Forward(before, task, label, guard, options.get(i), hbIn));
        }
        if (currentValue.containsKey(chosen)) {
            answers.put(task, delivery(chosen));
        } else {
            // The chosen send is in the graph (from the guide) but its task has not re-raised it
            // yet in this iteration: the rf is committed, the message arrives with the send
            // (TraceForge parks the task as "stuck").
            parked.put(task, new Parked(model, guard, true, chosen, false));
        }
    }

    /**
     * A nondeterministic choice (line 6). Replayed from the guide, or free: it explores index 0
     * and pushes a {@link Nondet} item for each other index.
     */
    private void onNondet(long task, JmcRuntimeEvent event) throws HaltExecutionException {
        Integer optionCount = event.getParam("optionCount");
        if (replaying(task)) {
            Graph.Node n = guideNode(task);
            if (!(n.label instanceof MpLabel.Nondet nd) || nd.optionCount() != optionCount) {
                throw drift(task, n, "nondet(" + optionCount + ")");
            }
            checkPending(task, n);
            answers.put(task, new PrimitiveValue(nd.chosen()));
            advance(task);
            return;
        }
        Graph before = graph.copy();
        Set<EventKey> hbIn = takePending(task);
        graph.append(task, new MpLabel.Nondet(optionCount, 0), null, hbIn);
        answers.put(task, new PrimitiveValue(0));
        advance(task);
        for (int v = optionCount - 1; v >= 1; v--) {
            stack.push(new Nondet(before, task, optionCount, v, hbIn));
        }
    }

    /** The answer that delivers a send's message to a receive, in the runtime's numbering. */
    private MpDeliveryValue delivery(EventKey send) {
        return MpDeliveryValue.of(
                Tid.of(runtimeOfStable.get(send.tid())), graph.sendIndexOf(send), currentValue.get(send));
    }

    /** A replayed send arrived: answer the receives that were waiting for it. */
    private void retryGuidedParked(EventKey send) {
        List<Long> ready = new ArrayList<>();
        parked.forEach((t, p) -> {
            if (send.equals(p.waitingFor())) {
                ready.add(t);
            }
        });
        for (long t : ready) {
            Parked p = parked.remove(t);
            if (!p.guard().test(((MpLabel.Send) graph.node(send).label).value())) {
                fail("replay drift: task " + t + " waited for " + send + ", whose message "
                        + currentValue.get(send) + " its guard rejects");
                answers.put(t, new MpFaultValue("must: " + fault));
                continue;
            }
            answers.put(t, delivery(send));
            if (p.guided()) {
                advance(t);
            }
        }
    }

    // ---------------------------------------------------------------- rf options and revisits

    /**
     * The sends a receive of {@code task} may read (the paper's consistent rf options, TraceForge's
     * {@code rfs}): unread (within {@code within}, if given), to {@code task}, under {@code model},
     * accepted by {@code guard}, so-minimal (p2p: the earliest per sender; asyn: all), sorted by
     * {@code (tid, index)}.
     *
     * @param g the graph
     * @param task the receiving task
     * @param model the communication model of the receive
     * @param guard the receive's predicate on messages
     * @param within the events to consider and the receives whose reads count, or {@code null} for
     *     the whole graph
     * @return the readable sends, in order
     */
    private static List<EventKey> deliverable(
            Graph g, long task, CommunicationModel model, Predicate<MpMessage> guard, Set<EventKey> within) {
        return deliverable(g, task, model, guard, within, null);
    }

    /**
     * As {@link #deliverable(Graph, long, CommunicationModel, Predicate, Set)}, where in addition
     * the read of the receive {@code self} (possibly {@code null}) does not count as a read.
     */
    private static List<EventKey> deliverable(
            Graph g, long task, CommunicationModel model, Predicate<MpMessage> guard,
            Set<EventKey> within, EventKey self) {
        Map<Long, EventKey> earliestPerSender = new TreeMap<>();
        List<EventKey> all = new ArrayList<>();
        for (Graph.Node n : g.nodesByStamp()) {
            if (!(n.label instanceof MpLabel.Send s) || s.dst().raw() != task || s.model() != model) {
                continue;
            }
            if (within != null && !within.contains(n.key)) {
                continue;
            }
            EventKey reader = g.readerOf(n.key);
            boolean read = reader != null && !reader.equals(self) && (within == null || within.contains(reader));
            if (read || !guard.test(s.value())) {
                continue;
            }
            all.add(n.key);
            earliestPerSender.merge(n.key.tid(), n.key, (a, b) -> a.idx() <= b.idx() ? a : b);
        }
        List<EventKey> out = model == CommunicationModel.P2P ? new ArrayList<>(earliestPerSender.values()) : all;
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /**
     * Lines 10–13 for a freshly added send {@code s}: every receive that could read it (same
     * destination and model, accepted by its guard, not porf-before {@code s}) is a candidate.
     * For a candidate {@code r}, the events inserted after {@code r} that are not in the porf
     * prefix of {@code s} are deleted; if {@code r} and every deleted event satisfy the revisit
     * condition ({@link #revisitCondition}), the graph restricted to the remaining events, with
     * {@code r} reading {@code s}, is pushed as a {@link Backward} item.
     */
    private void backwardRevisits(Graph.Node s) {
        MpLabel.Send sl = (MpLabel.Send) s.label;
        List<Graph.Node> candidates = new ArrayList<>();
        for (Graph.Node r : graph.nodesByStamp()) {
            if (!(r.label instanceof MpLabel.Recv rl) || r.key.tid() != sl.dst().raw() || rl.model() != sl.model()) {
                continue;
            }
            if (r == s || !r.guard.test(sl.value()) || s.prefix.contains(r.key)) {
                continue;
            }
            candidates.add(r);
        }
        for (Graph.Node r : candidates) {
            Set<EventKey> deleted = new HashSet<>();
            for (Graph.Node e : graph.nodesByStamp()) {
                if (e.stamp > r.stamp && e != s && !s.prefix.contains(e.key)) {
                    deleted.add(e.key);
                }
            }
            boolean ok = revisitCondition(r, s);
            for (Graph.Node e : graph.nodesByStamp()) {
                if (!ok) {
                    break;
                }
                if (deleted.contains(e.key)) {
                    ok = revisitCondition(e, s);
                }
            }
            if (!ok) {
                continue;
            }
            Set<EventKey> keep = new HashSet<>();
            for (Graph.Node e : graph.nodesByStamp()) {
                if (!deleted.contains(e.key)) {
                    keep.add(e.key);
                }
            }
            Graph restricted = graph.restrictTo(keep);
            restricted.setRf(r.key, s.key);
            stack.push(new Backward(restricted));
            LOGGER.debug("must: backward revisit {} <- {}", r.key, s.key);
        }
    }

    /**
     * Lines 17–22 for {@code e} and the revisiting send {@code s}, on the current graph: whether
     * {@code e} is maximal, i.e. exploring it differently now would not duplicate another
     * execution. A non-blocking receive must read ⊥, a nondet must have chosen index 0, a send
     * must not be read by an event kept in the previous graph, and a blocking receive must read
     * the least of its options among the events inserted no later than it or in the prefix of
     * {@code s}.
     */
    private boolean revisitCondition(Graph.Node e, Graph.Node s) {
        if (e.label instanceof MpLabel.Recv r && !r.blocking()) {
            return e.bottom;
        }
        if (e.label instanceof MpLabel.Nondet nd) {
            return nd.chosen() == 0;
        }
        Set<EventKey> previous = new HashSet<>();
        for (Graph.Node x : graph.nodesByStamp()) {
            if (x.stamp <= e.stamp || s.prefix.contains(x.key)) {
                previous.add(x.key);
            }
        }
        if (!e.isRecv()) {
            if (!e.isSend()) {
                return true;
            }
            EventKey reader = graph.readerOf(e.key);
            return reader == null || !previous.contains(reader);
        }
        MpLabel.Recv r = (MpLabel.Recv) e.label;
        List<EventKey> options = deliverable(graph, e.key.tid(), r.model(), e.guard, previous, e.key);
        return !options.isEmpty() && options.get(0).equals(e.rf);
    }

    // ---------------------------------------------------------------- scheduling

    /**
     * Picks the next task: the lowest-id runnable, unparked task, handed its pending answer. When
     * none is runnable it resumes main with the fault if a process failed, re-issues parked
     * receives that now have a message, and otherwise blocks the parked task of the highest id (or
     * releases main's final request). Returns {@code null} when no task is left.
     */
    @Override
    public SchedulingChoice<?> nextTask() {
        TreeSet<Long> runnable = new TreeSet<>();
        for (Long rt : getActiveTasks()) {
            Long st = stableOfRuntime.get(rt);
            if (st != null) {
                runnable.add(st);
            }
        }
        runnable.removeAll(parked.keySet());
        if (!runnable.isEmpty()) {
            long t = runnable.first();
            SchedulingChoiceValue a = answers.remove(t);
            long rt = runtimeOfStable.get(t);
            return a != null ? SchedulingChoice.task(rt, a) : SchedulingChoice.task(rt);
        }
        if (parked.isEmpty()) {
            return null;
        }
        if (failureToMain && parked.containsKey(1L)) {
            // Main is parked on a receive and a process has failed: resume main with the fault
            // rather than block it. An await of a blocked process gets the fault earlier,
            // at main's await event (updateEvent).
            parked.remove(1L);
            failureToMain = false;
            return SchedulingChoice.task(1L, new MpFaultValue("must: " + fault));
        }
        // Nothing runs: re-issue parked free receives that have a message now.
        // A re-issued receive may itself have to wait for its send to be re-raised, so loop until
        // one is answerable or none is left to re-issue.
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Map.Entry<Long, Parked> e : new ArrayList<>(parked.entrySet())) {
                Parked p = e.getValue();
                long t = e.getKey();
                if (p.waitingFor() == null && !deliverable(graph, t, p.model(), p.guard(), null).isEmpty()) {
                    parked.remove(t);
                    issueReceive(t, p.model(), p.guard(), true);
                    SchedulingChoiceValue a = answers.remove(t);
                    if (a != null) {
                        return SchedulingChoice.task(runtimeOfStable.get(t), a);
                    }
                    progress = true;
                }
            }
        }
        // Otherwise block: a receive still waiting for a send that nobody can raise any more is
        // replay drift (or a nondeterministic program); a free one with nothing to read becomes a
        // Block event of the graph.
        long t = parked.lastKey();
        Parked p = parked.remove(t);
        if (t == 1L && mainDraining) {
            // Every other task has finished or blocked; main's final request is answered
            // (main is the lowest id, so it is reached last). No Block for main.
            mainDraining = false;
            return SchedulingChoice.task(1L, new PrimitiveValue(0));
        }
        if (p.waitingFor() != null) {
            fail("replay drift: task " + t + " waits for " + p.waitingFor() + ", which this iteration never raised");
            return SchedulingChoice.task(runtimeOfStable.get(t), new MpFaultValue("must: " + fault));
        }
        graph.append(t, new MpLabel.Block(), null, takePending(t));
        advance(t);
        if (!iterationBlocked && countsBlocking(t)) {
            iterationBlocked = true;
            blockedIterations++;
            if (report != null) {
                report.setBlockedIterations(blockedIterations);
            }
        }
        return SchedulingChoice.blockTask(runtimeOfStable.get(t));
    }

    // ---------------------------------------------------------------- faults and export

    /** The halt for a replay that diverges from the guide (the program is nondeterministic). */
    private HaltExecutionException drift(long task, Graph.Node expected, String got) {
        return halt("replay drift: task " + task + " raised " + got + " where the guide expected " + expected);
    }

    /**
     * A fault detected on the raising task's own thread: recorded, and thrown to that thread so
     * that the checker's error is this diagnosis (otherwise an unanswered receive would fail
     * with the runtime's message instead). A fault detected elsewhere while a task waits for an
     * answer is delivered to that task as an {@link MpFaultValue} with the same effect. The first
     * fault of an iteration is the one kept.
     *
     * @return the exception for the caller to throw
     */
    private HaltExecutionException halt(String why) {
        fail(why);
        return HaltExecutionException.error("must: " + fault);
    }

    /** Records a fault if summaries are exported and the program breaks the required shape. */
    private void shape(String why) {
        if (rolesInSpawnOrder != null) {
            fail(why + "; the format requires main to spawn first, await all, and perform no message-passing event");
        }
    }

    /** Records a fault (the first one is kept) and logs it. */
    private void fail(String why) {
        if (fault == null) {
            fault = why;
        }
        LOGGER.error("must: {}", why);
    }

    /**
     * Whether a Block of {@code task} makes the graph blocked (as TraceForge's
     * {@code check_blocked}): it does not if it is a daemon's blocked receive. The reason comes
     * from the parked request, not the label ({@code MpLabel.Block} has none).
     */
    private boolean countsBlocking(long task) {
        return !daemons.contains(task) || awaitBlockedTasks.contains(task);
    }

    /** Whether some Block of the graph makes it blocked ({@link #countsBlocking}). */
    private boolean countsAsBlocked() {
        for (Graph.Node n : graph.nodesByStamp()) {
            if (n.label instanceof MpLabel.Block && countsBlocking(n.key.tid())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A declared role may not be an internal name: {@code "main"} is main, and names starting with
     * {@code "~"} are the exporter's internal aliases for daemons, so neither can become visible
     * or satisfy the never-spawned check.
     *
     * @param role the declared role
     * @throws IllegalArgumentException if the role is empty, {@code "main"}, or starts with
     *     {@code "~"}
     */
    public static void checkDeclaredRole(String role) {
        if (role.isEmpty() || role.equals("main") || role.startsWith("~")) {
            throw new IllegalArgumentException("role \"" + role + "\" is reserved: a declared role is"
                    + " non-empty, not \"main\", and does not start with \"~\"");
        }
    }

    /**
     * Summarises the iteration's graph and adds it to {@link #summaries}; in online mode also
     * queries the specification. Roles come from the creation events, checked against the declared
     * roles; daemons get internal names and are never visible.
     *
     * @throws IllegalArgumentException if the program does not fit the export format (an
     *     un-awaited process, an undeclared, missing or repeated role, a payload naming a daemon)
     */
    private void export() {
        long summariseStart = System.nanoTime();
        try {
            summariseAndLookUp();
        } finally {
            summariseNanos += System.nanoTime() - summariseStart - lastLookupNanos;
            lastLookupNanos = 0;
        }
    }

    /** {@link #export}'s body; the summarise time excludes the lookup, which is timed separately. */
    private void summariseAndLookUp() {
        Set<Long> notAwaited = new TreeSet<>(started);
        notAwaited.remove(1L);
        notAwaited.removeAll(awaited);
        notAwaited.removeAll(daemons);
        if (!notAwaited.isEmpty() && !mainBlockedOnAwait) {
            throw new IllegalArgumentException("process(es) " + notAwaited + " were never awaited by main");
        }
        Map<Long, String> roleOfTask = new HashMap<>();
        // The declared roles are checked against named non-daemon processes only.
        Set<String> spawnedRoles = new HashSet<>();
        for (long t : started) {
            if (t == 1L) {
                roleOfTask.put(t, "main");
                continue;
            }
            if (daemons.contains(t)) {
                // A daemon has no role and is never visible; this name is internal, outside every
                // declared role, and never encoded (see the encoder).
                roleOfTask.put(t, "~daemon " + nameOfId.get(t));
                continue;
            }
            // The role is the name the process was spawned with, as TraceForge identifies visible
            // threads by declared name; the declared list is only a check.
            String name = nameOfId.get(t);
            String role = roleOfStable.getOrDefault(t, "");
            if (role.isEmpty()) {
                throw new IllegalArgumentException("process " + name + " has no role; an exported program"
                        + " spawns every process with MpProcess.spawn(role, body)");
            }
            if (!rolesInSpawnOrder.contains(role)) {
                throw new IllegalArgumentException("process " + name + " has role " + role
                        + ", which is not declared " + rolesInSpawnOrder);
            }
            if (spawnedRoles.contains(role)) {
                throw new IllegalArgumentException("role " + role + " is spawned more than once");
            }
            spawnedRoles.add(role);
            roleOfTask.put(t, role);
        }
        for (String r : rolesInSpawnOrder) {
            if (!spawnedRoles.contains(r)) {
                throw new IllegalArgumentException("declared role " + r + " was never spawned");
            }
        }
        MpValueEncoder encoder = new MpValueEncoder(tid -> {
            if (daemons.contains(tid.raw())) {
                throw new IllegalArgumentException("payload names daemon " + nameOfId.get(tid.raw())
                        + "; a thread id is encoded as a role name and a daemon has none");
            }
            String r = roleOfTask.get(tid.raw());
            if (r == null) {
                throw new IllegalArgumentException("payload names task " + tid + ", which never started");
            }
            return r;
        });
        Summary s = SummaryExporter.summarize(graph.toExecutionGraph(), roleOfTask, visibleRoles, encoder);
        summaries.merge(s, 1, Integer::sum);
        exportedIterations++;
        int visibleEvents = 0;
        for (Map.Entry<Long, List<Graph.Node>> e : graph.tasks().entrySet()) {
            String role = roleOfTask.get(e.getKey());
            if (role != null && visibleRoles.contains(role)) {
                visibleEvents += messageEvents(e.getValue());
            }
        }
        summarisedGraphs++;
        sumVisibleEvents += visibleEvents;
        maxVisibleEvents = Math.max(maxVisibleEvents, visibleEvents);
        String line = s.toJson(0);
        // The canonical line without the multiplicity: drop the trailing ,"n":0 before the closing brace.
        String withoutN = line.substring(0, line.length() - ",\"n\":0}".length()) + "}";
        maxSummaryBytes = Math.max(maxSummaryBytes, withoutN.getBytes(StandardCharsets.UTF_8).length);
        int nonZero = 0;
        for (int c : s.counts()) {
            maxPorfCount = Math.max(maxPorfCount, c);
            if (c != 0) {
                nonZero++;
            }
        }
        maxPorfPairs = Math.max(maxPorfPairs, nonZero);
        if (spec != null && uncovered == null) {
            // As TraceForge's completion sink: covered, continue; uncovered, report and stop.
            lookups++;
            long lookupStart = System.nanoTime();
            SetIndex.Covers answer;
            try {
                answer = spec.covers(s);
            } finally {
                lastLookupNanos = System.nanoTime() - lookupStart;
                lookupNanos += lastLookupNanos;
            }
            if (answer instanceof SetIndex.Covers.Found) {
                found++;
            } else {
                if (answer instanceof SetIndex.Covers.NoSig) {
                    signatureMisses++;
                } else {
                    notFound++;
                }
                uncovered = s;
                uncoveredIteration = iterations;
                firstMissNanos = System.nanoTime() - exploreStartNanos;
                uncoveredAnswer = answer;
                stopRequested = true;
            }
        }
    }

    /** Updates the graph-shape counters with the iteration's graph. */
    private void measureShape() {
        int events = graph.size();
        measuredGraphs++;
        sumGraphEvents += events;
        maxGraphEvents = Math.max(maxGraphEvents, events);
        int messages = 0;
        for (List<Graph.Node> nodes : graph.tasks().values()) {
            messages += messageEvents(nodes);
        }
        maxMessageEvents = Math.max(maxMessageEvents, messages);
    }

    /** The send and receive events among {@code nodes}. */
    private static int messageEvents(List<Graph.Node> nodes) {
        int n = 0;
        for (Graph.Node node : nodes) {
            if (node.isSend() || node.isRecv()) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- results

    /**
     * Whether the enumeration is complete: the exploration stack ran empty, or the run stopped (e.g. at
     * the iteration budget) after an iteration with no work left — the stack empty and no iteration
     * in progress. A budget that is reached while work remains is not exhaustion.
     */
    public boolean exhausted() {
        return exhausted || (iterations > 0 && !active && stack.isEmpty());
    }

    /**
     * Enables the online mode: queries {@code spec} with every summary and stops the exploration at
     * the first one it does not cover. Needs the exporting constructor (the roles).
     *
     * @param spec the specification's index
     * @throws IllegalStateException if the strategy was built without roles
     */
    public void checkAgainst(SetIndex spec) {
        if (rolesInSpawnOrder == null) {
            throw new IllegalStateException("the online check needs the exporting constructor (roles)");
        }
        this.spec = Objects.requireNonNull(spec);
    }

    /** The first uncovered summary, if the online check found one. */
    public Optional<Summary> uncovered() {
        return Optional.ofNullable(uncovered);
    }

    /** The (1-based) iteration whose graph gave {@link #uncovered()}; 0 for none. */
    public int uncoveredIteration() {
        return uncoveredIteration;
    }

    /** The index's answer for {@link #uncovered()} ({@code NoSig} or {@code NotFound}). */
    public Optional<SetIndex.Covers> uncoveredAnswer() {
        return Optional.ofNullable(uncoveredAnswer);
    }

    /** Queries made against the specification (TraceForge's {@code lookups}). */
    public long lookups() {
        return lookups;
    }

    /**
     * Queries answered {@code NoSig}: no held summary has the signature (TraceForge's
     * {@code lookups_signature_miss}).
     */
    public long signatureMisses() {
        return signatureMisses;
    }

    /** Queries answered {@code Found} ({@code lookups_succeeded}). */
    public long found() {
        return found;
    }

    /**
     * Queries answered {@code NotFound}: the signature exists but no held summary is covered
     * (TraceForge's {@code lookups_failed_after_tests}).
     */
    public long notFound() {
        return notFound;
    }

    /** Distinct complete (non-blocked) graphs so far, by {@code GraphCanon}. */
    public int distinctCompleteGraphs() {
        return completeGraphs.size();
    }

    /** Distinct blocked graphs so far. */
    public int distinctBlockedGraphs() {
        return blockedGraphs.size();
    }

    /** Iterations that ran (consistent alternatives explored). */
    public int iterations() {
        return iterations;
    }

    /** Summaries with multiplicities (exporting constructor only). */
    public Map<Summary, Integer> summaries() {
        return Collections.unmodifiableMap(summaries);
    }

    /** Iterations summarised. */
    public int exportedIterations() {
        return exportedIterations;
    }

    /** Iterations not summarised. */
    public int skippedIterations() {
        return skippedIterations;
    }

    /**
     * The strategy's measurements: phase times and graph-shape counters over the explored graphs.
     * Times are cumulative wall-clock on the checker's thread, in nanoseconds.
     *
     * @param summariseNanos time turning graphs into summaries, failed attempts included
     * @param lookupNanos time in specification lookups
     * @param maxGraphEvents the most events (every label) in one explored graph
     * @param meanGraphEvents the mean events per explored graph
     * @param maxMessageEvents the most send and receive events (all threads) in one explored graph
     * @param maxVisibleEvents the most send and receive events of visible roles in one summarised
     *     graph (0 without export)
     * @param meanVisibleEvents the mean of those per summarised graph
     * @param maxSummaryBytes the largest summary line without its multiplicity, in UTF-8 bytes (0
     *     without export)
     * @param maxPorfCount the largest single order-count entry of any summary (0 without export)
     * @param maxPorfPairs the largest number of non-zero order-count entries in one summary
     * @param firstMissWallMs wall time from the start of the exploration to the first uncovered
     *     summary, or -1 for none
     */
    public record Measurements(long summariseNanos, long lookupNanos, int maxGraphEvents, double meanGraphEvents,
                               int maxMessageEvents, int maxVisibleEvents, double meanVisibleEvents,
                               int maxSummaryBytes, int maxPorfCount, int maxPorfPairs, long firstMissWallMs) {}

    /** The measurements so far. */
    public Measurements measurements() {
        return new Measurements(summariseNanos, lookupNanos, maxGraphEvents,
                measuredGraphs == 0 ? 0.0 : (double) sumGraphEvents / measuredGraphs, maxMessageEvents,
                maxVisibleEvents, summarisedGraphs == 0 ? 0.0 : (double) sumVisibleEvents / summarisedGraphs,
                maxSummaryBytes, maxPorfCount, maxPorfPairs, firstMissNanos < 0 ? -1 : firstMissNanos / 1_000_000);
    }

    /** The current graph as an {@code ExecutionGraph} — for tests. */
    public ExecutionGraph currentGraph() {
        return graph.toExecutionGraph();
    }

    /** A parameter that an event may lack entirely (events built without parameters have none). */
    private static Object optParam(JmcRuntimeEvent event, String key) {
        Map<String, Object> ps = event.getParams();
        return ps == null ? null : ps.get(key);
    }
}
