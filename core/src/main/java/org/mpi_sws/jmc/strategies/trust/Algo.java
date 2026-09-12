package org.mpi_sws.jmc.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.scheduling.ObjectValue;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.solver.ProverState;
import org.mpi_sws.jmc.solver.SMTSolverTypes;
import org.mpi_sws.jmc.solver.SolverResult;
import org.mpi_sws.jmc.solver.SolverUtil;
import org.mpi_sws.jmc.solver.incremental.IncrementalSolver;
import org.mpi_sws.jmc.strategies.ValueTracker;
import org.mpi_sws.jmc.util.FileUtil;

import java.util.*;

/**
 * Contains the core Trust algorithm implementation. (<a
 * href="https://doi.org/10.1145/3498711"></a>) We implement the recursive version described in the
 * paper and in the thesis. (<a
 * href="https://kluedo.ub.rptu.de/frontdoor/index/index/docId/7670"></a>)
 *
 * <p>This class contains, in addition to the execution graph and the algorithm, the auxiliary state
 * needed to enforce a specific task scheduling order.
 *
 * <p>Disclaimer!! This algorithm assumes that the programs are deterministic. Meaning, if you run a
 * task, you will receive the same sequence of events in that task.
 */
public class Algo {
    private static final Logger LOGGER = LogManager.getLogger(Algo.class);
    /**
     * The schedule of choices that guides the program's re-execution so its run aligns with the
     * graph currently being explored (consumed by {@link #nextTask()} during guiding); derived from
     * {@link #executionGraph}.
     */
    private ArrayDeque<SchedulingChoiceWrapper> guidingTaskSchedule;
    /** The execution graph being built/explored this iteration. */
    private ExecutionGraph executionGraph;
    /** True while replaying a guiding schedule so the program reaches a revisited graph's state. */
    private boolean isGuiding;
    /** The recursion tree of pending forward/backward revisits. */
    private final ExplorationStack explorationStack;
    /** Maps location hash codes across iterations so accesses alias consistently. */
    private final LocationStore locationStore;
    /** Optional diagnostic renderer of the exploration tree ({@code null} when disabled). */
    private final TreeLogger tLogger;
    /** Running count of blocked graphs encountered (diagnostics). */
    private long numOfBlockedGraphs = 0L;
    /**
     * Values to return to the program keyed by task id: stored by {@link #storeExternalValue} (the
     * boolean outcome of a symbolic event) and attached to the task's scheduling choice by {@link
     * #updateExternalValue}. Currently only constraint-evaluation (symbolic) events register values.
     */
    private ValueTracker externalValueTracker;
    /**
     * Values kept internal to the model-checking procedure keyed by task id: used during guiding to
     * carry a symbolic choice's {@link SolverResult} from {@link #nextTask} (via {@link
     * #storeInternalValue}) to {@link #handleGuidedSymEvent}, which reconstructs the branch.
     */
    private ValueTracker internalValueTracker;
    /**
     * The incremental SMT solver used for symbolic (ConDpor) reasoning, or {@code null} when the
     * concolic extension is disabled.
     */
    private final IncrementalSolver solver;

    /**
     * Creates a Trust algorithm instance with the concolic extension and tree logger disabled.
     */
    public Algo() {
        this.guidingTaskSchedule = null;
        this.isGuiding = false;
        this.executionGraph = new ExecutionGraph();
        // Initialize exploration stack with an empty inner stack with the initialized execution graph and prover id 1
        this.explorationStack = new ExplorationStack(this.executionGraph);
        this.locationStore = new LocationStore();
        this.executionGraph.addEvent(Event.init());
        this.externalValueTracker = new ValueTracker();
        this.internalValueTracker = new ValueTracker();
        this.tLogger = null;
        this.solver = null;
    }

    /**
     * Creates a Trust algorithm instance, optionally enabling the exploration-tree logger and the
     * concolic (ConDpor) SMT solver.
     *
     * @param hasTreeLogger whether to record the exploration tree.
     * @param solverType the solver name (e.g. {@code "z3"}), or {@code "off"} to disable symbolic
     *     reasoning.
     */
    public Algo(boolean hasTreeLogger, String solverType) {
        this.guidingTaskSchedule = null;
        this.isGuiding = false;
        this.executionGraph = new ExecutionGraph();
        this.explorationStack = new ExplorationStack();
        this.locationStore = new LocationStore();
        this.executionGraph.addEvent(Event.init());
        this.externalValueTracker = new ValueTracker();
        this.internalValueTracker = new ValueTracker();
        if (hasTreeLogger) {
            this.tLogger = new TreeLogger();
        } else {
            this.tLogger = null;
        }
        this.solver = initSolver(solverType);
    }

    /**
     * Creates the incremental SMT solver for the given solver name (ConDpor), or {@code null} when
     * it is {@code "off"}. Documented in full with the ConDpor material.
     *
     * @param solverType the solver name.
     * @return the incremental solver, or {@code null} if disabled.
     */
    private IncrementalSolver initSolver(String solverType) {
        SMTSolverTypes type = getSolverType(solverType);
        if (type == SMTSolverTypes.OFF) {
            return null;
        }
        return SolverUtil.createIncrementalSolver(type);
    }

    /**
     * Maps a solver name to its {@link SMTSolverTypes} constant (ConDpor), defaulting to Z3 for an
     * unknown name and {@code OFF} for {@code null}.
     *
     * @param solverType the solver name.
     * @return the matching solver type.
     */
    private SMTSolverTypes getSolverType(String solverType) {
        if (solverType == null) {
            LOGGER.warn("No solver type specified. Thus, the solver will be turned off.");
            return SMTSolverTypes.OFF;
        }
        return switch (solverType.toLowerCase()) {
            case "z3" -> SMTSolverTypes.Z3;
            case "cvc5" -> SMTSolverTypes.CVC5;
            case "cvc4" -> SMTSolverTypes.CVC4;
            case "mathsat5" -> SMTSolverTypes.MATHSAT5;
            case "yices2" -> SMTSolverTypes.YICES2;
            case "opensmt" -> SMTSolverTypes.OPENSMT;
            case "smtinterpol" -> SMTSolverTypes.SMTINTERPOL;
            case "princess" -> SMTSolverTypes.PRINCESS;
            case "boolector" -> SMTSolverTypes.BOOLECTOR;
            case "off" -> SMTSolverTypes.OFF;
            default -> {
                LOGGER.warn("Unknown solver type: {}. Defaulting to Z3.", solverType);
                yield SMTSolverTypes.Z3;
            }
        };
    }

    /**
     * Returns the next scheduling choice dictated by the guiding schedule, or {@code null} when the
     * algorithm is not guiding (leaving the choice to {@link TrustStrategy}'s fallback policy).
     *
     * @return the next guiding choice, or {@code null} if not guiding.
     */
    public SchedulingChoice<?> nextTask() {

        // If we are not in guiding mode, then there is no constraint from Trust over the next candidate
        if (!isGuiding) {
            return null;
        }
        if (guidingTaskSchedule == null || guidingTaskSchedule.isEmpty()) {
            return null;
        }
        // Otherwise, the next task must be the tail of trace sequence which Trust is going to explore its extension.
        SchedulingChoice<?> out = guidingTaskSchedule.pop().choice();
        if (guidingTaskSchedule.isEmpty()) {
            LOGGER.debug("End of guiding phase");
            isGuiding = false;
        }

        // Check for symbolic choice
        if (out.isSymbolic()) {
            SolverResult solverResult = extractSolverResult(out);
            // The task id in algo must be adjusted
            storeInternalValue(out.getTaskId() - 1, solverResult);
        }
        return out;
    }

    /**
     * Extracts the {@link SolverResult} carried by a symbolic scheduling choice (ConDpor).
     *
     * @param choice a symbolic scheduling choice.
     * @return the solver result it carries.
     */
    private SolverResult extractSolverResult(SchedulingChoice<?> choice) {
        Object value = choice.getValue();
        if (value instanceof ObjectValue objectValue
                && objectValue.asObject() instanceof SolverResult solverResult) {
            return solverResult;
        }
        throw new RuntimeException("Expected symbolic scheduling choice to carry a SolverResult");
    }

    /**
     * This method updates the value of a give scheduling choice according to the value tracker
     *
     * @param task scheduling choice to update its value
     */
    public void updateExternalValue(SchedulingChoice<?> task) {
        // Since the task we receive is from runtime, the task id must be treated adjusted in algo
        long id = task.getTaskId() - 1;
        if (externalValueTracker.containsValue(id)) {
            Object value = externalValueTracker.getValue(id);
            LOGGER.debug("Updating external value {} for task: {}", value, id);
            externalValueTracker.removeValue(id);

            PrimitiveValue val = new  PrimitiveValue(value);
            task.setValue(val);
        }
    }

    /**
     * Consumes the next guiding-schedule entry as the program replays an event during guiding:
     * enforces block/end markers, re-maps the event's location onto the schedule's expected location
     * (via {@link LocationStore} aliases), and delivers symbolic results.
     *
     * @param event the event just replayed by the program.
     */
    private void handleGuidedEvent(Event event) {
        if (EventUtils.isLockAcquired(event)) {
            // Ignore lock acquired events in the guiding trace
            // These are not added to the execution graph and does not bear any consequence
            // on what occurs below.

            // The lock acquired event is also not a yielding event therefore the real event will
            // follow.
            return;
        }
        SchedulingChoiceWrapper choiceW = guidingTaskSchedule.peek();
        SchedulingChoice<?> choice = choiceW.choice();
        if (choice.isBlockTask()) {
            throw HaltTaskException.blocked(choice.getTaskId());
        } else if (choice.isBlockExecution()) {
            throw HaltExecutionException.error("Encountered a block label");
        } else if (choice.isEnd() && !EventUtils.isExclusiveRead(event)) {
            // We have observed all the events in the guiding trace, pop the end event
            // Unless it is an exclusive read, then we expect a matching exclusive write
            guidingTaskSchedule.pop();
            if (guidingTaskSchedule.isEmpty()) {
                isGuiding = false;
                LOGGER.debug("The guiding task schedule is empty");
            }
        }
        if (choiceW.hasLocation()) {
            Integer location = choiceW.location();
            if (event.getLocation() == null) {
                throw HaltExecutionException.error(
                        "Expected location with event but it contains none");
            }
            if (Objects.equals(event.getLocation(), location)) {
                return;
            }
            if (!locationStore.containsAlias(event.getLocation())) {
                locationStore.addAlias(location, event.getLocation());
            }
        }

        if (event.isSymbolic()) {
            handleGuidedSymEvent(event);
        }
    }

    /**
     * Stores a value to be handed back to the runtime (via a scheduling choice) for the given task.
     *
     * @param id the (0-indexed) task id.
     * @param value the value to deliver.
     */
    private void storeExternalValue(long id, Object value) {
        if (externalValueTracker.containsValue(id)) {
            LOGGER.error("Value for id {} already exists in external tracker. This should not happen.", id);
            throw new RuntimeException("Value for id " + id + " already exists");
        }

        LOGGER.debug("Storing value {} for id {}", value, id);
        externalValueTracker.setValue(id, value);
    }

    /**
     * Stores a value the algorithm keeps for its own guiding decisions for the given task.
     *
     * @param id the (0-indexed) task id.
     * @param value the value to store.
     */
    private void storeInternalValue(long id, Object value) {
        if (internalValueTracker.containsValue(id)) {
            throw new RuntimeException("Value for id " + id + " already exists");
        }

        internalValueTracker.setValue(id, value);
    }

    /**
     * Replays a symbolic event during guided re-execution (ConDpor).
     *
     * <p>The branch outcome for this event was decided when the revisit was scheduled and stored as a
     * {@link SolverResult} in the internal value tracker; this method reapplies it — setting the
     * event's {@code result}/{@code isNegatable} attributes, asserting the (possibly negated)
     * constraint on a fresh prover, and delivering the result back to the runtime — so the
     * re-execution follows exactly the same symbolic branch.
     *
     * @param event the replayed symbolic event.
     */
    private void handleGuidedSymEvent(Event event) {
        long taskId = event.getTaskId();
        if (!internalValueTracker.containsValue(taskId)) {
            throw HaltExecutionException.error(
                    "Missing guided symbolic result for task " + taskId);
        }
        Object rawValue = internalValueTracker.getValue(taskId);
        // clear the entry related to taskId in tracker
        internalValueTracker.removeValue(taskId);
        if (!(rawValue instanceof SolverResult solverResult)) {
            throw HaltExecutionException.error(
                    "Expected SolverResult for guided symbolic event on task " + taskId);
        }

        event.setAttribute("result", solverResult.result());
        event.setAttribute("isNegatable", solverResult.isNegatable());

        if (solver.isFreshProver()) {
            JmcBooleanFormula formula = event.getAttribute("booleanFormula");
            if (solverResult.result()) {
                solver.addFormula(formula);
            } else {
                solver.addNegatedFormula(formula);
            }
        }
        // Store the value in tracker
        storeExternalValue(event.getKey().getTaskId(), solverResult.result());
    }

    /**
     * Records the task schedule generated by the execution graph to the specified filePath.
     *
     * @param filePath to record the task schedule in.
     */
    public void recordTaskSchedule(String filePath) throws JmcCheckerException {
        List<SchedulingChoiceWrapper> taskSchedule =
                ExecutionGraph.getTaskSchedule(executionGraph.checkConsistency());
        FileUtil.storeTaskSchedule(
                filePath, taskSchedule.stream().map(SchedulingChoiceWrapper::choice).toList());
    }

    /**
     * Handles the visit with this event. Equivalent of running a single loop iteration of the Visit
     * method of the algorithm.
     *
     * <p>We assume that the updateEvent call is followed immediately by a yield call. Therefore, we
     * check the top of a guiding trace and raise exception if the scheduling choice is blocking.
     *
     * @param event A {@link Event} that is used to update the execution graph.
     */
    @SuppressWarnings({"checkstyle:MissingSwitchDefault", "checkstyle:LeftCurly"})
    public void updateEvent(Event event) throws HaltTaskException, HaltExecutionException {
        LOGGER.debug("Received event: {}", event);
        if (areWeGuiding()) {
            handleGuidedEvent(event);
            return;
        }

        // Need to assign the right location value to the event. Check aliases and update the event
        // location accordingly.
        if (event.getLocation() != null) {
            if (locationStore.containsAlias(event.getLocation())) {
                event.setLocation(locationStore.getAlias(event.getLocation()));
            } else {
                locationStore.addLocation(event.getLocation());
            }
        }

        switch (event.getType()) {
            case END:
                handleBot(event);
                break;
            case SYMBOLIC:
                handleSymbolic(event);
                break;
            case READ:
                handleRead(event);
                break;
            case WRITE:
                if (EventUtils.isLockReleaseWrite(event)) {
                    handleLockReleaseWrite(event);
                } else {
                    handleWrite(event);
                }
                break;
            case READ_EX:
                if (EventUtils.isLockAcquireRead(event)) {
                    handleLockAcquireRead(event);
                } else {
                    handleRead(event);
                }
                break;
            case WRITE_EX:
                if (EventUtils.isLockAcquireWrite(event)) {
                    handleLockAcquireWrite(event);
                } else {
                    handleWriteX(event);
                }
                break;
            case NOOP:
                if (areWeGuiding()) {
                    return;
                }
                handleNoop(event);
                break;
            case ASSUME:
                handleAssume(event);
        }
        LOGGER.debug("Handled event: {}", event);
    }

    /**
     * Delegates to {@link ExecutionGraph#checkCoherencyEdges()} on the current graph (debugging aid).
     *
     * @return true if the coherence edges are well-formed.
     */
    public boolean checkCoherencyEdges() {
        return executionGraph.checkCoherencyEdges();
    }

    /**
     * Handles the NOOP event. This is a special case where we do not need to update the execution
     * graph.
     *
     * @param event The NOOP event.
     */
    public void handleNoop(Event event) {
        if (EventUtils.isLockAcquired(event)) {
            handleLockAcquired(event);
            return;
        }
        ExecutionGraphNode eventNode = this.executionGraph.addEvent(event);
        // Maintain total order among thread start events
        if (EventUtils.isThreadStart(event)) {
            this.executionGraph.trackThreadCreates(eventNode);
            if (event.getTaskId() != 0) { // Skip the main thread
                this.executionGraph.trackThreadStarts(eventNode);
            }
        } else if (EventUtils.isThreadJoin(event)) {
            this.executionGraph.trackThreadJoins(eventNode);
            this.executionGraph.trackThreadJoinCompletion(eventNode);
        }
    }

    /**
     * Handles a symbolic (ConDpor) event: evaluates its branch via the solver and delivers the
     * result to the runtime. Documented in full with the ConDpor material.
     *
     * @param event the symbolic event.
     */
    public void handleSymbolic(Event event) {
        LOGGER.debug("Handling symbolic event: {}", event);
        boolean result = processNewSymEvent(event);
        storeExternalValue(event.getKey().getTaskId(), result);
    }

    /**
     * Adds a new constraint-evaluation (ConDpor) event to the graph and decides its branch.
     *
     * <p>The solver's {@code computeNewSymbolicOperation} returns the branch taken now (from the
     * concrete model, no SMT call) and whether the other branch is also feasible; these are recorded
     * on the event as {@code result}/{@code isNegatable}. If the other branch is feasible, an {@code
     * FSYMB} item is pushed so it is explored in a later iteration.
     *
     * @param event the symbolic event.
     * @return the boolean outcome of the taken branch.
     */
    private boolean processNewSymEvent(Event event) {
        JmcBooleanFormula symbolicOperation = event.getAttribute("booleanFormula");
        SolverResult solverResult = solver.computeNewSymbolicOperation(symbolicOperation);

        event.setAttribute("result", solverResult.result());
        event.setAttribute("isNegatable", solverResult.isNegatable());

        ExecutionGraphNode symb = this.executionGraph.addEvent(event);
        if (solverResult.isNegatable()) {
            LOGGER.debug("Pushing the fWR item to explore the other outcome of symbolic event {}", event.key());
            explorationStack.push(
                    ExplorationStack.Item.symbolicForwardRevisit(
                            symb,
                            this.executionGraph));
        }
        return solverResult.result();
    }

    /**
     * Prepares an iteration. Iteration 0 runs the program freely; later iterations pop the next
     * pending revisit and build a guiding schedule for it (via {@link #findNextExplorationChoice()}),
     * or signal completion ({@code HaltCheckerException.ok()}) when the exploration stack is empty.
     *
     * @param iteration The iteration number.
     * @param report The model-checker report (unused here; kept for interface symmetry).
     */
    public void initIteration(int iteration, JmcModelCheckerReport report) {
        // Check if we are guiding the execution and construct the task schedule accordingly!
        if (iteration == 0) {
            LOGGER.debug("Initializing iteration");
            return;
        }

        if (executionGraph.isBlocked()) {
            logBlockedGraph();
        }

        // Check if the exploration stack is empty. If so, we are done with the exploration.
        if (explorationStack.isEmpty()) {
            LOGGER.debug("Exploration stack is empty. We are done with the exploration.");
            // We have reached the end of the exploration stack.
            // We should not be guiding the execution
            throw HaltCheckerException.ok();
        }

        // Clear location aliases. By this point, we have replaced all the locations in the
        // execution graph with the latest ones.
        // TODO: need to check this properly
        locationStore.clearAliases();

        // We are guiding
        isGuiding = true;

        LOGGER.debug("Initializing the {}th iteration", iteration);
        findNextExplorationChoice();
    }

    /**
     * Checks if we are guiding the execution.
     *
     * @return true if a non-empty guiding schedule is being replayed.
     */
    public boolean areWeGuiding() {
        return isGuiding && guidingTaskSchedule != null && !guidingTaskSchedule.isEmpty();
    }

    /**
     * Pops exploration-stack items until one yields a consistent graph, materializes it (applying
     * backward/forward revisits and prover changes), and turns it into the {@link
     * #guidingTaskSchedule}. Skips revisits that produce inconsistent graphs; signals completion when
     * the stack empties.
     */
    private void findNextExplorationChoice() {
        if (explorationStack.isEmpty()) {
            // This must not happen. We should have handled this in the resetIteration method.
            throw new RuntimeException( // TODO : We need to define a better exception for this
                    // case.
                    "Exploration stack is empty. We should have handled this in the resetIteration method.");
        }

        // The main loop of the procedure
        List<ExecutionGraphNode> nextGraphSchedule = new ArrayList<>();
        while (nextGraphSchedule.isEmpty()) {

            if (explorationStack.isEmpty()) {
                // We have reached the end of the exploration stack.
                throw HaltCheckerException.ok();
            }

            // Get the next exploration choice from the exploration stack.
            ExplorationStack.Item item = explorationStack.pop();
            logUpdateGraphId(item);
            // Read the size of the exploration stack
            int stackSize = explorationStack.totalSize();
            // Check if the item is a backward revisit.
            if (item.isBackwardRevisit()) { // TODO : Is any backward revisit type allowed? or only
                // BWR?
                processBWR(item);
                continue;
            }

            if (item.isRemoveProver()) {
                processRMP(item);
                continue;
            }

            // Handle the forward revisit
            ExecutionGraph newGraph = item.getGraph();
            if (newGraph == null) {
                // It is not possible for an item to have a null graph. This must be a bug in the
                // exploration stack.
                throw new RuntimeException( // TODO : We need to define a better exception for this
                        // case.
                        "The exploration stack item has a null graph. This must be a bug in the exploration stack.");
            } else {
                executionGraph = newGraph;
            }

            // Update solver
            updateSolver();

            switch (item.getType()) {
                case FRW -> nextGraphSchedule = processFRW(item);
                case FWW -> nextGraphSchedule = processFWW(item);
                case FLW -> nextGraphSchedule = processFLW(item);
                case CONT -> nextGraphSchedule = processCont(item);
                case FSYMB -> nextGraphSchedule = processFSYMB(item);
                default -> throw new RuntimeException(
                        "The exploration stack item has an invalid type. This must be a bug in the exploration stack.");
            }

            if (nextGraphSchedule.isEmpty()) {
                if (EventUtils.isLockAcquireRead(item.getEvent1().getEvent())) {
                    int newStackSize = explorationStack.totalSize();
                    if (newStackSize == stackSize) {
                        LOGGER.debug(
                                "The forward revisit of lock acquire read resulted in an inconsistent graph. Continuing to next item.");
                        logInconsistentGraph();
                        item.getGraph().setConsistent(false);
                    }
                } else {
                    LOGGER.debug("The revisit resulted in an inconsistent graph. Continuing to next item.");
                    logInconsistentGraph();
                    item.getGraph().setConsistent(false);
                }
            }
        }

        LOGGER.debug("Found the SC graph");
        checkGraphSchedule(nextGraphSchedule);
        //executionGraph.printGraph();

        // The SC graph is found. We need to set the guiding task schedule.
        // TODO : To increase efficiency, we can use the topological sort which
        guidingTaskSchedule = new ArrayDeque<>(ExecutionGraph.getTaskSchedule(nextGraphSchedule));
        printGuidingTaskSchedule();
    }

    /**
     * Points the SMT solver at the prover context of the current exploration level and sets its
     * fresh-prover flag (ConDpor); a no-op when no solver is configured.
     */
    private void updateSolver() {
        if (solver != null) {
            // Update the solver with the corresponding prover
            int newProverId = explorationStack.getProverId();
            solver.setProverById(newProverId);

            // We must check if the solver is fresh or not.
            // If the solver is empty, we need to set the fresh prover flag to true ( This is a new prover)
            // This is needed for guiding the execution.
            if (solver.size() == 0) {
                solver.setFreshProverFlag(true);
            } else {
                solver.setFreshProverFlag(false);
            }
        }
    }

    /**
     * Sanity-checks a graph schedule before it becomes the guiding schedule: logs it and verifies no
     * task appears after its own thread-finish event.
     *
     * @param graphSchedule the topologically sorted graph to validate.
     */
    private void checkGraphSchedule(List<ExecutionGraphNode> graphSchedule) {
        // Print
        if (graphSchedule == null || graphSchedule.isEmpty()) {
            LOGGER.debug("Graph schedule is empty");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph schedule: ");
        for (ExecutionGraphNode node : graphSchedule) {
            sb.append(node.getEvent().key().toString())
                    .append(" - ")
                    .append(node.getEvent().toVerboseString())
                    .append("\n");
        }
        LOGGER.debug(sb.toString());

        // Check if the graph schedule is consistent
        Set<Long> completedTasks = new HashSet<>();
        for (ExecutionGraphNode node : graphSchedule) {
            if (EventUtils.isThreadFinish(node.getEvent())) {
                completedTasks.add(node.getEvent().getTaskId());
                continue;
            }
            Long taskId = node.getEvent().getTaskId();
            if (completedTasks.contains(taskId)) {
                throw HaltCheckerException.error(
                        "The graph schedule is inconsistent. Task "
                                + taskId
                                + " has already completed but it appears again in the schedule.");
            }
        }
    }

    /**
     * Prints the current guiding task schedule to the debug log. This is useful for debugging
     * purposes to see the order of tasks in the guiding schedule.
     */
    private void printGuidingTaskSchedule() {
        if (guidingTaskSchedule == null) {
            LOGGER.debug("Guiding task schedule is null");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Guiding task schedule:");
        for (SchedulingChoiceWrapper choice : guidingTaskSchedule) {
            sb.append(choice.choice().getTaskId()).append(" - ");
        }
        LOGGER.debug(sb.toString());
    }

    /**
     * Processes a "remove prover" item by discarding the current level's SMT prover context
     * (ConDpor). Documented in full with the ConDpor material.
     *
     * @param item the remove-prover item.
     */
    public void processRMP(ExplorationStack.Item item) {
        int id = explorationStack.getProverId();
        if ( id < 0 ) {
            throw new RuntimeException("No prover can exist with id 0");
        }
        solver.removeProver(id);
    }

    /**
     * Materializes a backward-revisit item: on its restricted graph, pushes the write's alternative
     * coherence placements ({@code FWW}) and finally its coherence-maximal placement ({@code FLW},
     * carrying any deferred additional events), so the sub-exploration proceeds depth-first.
     *
     * <p>Under ConDpor it also opens a fresh SMT prover context for the sub-exploration
     * ({@link #createNewProver()}) and schedules its teardown ({@link #addRemoveProverItem(int)});
     * these are no-ops when the solver is disabled.
     *
     * @param item the backward-revisit item.
     */
    public void processBWR(ExplorationStack.Item item) {

        int newProverId = createNewProver();
        addRemoveProverItem(newProverId);

        ExecutionGraphNode write = item.getEvent1();
        ExecutionGraph restrictedGraph = item.getGraph();

        List<ExecutionGraphNode> alternativeWrites = restrictedGraph.getCoherentPlacings(write);

        logNewBranchs();
        if (!alternativeWrites.isEmpty()) {
            for (int i = alternativeWrites.size() - 1; i >= 0; i--) {
                ExplorationStack.Item newItem = ExplorationStack.Item.forwardWW(
                        write, alternativeWrites.get(i), restrictedGraph);
                explorationStack.push(newItem);
                logNewChild(newItem);
            }
        }

        ExplorationStack.Item forwardLW = ExplorationStack.Item.forwardLW(write, restrictedGraph);
        for (Event additionalEvent : item.getAdditionalEventsToProcess()) {
            forwardLW.addAdditionalEvent(additionalEvent);
        }
        explorationStack.push(forwardLW);
        logLastChild(forwardLW);
    }

    /**
     * Opens a new SMT prover context cloned from the current one, registers it, and records it on the
     * current exploration level (ConDpor); returns {@code -1} when no solver is configured.
     *
     * @return the new prover id, or {@code -1} if no solver.
     */
    private int createNewProver() {
        // If no solver is configured, ignore.
        if (solver == null) {
            return -1;
        }
        // Create a new prover
        ProverState newProver = solver.createNewProver();
        // Update prover's model with current prover state
        solver.cloneCurrentProverState(newProver);
        // Register the new prover
        int newProverId = solver.registerNewProver(newProver);
        // update the solver with the new prover
        solver.setProverById(newProverId);
        // Update the current inner_stack state
        explorationStack.setProverId(newProverId);
        return newProverId;
    }

    /**
     * Pushes a "remove prover" item so the newly created prover context is torn down when the
     * sub-exploration finishes (ConDpor); a no-op when no solver is configured.
     *
     * @param proverId the prover id that will be removed.
     */
    private void addRemoveProverItem(int proverId) {
        if (solver == null || proverId < 0) {
            return;
        }

        ExplorationStack.Item removeProverItem = ExplorationStack.Item.removeProver();
        explorationStack.push(removeProverItem);
    }

    /**
     * Rolls the solver's stack back by the number of symbolic events removed by a restrict (ConDpor);
     * a no-op when there were none. Documented in full with the ConDpor material.
     *
     * @param restrictView the restrict result carrying the removed-symbolic-event count.
     */
    private void restrictSolverStack(GraphRestrictView restrictView) {
        // We need to check if the solver object exists, if there are any symbolic events,
        // and if the solver is not fresh.
        if (solver != null && restrictView != null &&
                restrictView.getNumOfSymEvents() > 0 && !solver.isFreshProver()) {
            solver.restrictSolverStack(restrictView.getNumOfSymEvents());
        }
    }

    /**
     * Rolls the solver's stack back by the given number of symbolic events (ConDpor). Documented in
     * full with the ConDpor material.
     *
     * @param numOfSymbEvents the number of symbolic events to pop.
     */
    public void restrictSolverStack(int numOfSymbEvents) {
        // We need to check if the solver object exists, if there are any symbolic events,
        // and if the solver is not fresh.
        if (solver != null && numOfSymbEvents > 0 && !solver.isFreshProver()) {
            solver.restrictSolverStack(numOfSymbEvents);
        }
    }

    /**
     * Processes a symbolic forward-revisit ({@code FSYMB}) item — the second branch of a
     * constraint-evaluation event (ConDpor).
     *
     * <p>It restricts the graph back to the symbolic node (rolling the solver stack back by the
     * removed symbolic events), **negates** the event's recorded result, pops the old constraint and
     * pushes the negated one on the solver, refreshes the model (`solveAndUpdateModel`), and returns
     * the re-checked consistent schedule.
     *
     * @param item the symbolic forward-revisit item.
     * @return the topological order of the resulting graph, or empty if inconsistent.
     */
    private List<ExecutionGraphNode> processFSYMB(ExplorationStack.Item item) {
        ExecutionGraphNode node = item.getEvent1();

        LOGGER.debug("Processing symbolic forward revisit of s {} to explore the other outcome", node.key());

        GraphRestrictView view = executionGraph.restrict(node);
        restrictSolverStack(view);

        Event s = node.getEvent();
        if (!s.isSymbolic()) {
            throw new RuntimeException("The event is not a symbolic operation");
        }

        // Negate the result of the symbolic operation
        boolean result = s.getAttribute("result");
        s.setAttribute("result", !result);

        if (!solver.isFreshProver()) {
            // To pop the formula(event) from the solver
            restrictSolverStack(1);
            JmcBooleanFormula formula = s.getAttribute("booleanFormula");
            boolean newResult = s.getAttribute("result");
            if (newResult) {
                solver.addFormula(formula);
            } else {
                solver.addNegatedFormula(formula);
            }
            solver.solveAndUpdateModel();
        }
        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    /**
     * Processes a forward read revisit ({@code FRW}): re-points the read to the alternative write,
     * restricts the graph to that point, recomputes clocks, re-adds any deferred events, and returns
     * the new consistent schedule. For a lock-acquire read the (intentionally inconsistent)
     * consistency check is skipped.
     *
     * @param item the forward read-revisit item.
     * @return the topological order of the resulting graph, or empty if inconsistent.
     */
    private List<ExecutionGraphNode> processFRW(ExplorationStack.Item item) {
        // Forward revisit of w -> r
        ExecutionGraphNode read = item.getEvent1();
        ExecutionGraphNode write = item.getEvent2();

        LOGGER.debug("Processing forward revisit of w {} -> r {}", write.key(), read.key());

        executionGraph.changeReadsFrom(read, write);
        GraphRestrictView restrictView = executionGraph.restrict(read);
        restrictSolverStack(restrictView);
        executionGraph.recomputeVectorClocks();

        for (Event additionalEvent : item.getAdditionalEventsToProcess()) {
            processAdditionalEvent(additionalEvent);
        }

        // The following is an optimization to avoid doing unnecessary consistency checks. If the read event is
        // a lock acquire read, we know that the graph is not consistent because the resulted graph has two
        // lock acquire reads reading from the same lock write.
        if (EventUtils.isLockAcquireRead(item.getEvent1().getEvent())) {
            LOGGER.debug("Skipping consistency check for lock acquire read forward revisit");
            return new ArrayList<>();
        }
        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    /**
     * Re-adds a deferred additional event after a revisit — the exclusive read/write of a lock whose
     * pairing was temporarily broken by the revisit — by re-invoking the appropriate lock handler.
     *
     * @param event the deferred lock event to re-process.
     */
    private void processAdditionalEvent(Event event) {
        switch (event.getType()) {
            case READ_EX -> {
                // The case of backward revisit of a lock acquire write to a lock acquire read
                // We would've removed the revisited read and are adding it again
                if (EventUtils.isLockAcquireRead(event)) {
                    handleLockAcquireRead(event);
                }
            }
            case WRITE_EX -> {
                // The case when a lock acquire read wants to read from a different lock
                // write (init, or lock release). Here we add the lock acquire write to the
                // graph explicitly.
                if (EventUtils.isLockAcquireWrite(event)) {
                    handleLockAcquireWrite(event);
                }
            }
        }
    }

    /**
     * Processes a forward write-write revisit ({@code FWW}): swaps the two writes' coherence
     * positions, restricts the graph, and returns the new consistent schedule.
     *
     * @param item the forward write-revisit item.
     * @return the topological order of the resulting graph, or empty if inconsistent.
     */
    private List<ExecutionGraphNode> processFWW(ExplorationStack.Item item) {
        // Forward revisit of w -> w (alternative coherence placing)
        ExecutionGraphNode write1 = item.getEvent1();
        ExecutionGraphNode write2 = item.getEvent2();

        LOGGER.debug("Processing forward revisit of w {} -> w {}", write1.key(), write2.key());

        executionGraph.swapCoherency(write1, write2);
        GraphRestrictView restrictView = executionGraph.restrict(write1);
        restrictSolverStack(restrictView);
        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    /**
     * Processes a coherence-maximal (lock) write placement ({@code FLW}): makes the write
     * coherence-maximal, restricts the graph, re-adds a deferred event if present, and returns the
     * new consistent schedule.
     *
     * @param item the coherence-maximal write item.
     * @return the topological order of the resulting graph, or empty if inconsistent.
     */
    public List<ExecutionGraphNode> processFLW(ExplorationStack.Item item) {
        // Forward revisit of w -> lw (max-co)
        ExecutionGraphNode w = item.getEvent1();

        LOGGER.debug("Processing forward revisit of w {} -> lw", w.key());
        // set the co
        executionGraph.trackCoherency(w);

        GraphRestrictView restrictView = executionGraph.restrict(w);
        restrictSolverStack(restrictView);

        List<Event> additionalEvents = item.getAdditionalEventsToProcess();
        if (additionalEvents.size() > 1) {
            throw HaltCheckerException.error(
                    "The forward revisit item has more than one additional event");
        }
        for (Event additionalEvent : additionalEvents) {
            processAdditionalEvent(additionalEvent);
        }

        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    /**
     * Processes a "continue current" item by simply returning the current graph's schedule.
     *
     * <p>Must not be used during normal Trust model checking; it exists for estimation, which does
     * not follow the DFS exploration order strictly.
     *
     * @param item the continue item.
     * @return the topological order of the current graph, or empty if inconsistent.
     */
    private List<ExecutionGraphNode> processCont(ExplorationStack.Item item) {
        // Just continue the exploration with the current graph
        return executionGraph.checkConsistencyAndTopologicallySort();
    }

    /**
     * Cleans up the execution graph, exploration stack, and location store at the end of a run, and
     * records the blocked-iteration count (and diagnostic logs) into the report.
     *
     * @param report the model-checker report to update.
     */
    public void teardown(JmcModelCheckerReport report) {
        // Clean up the execution graph and the task schedule.
        logLastGraphSize();
        this.executionGraph.clear();
        this.explorationStack.clear();
        this.locationStore.clearAliases();
        this.locationStore.clear();
        reportInconsistentGraphLogs();
        reportBlockedGraphLogs();
        report.setBlockedIterations(Math.toIntExact(numOfBlockedGraphs));
    }

    /**
     * Returns the (0-indexed) ids of tasks whose po-maximal event is not a blocking label, i.e.
     * where a new event may be added — the candidate set for {@link TrustStrategy}'s fallback policy.
     *
     * @return the schedulable task ids.
     */
    public List<Long> getSchedulableTasks() {
        // Get from execution graph
        return this.executionGraph.getUnblockedTasks().stream().map(Long::valueOf).toList();
    }

    /**
     * Handles an error event by halting the execution with the carried message.
     *
     * @param event the error event.
     */
    private void handleError(Event event) {
        // Error events, halt the current execution.
        if (event.getType() == Event.Type.ERROR) {
            String message = event.getAttribute("message");
            throw HaltExecutionException.error(message);
        }
    }

    /**
     * Handles the end-of-execution ("bottom") event by halting the execution successfully.
     *
     * @param event the end event.
     */
    private void handleBot(Event event) {
        // End of the execution
        // No-op for now
        throw HaltExecutionException.ok();
    }

    /**
     * Handles a read: makes it observe the coherence-maximal write and pushes forward read-revisit
     * items for every alternative write not porf-before it. Applies the SC shortcut when the co-max
     * write already happens-before the read.
     *
     * @param event the read event.
     */
    private void handleRead(Event event) {
        if (areWeGuiding()) {
            return;
        }
        ExecutionGraphNode read = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());

        // TODO: If coMaxWrite is init (reading from a possibly uninitialized location). Write
        //      warning if flag is set
        // if (coMaxWrite.getEvent().isInit()) {
        //
        // }

        // Need to handle lock acquire reads
        // If the read is reading from a write of a lock acquire then we need to add a lock await
        // label after the read to block the thread.

        if (coMaxWrite.happensBefore(read)) {
            // TODO :: For debugging
            LOGGER.debug("Read is before the coMaxWrite");
            LOGGER.debug("The coMaxWrite is " + coMaxWrite.getEvent());
            // Easy case. No concurrent write to revisit. [Note that this is an optimization for
            // sequential consistency model. If we are exploring relaxed memory models in the
            // future,
            // we need to remove this optimization.]
            executionGraph.setReadsFrom(read, coMaxWrite);
            return;
        }
        List<ExecutionGraphNode> alternativeWrites = executionGraph.getAlternativeWrites(read);

        // Set the reads-from relation
        executionGraph.setReadsFrom(read, coMaxWrite);

        if (alternativeWrites.isEmpty()) {
            LOGGER.debug("No alternative writes to revisit");
            // No alternative writes to revisit.
            return;
        }

        logNewBranchs();

        // We have alternative writes to revisit.
        for (int i = alternativeWrites.size() - 1; i >= 0; i--) {
            ExplorationStack.Item newItem =
                    ExplorationStack.Item.forwardRW(
                            read, alternativeWrites.get(i), this.executionGraph);
            explorationStack.push(newItem);
            logNewChild(newItem);
        }
        logConCurrChild();
        logUpdateGraphIdWithLastGraph();
    }

    /**
     * Handles a write: pushes forward write-write revisits for its alternative coherence placements,
     * then backward revisits of earlier reads that pass the maximal-extension check, and finally
     * makes the write coherence-maximal.
     *
     * @param event the write event.
     */
    private void handleWrite(Event event) {
        if (areWeGuiding()) {
            return;
        }

        // Add the write event to the execution graph
        ExecutionGraphNode write = executionGraph.addEvent(event);

        /* Check for (w->w) coherent forward revisits */
        List<ExecutionGraphNode> concurrentWrites = executionGraph.getCoherentPlacings(write);

        boolean hasForwardRevisits = false;

        if (!concurrentWrites.isEmpty()) {
            LOGGER.debug("Found concurrent writes to revisit");

            hasForwardRevisits = true;
            logNewBranchs();
            // We have concurrent writes to revisit.
            // If flag is set, write race warning
            for (int i = concurrentWrites.size() - 1; i >= 0; i--) {
                ExplorationStack.Item newItem =
                        ExplorationStack.Item.forwardWW(
                                write, concurrentWrites.get(i), executionGraph);
                explorationStack.push(newItem);
                logNewChild(newItem);
            }
        } else {
            LOGGER.debug("No concurrent writes to revisit");
        }

        /* Check for (w->r) backward revisits */
        // Find potential reads that need to be revisited
        // TODO :: I'm not sure the way `getPotentialReads` method is ordering the reads is correct.
        List<ExecutionGraphNode> potentialReads = executionGraph.getPotentialReads(write);
        if (potentialReads.isEmpty()) {
            LOGGER.debug("No potential reads to revisit");
            // After batching the forward revisits, since there is no backward revisit, we need to
            // continue the exploration by adding the recently added write as the CO max.
            executionGraph.trackCoherency(write);
            if (hasForwardRevisits) {
                logConCurrChild();
                logUpdateGraphIdWithLastGraph();
            }
            return;
        }
        LOGGER.debug("Found potential reads to revisit");

        List<BackwardRevisitView> revisitViews =
                potentialReads.stream().map((r) -> executionGraph.revisitView(write, r)).toList();

        revisitViews =
                revisitViews.stream().filter(BackwardRevisitView::isMaximalExtension).toList();

        // If symbolic exploration is enabled, maximality condition must be checked for symbolic events
        if (solver != null) {
            revisitViews =
                    revisitViews.stream().filter(BackwardRevisitView::isMaximalSymbolicExtension).toList();
        }

        boolean hasBackwardRevisits = false;
        if (!revisitViews.isEmpty()) {
            hasBackwardRevisits = true;
            if (!hasForwardRevisits) {
                logNewBranchs();
            }
        }

        for (int i = revisitViews.size() - 1; i >= 0; i--) {
            ExplorationStack.Item newItem =
                    ExplorationStack.Item.backwardRevisit(
                            revisitViews.get(i).getWrite(),
                            revisitViews.get(i).getRestrictedGraph());
            explorationStack.push(newItem);
            logNewChild(newItem);
        }

        // After batching the backward and forward revisits, we need to continue the exploration by
        // adding the recently
        // added write as the CO max.
        executionGraph.trackCoherency(write);
        if (hasBackwardRevisits || hasForwardRevisits) {
            logConCurrChild();
            logUpdateGraphIdWithLastGraph();
        }
    }

    /**
     * Handles the write half of a (non-lock) read-modify-write: makes it coherence-maximal — there
     * are no write-write forward revisits for exclusive writes, since they are totally ordered by
     * happens-before — and pushes backward revisits of earlier reads, subject to RMW atomicity.
     *
     * <p><b>Currently unreachable.</b> The only source of exclusive writes is lock acquire, whose
     * {@code WRITE_EX} carries the {@code lock_acquire} attribute and is dispatched to {@link
     * #handleLockAcquireWrite(Event)} instead; there are no non-lock exclusive writes (atomics are
     * modeled as a lock plus plain read/write), so this handler is not invoked.
     *
     * @param event the exclusive write event.
     */
    private void handleWriteX(Event event) {
        if (areWeGuiding()) {
            return;
        }
        // Add the write event to the execution graph
        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);

        // There will not be any (w->w) forward revisits for exclusive writes.
        // Because all exclusive writes to the same location are totally ordered by the
        // happens-before

        // Check for (w->r) backward revisits
        // Find potential reads that need to be revisited
        // Additionally, we need to add a blocking label to the writes of the corresponding revisit
        // reads.
        // The only reason there are alternative reads to consider is due to two reads
        // reading from the same exclusive write which is inconsistent but the one-step
        // inconsistency is needed to ensure we explore the alternative ordering.
        List<ExecutionGraphNode> potentialReads = executionGraph.getPotentialReads(write);
        if (potentialReads.isEmpty()) {
            return;
        }
        List<BackwardRevisitView> revisitViews =
                potentialReads.stream().map((r) -> executionGraph.revisitView(write, r)).toList();

        revisitViews =
                revisitViews.stream().filter(BackwardRevisitView::isMaximalExtension).toList();

        for (BackwardRevisitView revisit : revisitViews) {
            // Block the tasks of the reads that need to be revisited
            executionGraph.addBlockingLabel(revisit.getRead().getEvent().getTaskId());
            explorationStack.push(
                    ExplorationStack.Item.backwardRevisit(
                            revisit.getWrite(), revisit.getRestrictedGraph()));
        }
    }

    /**
     * Handles a lock-acquire read (the read half of a lock RMW). If the lock is currently held (the
     * co-max write is another acquire's exclusive write), the task is blocked via a blocking label
     * instead of committing the read; otherwise the read observes the co-max write and forward
     * revisits over alternative lock writes are pushed (each carrying the paired exclusive write as a
     * deferred additional event).
     *
     * @param event the lock-acquire read event.
     */
    private void handleLockAcquireRead(Event event) {
        if (areWeGuiding()) {
            return;
        }

        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());
        if (EventUtils.isLockAcquireWrite(coMaxWrite.getEvent())) {
            // Then we block the task and delay the acquiring of the lock
            executionGraph.blockTaskForLock(event);
            return;
        }

        ExecutionGraphNode read = executionGraph.addEvent(event);
        if (coMaxWrite.happensBefore(read)) {
            LOGGER.debug("No alternative lock acquires to revisit");
            executionGraph.setReadsFrom(read, coMaxWrite);
            return;
        }

        // Find alternative lock reads to revisit
        List<ExecutionGraphNode> alternativeWrites = executionGraph.getAlternativeLockWrites(read);
        executionGraph.setReadsFrom(read, coMaxWrite);
        if (alternativeWrites.isEmpty()) {
            LOGGER.debug("No alternative lock acquires to revisit");
            return;
        }

        logNewBranchs();

        for (int i = alternativeWrites.size() - 1; i >= 0; i--) {
            ExecutionGraphNode altWrite = alternativeWrites.get(i);
            LOGGER.debug("Adding revisit to alternative lock acquire write: {}", altWrite.key());
            ExplorationStack.Item item =
                    ExplorationStack.Item.forwardRW(read, altWrite, executionGraph);
            Event additionalWrite =
                    new Event(
                            read.getEvent().getTaskId(),
                            read.getEvent().getLocation(),
                            Event.Type.WRITE_EX);
            additionalWrite.setAttribute("lock_acquire", true);
            item.addAdditionalEvent(additionalWrite);
            explorationStack.push(item);
            logNewChild(item);
        }
        logConCurrChild();
        logUpdateGraphIdWithLastGraph();
    }

    /**
     * Handles a lock-acquire write (the write half of a lock RMW): makes it coherence-maximal and
     * pushes backward revisits of alternative lock reads that pass the maximal-extension check and
     * whose resulting graph is a consistent RMW. Skipped if the task is still blocked on the lock.
     *
     * @param event the lock-acquire write event.
     */
    private void handleLockAcquireWrite(Event event) {
        if (areWeGuiding()) {
            return;
        }

        if (executionGraph.isTaskBlocked(event.getTaskId())) {
            // We cannot get the lock
            // Therefore, we skip the write event
            return;
        }

        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.acquireLock(event.getLocation(), event.getTaskId());

        List<ExecutionGraphNode> alternateLockReads = executionGraph.getAlternativeLockReads(write);
        if (!alternateLockReads.isEmpty()) {
            List<BackwardRevisitView> revisitViews =
                    alternateLockReads.stream()
                            .map((r) -> executionGraph.revisitView(write, r))
                            .toList();
            revisitViews =
                    revisitViews.stream().filter(BackwardRevisitView::isMaximalExtension).toList();

            boolean visitedConsistentBWR = false;

            for (int i = revisitViews.size() - 1; i >= 0; i--) {
                ExplorationStack.Item item =
                        ExplorationStack.Item.backwardRevisit(
                                revisitViews.get(i).getWrite(),
                                revisitViews.get(i).getRestrictedGraph());
                item.addAdditionalEvent(revisitViews.get(i).additionalEvent());
                if (item.getGraph().isRdxInconsistent(item.getEvent1())) {
                    if (!visitedConsistentBWR) {
                        logNewBranchs();
                        visitedConsistentBWR = true;
                    }
                    explorationStack.push(item);
                    logLastChild(item);
                }
            }
        }
        executionGraph.trackCoherency(write);
    }

    /**
     * Handles a lock release: adds the release write, unblocks all tasks waiting to acquire that
     * lock, and makes the write coherence-maximal.
     *
     * @param event the lock-release write event.
     */
    private void handleLockReleaseWrite(Event event) {
        if (areWeGuiding()) {
            return;
        }

        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.unblockAllTasksForLock(event.getLocation());
        executionGraph.trackCoherency(write);
    }

    /**
     * Handles a "lock acquired" marker: if the task is still waiting for the lock, issues the
     * acquire as a fresh exclusive read/write (RMW) pair; otherwise it already holds the lock and
     * this is a no-op.
     *
     * @param event the lock-acquired event.
     */
    public void handleLockAcquired(Event event) {
        if (areWeGuiding()) {
            return;
        }
        if (!executionGraph.waitingForLock(event.getLocation(), event.getTaskId())) {
            // We have acquired the lock
            return;
        }

        Event readEvent = new Event(event.getTaskId(), event.getLocation(), Event.Type.READ_EX);
        readEvent.setAttribute("lock_acquire", true);
        handleLockAcquireRead(readEvent);

        Event writeEvent = new Event(event.getTaskId(), event.getLocation(), Event.Type.WRITE_EX);
        writeEvent.setAttribute("lock_acquire", true);
        handleLockAcquireWrite(writeEvent);
    }

    /**
     * Handles an assumption by adding it to the graph; a failed assumption becomes a blocked assume
     * that prunes the execution.
     *
     * @param event the assume event.
     */
    private void handleAssume(Event event) {
        executionGraph.addEvent(event);
    }

    /** Logs the current exploration-stack contents at debug level (debugging aid). */
    public void logStackState() {
        explorationStack.logStackState();
    }

    /**
     * Writes the execution graph to a file.
     *
     * @param filePath The path to the file to write the execution graph to.
     */
    public void writeExecutionGraphToFile(String filePath) {
        //        if (!executionGraph.checkExtensiveConsistency()) {
        //            throw HaltExecutionException.error(
        //                    "The execution graph is not consistent at the end of the iteration.");
        //        }

        String executionGraphJson = executionGraph.toJsonString();
        FileUtil.unsafeStoreToFile(filePath, executionGraphJson);
    }

    /**
     * Returns the current execution graph.
     *
     * @return the execution graph.
     */
    public ExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    /**
     * Replaces the current execution graph (used by the estimator).
     *
     * @param executionGraph the graph to set.
     */
    public void setExecutionGraph(ExecutionGraph executionGraph) {
        this.executionGraph = executionGraph;
    }

    /**
     * Returns the exploration stack.
     *
     * @return the exploration stack.
     */
    public ExplorationStack getExplorationStack() {
        return explorationStack;
    }

    /** Resets the algorithm to a fresh state with an empty graph seeded by the init event. */
    public void clear() {
        this.guidingTaskSchedule = null;
        this.isGuiding = false;
        this.executionGraph.clear();
        this.explorationStack.clear();
        this.locationStore.clear();
        this.executionGraph.addEvent(Event.init());
    }

    /**
     * Returns whether the exploration stack is empty (no pending revisits).
     *
     * @return true if there is nothing left to explore.
     */
    public boolean isStackEmpty() {
        return this.explorationStack.isEmpty();
    }

    /** Tree-logger hook: records the start of a new branching line (no-op if logging is off). */
    private void logNewBranchs() {
        if (tLogger == null) {
            return;
        }
        tLogger.appendNewBranchs(executionGraph.size());
    }

    /**
     * Tree-logger hook: records a (non-last) child graph (no-op if logging is off).
     *
     * @param item the child's exploration item.
     */
    private void logNewChild(ExplorationStack.Item item) {
        if (tLogger == null) {
            return;
        }
        tLogger.appendNewChild(item);
    }

    /**
     * Tree-logger hook: records the last child graph on a branching line (no-op if logging is off).
     *
     * @param item the child's exploration item.
     */
    private void logLastChild(ExplorationStack.Item item) {
        if (tLogger == null) {
            return;
        }
        tLogger.appendLastChild(item);
    }

    /** Tree-logger hook: records a "continue current graph" child (no-op if logging is off). */
    private void logConCurrChild() {
        if (tLogger == null) {
            return;
        }
        tLogger.appendContinueCurrent();
    }

    /** Tree-logger hook: ends the current line of children (no-op if logging is off). */
    private void logEndofChilds() {
        if (tLogger == null) {
            return;
        }
        tLogger.appendNextLine();
    }

    /**
     * Tree-logger hook: advances the "current graph" to the one reserved for {@code nextItem} (no-op
     * if logging is off).
     *
     * @param nextItem the item whose graph becomes current.
     */
    private void logUpdateGraphId(ExplorationStack.Item nextItem) {
        if (tLogger == null) {
            return;
        }
        tLogger.updateLoggerGraphId(nextItem, executionGraph.size());
    }

    /**
     * Tree-logger hook: advances the "current graph" to the most recently created child (no-op if
     * logging is off).
     */
    private void logUpdateGraphIdWithLastGraph() {
        if (tLogger == null) {
            return;
        }
        tLogger.updateLoggerGraphIdWithLastGraph(executionGraph.size());
    }

    /** Tree-logger hook: records the current graph as inconsistent (no-op if logging is off). */
    private void logInconsistentGraph() {
        if (tLogger == null) {
            return;
        }
        tLogger.addInconsistentGraph();
    }

    /** Counts a blocked graph and, if logging is on, records it in the tree logger. */
    private void logBlockedGraph() {
        numOfBlockedGraphs++;
        if (tLogger == null) {
            return;
        }
        tLogger.addBlockedGraph();
    }

    /** Tree-logger hook: records the size of the finished leaf graph (no-op if logging is off). */
    private void logLastGraphSize() {
        if (tLogger == null) {
            return;
        }
        tLogger.addLeafSize(executionGraph.size());
    }

    /**
     * Returns the assembled exploration-tree log, or {@code null} if the tree logger is disabled.
     *
     * @return the tree log, or {@code null}.
     */
    public StringBuilder getTreeLog() {
        if (tLogger == null) {
            return null;
        }
        return tLogger.getLogger();
    }

    /**
     * Returns the inconsistent-graph log, or {@code null} if the tree logger is disabled.
     *
     * @return the inconsistent-graph log, or {@code null}.
     */
    public StringBuilder getInconsistentGraphLog() {
        if (tLogger == null) {
            return null;
        }
        return tLogger.getInConsistentGraphLogger();
    }

    /**
     * Returns the blocked-graph log, or {@code null} if the tree logger is disabled.
     *
     * @return the blocked-graph log, or {@code null}.
     */
    public StringBuilder getBlockedGraphLog() {
        if (tLogger == null) {
            return null;
        }
        return tLogger.getBlockedGraphLogger();
    }

    /**
     * Returns the leaf-size log, or {@code null} if the tree logger is disabled.
     *
     * @return the leaf-size log, or {@code null}.
     */
    public StringBuilder getLeafSizeLog() {
        if (tLogger == null) {
            return null;
        }
        return tLogger.getLeafSizeLogger();
    }

    /** Logs the number of inconsistent graphs encountered (no-op if the tree logger is disabled). */
    public void reportInconsistentGraphLogs() {
        if (tLogger == null) {
            return;
        }
        LOGGER.info("Number of Inconsistent Graph: " + tLogger.getNumOfInconsistentGraphs());
    }

    /** Logs the number of blocked graphs encountered (no-op if the tree logger is disabled). */
    public void reportBlockedGraphLogs() {
        if (tLogger == null) {
            return;
        }
        LOGGER.info("Number of Blocked Graph:" + tLogger.getNumOfBlockedGraphs());
    }
}
