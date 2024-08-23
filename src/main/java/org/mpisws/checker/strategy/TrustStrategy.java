package org.mpisws.checker.strategy;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import dpor.Trust;
import executionGraph.CO;
import executionGraph.ExecutionGraph;
import kotlin.Pair;
import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.symbolic.SymbolicOperation;
import programStructure.*;

/**
 * The TrustStrategy class implements the {@link SearchStrategy} interface and is responsible for managing the execution
 * order of events in a multithreaded program using the {@link Trust} model checker. It maintains a record of execution
 * graphs and a trust object for the trust model checker. The class provides functionality to handle various types of
 * events including start, join, read, write, finish, symbolic arithmetic events. The class uses the
 * {@link RuntimeEnvironment} API to create and record events. The TrustStrategy class is designed to control the flow
 * of a program's execution by using trust model checker. When it faces a new event, it passes the event to the trust
 * model checker and updates the current graph based on the model checker's response. Based on the response, it picks
 * the next thread to execute. The class also provides functionality to save the execution graphs to a file.
 */
public class TrustStrategy extends DPORStrategy {

    /**
     * The following constructor initializes the model checker graphs list, the current execution graph, and trust object,
     * the guiding execution graph if it is available. Moreover, it initializes the buggy trace path, the buggy trace file,
     * the execution graphs path, and the solver.
     */
    public TrustStrategy() {
        super();
        initTrust();
    }

    /**
     * Initializes the trust object.
     * <p>
     * This method initializes the {@link #dpor} object. It sets the {@link Trust#getGraphCounter()} with the number of
     * graphs that are available in the {@link RuntimeEnvironment#mcGraphs}.
     * </p>
     */
    private void initTrust() {
        dpor = new Trust(executionGraphsPath);
        dpor.setGraphCounter(RuntimeEnvironment.numOfGraphs);
    }

//    /**
//     * Initializes the trust object with the root event of the current graph.
//     * <p>
//     * This method adds the root event of the {@link #currentGraph} to the {@link Trust#getAllEvents()}.
//     * </p>
//     */
//    private void noGuidingGraph() {
//        System.out.println("[Trust Strategy Message] : The guiding execution graph is empty");
//        Optional<Event> event = Optional.of(Objects.requireNonNull(currentGraph.getRoot()).getValue());
//        event.ifPresent(value -> trust.getAllEvents().add(value));
//    }

    /**
     * Represents the required strategy for the next enter monitor event.
     * <p>
     * This method represents the required strategy for the next enter monitor event. It creates a {@link EnterMonitorEvent}
     * for the corresponding entering a monitor request of a thread. First, it adds the created {@link EnterMonitorEvent}
     * to the {@link RuntimeEnvironment#eventsRecord}. Then, it adds the event to the {@link #currentGraph} if the
     * {@link #guidingActivate} is true. Otherwise, it passes the event to the {@link #dpor} model checker and updatest
     * the current graph.
     * </p>
     *
     * @param thread  is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        EnterMonitorEvent enterMonitorEvent = RuntimeEnvironment.createEnterMonitorEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(enterMonitorEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(enterMonitorEvent);
        } else {
            passEventToDPOR(enterMonitorEvent);
            updateCurrentGraph(enterMonitorEvent);
        }
    }

    /**
     * Represents the required strategy for the next exit monitor event.
     * <p>
     * This method represents the required strategy for the next exit monitor event. It creates a {@link ExitMonitorEvent}
     * for the corresponding exiting a monitor request of a thread and records it. The created {@link ExitMonitorEvent}
     * is added to the {@link #currentGraph} if the {@link #guidingActivate} is true. Otherwise, it passes the event to
     * the {@link #dpor} model checker and updates the current graph. The method also analyzes the suspended threads for
     * the monitor.
     * </p>
     *
     * @param thread  is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    @Override
    public void nextExitMonitorEvent(Thread thread, Object monitor) {
        ExitMonitorEvent exitMonitorEvent = RuntimeEnvironment.createExitMonitorEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(exitMonitorEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(exitMonitorEvent);
            analyzeSuspendedThreadsForMonitor(monitor, thread);
        } else {
            passEventToDPOR(exitMonitorEvent);
            updateCurrentGraph(exitMonitorEvent);
            analyzeSuspendedThreadsForMonitor(monitor, thread);
        }
    }

    /**
     * Handles the next park request of a given thread.
     * <p>
     * This method handles the next park request of a given thread. It creates a {@link ParkEvent} for the corresponding
     * parking request of a thread and records it. If the scheduling is in the guided mode, it adds the event to the
     * current graph. Also, it checks whether the thread has the parking permit or not. If the thread has the parking
     * permit, it sets the parking permit to false. Otherwise, it parks the thread. If the scheduling is normal mode,
     * it checks whether the thread has the parking permit or not. If the thread has the parking permit, it sets the
     * parking permit to false. Then, it creates a {@link UnparkEvent} for the corresponding unparking request of the
     * thread and records it. Finally, it passes the events to the trust model checker and updates the current graph.
     * If the thread does not have the parking permit, it parks the thread. Then, it passes the park event to the trust
     * model checker.
     * </p>
     *
     * @param thread is the thread that is going to be parked.
     */
    @Override
    public void nextParkRequest(Thread thread) {
        ParkEvent parkRequestEvent = RuntimeEnvironment.createParkEvent(thread);
        RuntimeEnvironment.eventsRecord.add(parkRequestEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(parkRequestEvent);
            long tid = RuntimeEnvironment.threadIdMap.get(thread.getId());
            if (RuntimeEnvironment.threadParkingPermit.get(tid)) {
                RuntimeEnvironment.threadParkingPermit.put(tid, false);
            } else {
                parkThread(thread);
            }
        } else {
            long tid = RuntimeEnvironment.threadIdMap.get(thread.getId());
            if (RuntimeEnvironment.threadParkingPermit.get(tid)) {
                RuntimeEnvironment.threadParkingPermit.put(tid, false);
                UnparkEvent unparkRequestEvent = RuntimeEnvironment.createUnparkEvent(thread);
                RuntimeEnvironment.eventsRecord.add(unparkRequestEvent);
                List<Event> events = new ArrayList<>();
                events.add(parkRequestEvent);
                events.add(unparkRequestEvent);
                passEventToDPOR(events);
                updateCurrentGraph(unparkRequestEvent);
            } else {
                parkThread(thread);
                passEventToDPOR(parkRequestEvent);
            }
        }
    }

    /**
     * Handles the next unpark request of a given thread.
     * <p>
     * This method handles the next unpark request of a given thread. It creates a {@link UnparkingEvent} for the
     * corresponding unparking request of a thread and records it. If the scheduling is in the guided mode, it adds the
     * event to the current graph. Also, it checks whether the unparkee thread is parked or not. If the unparkee thread
     * is parked, it unparks the unparkee thread. Otherwise, it sets the parking permit of the unparkee thread to true.
     * If the scheduling is normal mode, it checks whether the unparkee thread is parked or not. If the unparkee thread
     * is parked, it unparks the unparkee thread, creates a {@link UnparkEvent} for the corresponding unparking request
     * of the unparkee thread and records it. Then, it passes the events to the trust model checker and updates the
     * current graph. If the unparkee thread is not parked, it sets the parking permit of the unparkee thread to true,
     * creates a {@link UnparkEvent} for the corresponding unparking request of the unparkee thread and records it.
     * Finally, it passes the events to the trust model checker and updates the current graph.
     * </p>
     *
     * @param unparkerThread is the thread that is going to unpark unparkeeThread.
     * @param unparkeeThread is the thread that is going to be unparked by unparkerThread.
     */
    @Override
    public void nextUnparkRequest(Thread unparkerThread, Thread unparkeeThread) {
        UnparkingEvent unparkingRequestEvent = RuntimeEnvironment.createUnparkingEvent(unparkerThread, unparkeeThread);
        RuntimeEnvironment.eventsRecord.add(unparkingRequestEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(unparkingRequestEvent);
            if (RuntimeEnvironment.parkedThreadList.contains(unparkeeThread)) {
                unparkThread(unparkeeThread);
            } else {
                RuntimeEnvironment.threadParkingPermit.put(RuntimeEnvironment.threadIdMap.get(unparkeeThread.getId()), true);
            }
        } else {
            if (RuntimeEnvironment.parkedThreadList.contains(unparkeeThread)) {
                unparkThread(unparkeeThread);
                passEventToDPOR(unparkingRequestEvent);
                updateCurrentGraph(unparkingRequestEvent);
                UnparkEvent unparkRequestEvent = RuntimeEnvironment.createUnparkEvent(unparkeeThread);
                RuntimeEnvironment.eventsRecord.add(unparkRequestEvent);
                passEventToDPOR(unparkRequestEvent);
                updateCurrentGraph(unparkRequestEvent);
            } else {
                RuntimeEnvironment.threadParkingPermit.put(RuntimeEnvironment.threadIdMap.get(unparkeeThread.getId()), true);
                passEventToDPOR(unparkingRequestEvent);
                updateCurrentGraph(unparkingRequestEvent);
            }
        }
    }

    /**
     * Represents the required strategy for the next read event.
     * <p>
     * This method represents the required strategy for the next read event. It passes the read event to the {@link #dpor}
     * model checker if the {@link #guidingActivate} is false and updates the current graph. Otherwise, it adds the read
     * event to the current graph coupled with the reads-from edge.
     * </p>
     *
     * @param readEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        RuntimeEnvironment.eventsRecord.add(readEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(readEvent);
            addRfEdgeToCurrentGraph(readEvent);
        } else {
            passEventToDPOR(readEvent);
            updateCurrentGraph(readEvent);
        }
    }

    /**
     * @param receiveEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReceiveEvent(ReceiveEvent receiveEvent) {
        //Trust Strategy does not handle the receive event
    }

    /**
     * Adds the reads-from edge to the current graph.
     * <p>
     * This method adds the reads-from edge to the {@link #currentGraph}. It sets the reads-from edge of the read event
     * to the {@link #currentGraph}.
     * </p>
     *
     * @param readEvent is the read event that is going to be executed.
     */
    private void addRfEdgeToCurrentGraph(ReadEvent readEvent) {
        Optional<ReadsFrom> readsFrom = Optional.ofNullable(findRfEdge((ReadEvent) guidingEvent));
        readsFrom.ifPresent(readEvent::setRf);
    }

    /**
     * Finds the reads-from edge of the read event.
     * <p>
     * This method finds the reads-from edge of the read event. It returns the reads-from corresponding event to the
     * read event.
     * </p>
     *
     * @param readEvent is the read event that is going to be executed.
     * @return the corresponding reads-from event to the read event.
     */
    private ReadsFrom findRfEdge(ReadEvent readEvent) {
        ReadsFrom readsFrom;

        if (readEvent.getRf() instanceof InitializationEvent) {
            readsFrom = (ReadsFrom) currentGraph.getGraphEvents().get(0);
        } else {
            WriteEvent tempWrite = (WriteEvent) readEvent.getRf();

            readsFrom = currentGraph.getGraphEvents().stream()
                    .filter(event -> event instanceof WriteEvent)
                    .map(event -> (WriteEvent) event)
                    .filter(writeEvent -> writeEvent.getTid() == Objects.requireNonNull(tempWrite).getTid() &&
                            writeEvent.getSerial() == tempWrite.getSerial())
                    .findFirst()
                    .orElse(null);
        }
        return readsFrom;
    }

    /**
     * Checks whether the graph is valid or not.
     * <p>
     * This method checks whether the graph is valid or not. It returns true if the {@link ExecutionGraph#getSc()} of the
     * graph does not contain the given thread event. Otherwise, it returns false.
     * </p>
     *
     * @param graph       is the graph that is going to be checked.
     * @param threadEvent is the thread event that is going to be checked.
     * @return true if the graph is valid, otherwise false.
     */
    @Override
    public boolean isValidGraph(ExecutionGraph graph, ThreadEvent threadEvent) {
        return graph.getSc().stream()
                .noneMatch(pair -> pair.component1().getType() == threadEvent.getType() &&
                        ((ThreadEvent) pair.component1()).getTid() == threadEvent.getTid() &&
                        ((ThreadEvent) pair.component1()).getSerial() == threadEvent.getSerial());
    }

    /**
     * Represents the required strategy for the next write event.
     * <p>
     * This method represents the required strategy for the next write event. It passes the write event to the
     * {@link #dpor} model checker if the {@link #guidingActivate} is false and updates the current graph. Otherwise,
     * it adds the write event to the current graph.
     * </p>
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        RuntimeEnvironment.eventsRecord.add(writeEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(writeEvent);
        } else {
            passEventToDPOR(writeEvent);
            updateCurrentGraph(writeEvent);
        }
    }

    /**
     * @param sendEvent is the write event that is going to be executed.
     */
    @Override
    public void nextSendEvent(SendEvent sendEvent) {
        // Trust Strategy does not handle the send event
    }

    @Override
    public boolean nextBlockingReceiveRequest(ReceiveEvent receiveEvent) {
        // Trust Strategy does not handle the blocking receive request
        return false;
    }

    /**
     * Represents the required strategy for the next enter monitor request.
     * <p>
     * This method represents the required strategy for the next enter monitor request. It creates a
     * {@link MonitorRequestEvent} for the corresponding entering a monitor request of a thread and records it.
     * The created {@link MonitorRequestEvent} is added to the {@link #currentGraph} if the {@link #guidingActivate} is
     * true. Otherwise, it passes the event to the {@link #dpor} model checker. The method also checks the deadlock
     * detection between the threads in using the monitors. If there is a deadlock, it sets the
     * {@link RuntimeEnvironment#deadlockHappened} to true and sets the {@link RuntimeEnvironment#executionFinished} to
     * true. Otherwise, it picks the next thread to execute.
     * </p>
     *
     * @param thread  is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     * @return the next thread that is going to be executed.
     */
    @Override
    public Thread nextEnterMonitorRequest(Thread thread, Object monitor) {
        MonitorRequestEvent monitorRequestEvent = RuntimeEnvironment.createMonitorRequestEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(monitorRequestEvent);
        RuntimeEnvironment.monitorRequest.put(thread, monitor);
        if (guidingActivate) {
            addEventToCurrentGraph(monitorRequestEvent);
        } else {
            passEventToDPOR(monitorRequestEvent);
        }
        if (monitorsDeadlockDetection()) {
            System.out.println(
                    "[Trust Strategy Message] : There is a deadlock between the threads in using " +
                            "the monitors"
            );
            RuntimeEnvironment.deadlockHappened = true;
            RuntimeEnvironment.executionFinished = true;
            return null;
        } else {
            System.out.println(
                    "[Trust Strategy Message] : There is no deadlock between the threads in using " +
                            "the monitors"
            );
            return pickNextThread();
        }
    }

    /**
     * Represents the required strategy for the next symbolic operation request.
     * <p>
     * This method represents the required strategy for the next symbolic operation request. It calls the
     * {@link #handleGuidingSymbolicOperationRequest(Thread, SymbolicOperation)} if the {@link #guidingActivate} is true.
     * Otherwise, it calls the {@link #handleNewSymbolicOperationRequest(Thread, SymbolicOperation)}.
     * </p>
     *
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     */
    @Override
    public void nextSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        if (guidingActivate) {
            handleGuidingSymbolicOperationRequest(thread, symbolicOperation);
        } else {
            handleNewSymbolicOperationRequest(thread, symbolicOperation);
        }
    }

    /**
     * Handles the guiding symbolic operation request.
     * <p>
     * This method handles the guiding symbolic operation request. It sets the {@link RuntimeEnvironment#solverResult}
     * with the result of the {@link SymExecutionEvent#getResult()} of the {@link #guidingEvent}. Then, it updates the
     * path symbolic operations with the given symbolic operation and thread. It creates a new {@link SymExecutionEvent}
     * with the given thread, the formula of the symbolic operation, and the negatable value of the {@link #guidingEvent}.
     * Finally, it adds the created event to the {@link RuntimeEnvironment#eventsRecord} and passes the event to the
     * current graph.
     * </p>
     *
     * @param thread            is the thread that is going to execute the symbolic operation.
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     */
    public void handleGuidingSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        SymExecutionEvent guidingSymExecutionEvent = (SymExecutionEvent) guidingEvent;
        RuntimeEnvironment.solverResult = guidingSymExecutionEvent.getResult();
        updatePathSymbolicOperations(symbolicOperation, thread);
        SymExecutionEvent symExecutionEvent = RuntimeEnvironment.createSymExecutionEvent(thread,
                symbolicOperation.getFormula().toString(), guidingSymExecutionEvent.isNegatable());
        RuntimeEnvironment.eventsRecord.add(symExecutionEvent);
        addEventToCurrentGraph(symExecutionEvent);
    }

    /**
     * Handles the new symbolic operation request.
     * <p>
     * This method handles the new symbolic operation request. It finds the dependent formulas of the given symbolic
     * operation. If there is no dependent formula, it calls the {@link #handleFreeFormulas(SymbolicOperation)}
     * method. Otherwise, it calls the {@link #handleDependentFormulas(SymbolicOperation, List)} method. Then, it
     * updates the path symbolic operations with the given symbolic operation and thread. It creates a new
     * {@link SymExecutionEvent} with the given thread, the formula of the symbolic operation, and the negatable value.
     * Finally, it adds the created event to the {@link RuntimeEnvironment#eventsRecord} and passes the event to the trust.
     * </p>
     *
     * @param symbolicOperation is the symbolic operation that is going to be added to the path symbolic operations.
     * @param thread            is the thread that is going to execute the symbolic operation.
     */
    public void handleNewSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        List<SymbolicOperation> dependentOperations = findDependentFormulas(symbolicOperation);
        if (dependentOperations == null) {
            handleFreeFormulas(symbolicOperation);
            System.out.println("[Trust Strategy Message] : The result of the symbolic arithmetic operation is " +
                    RuntimeEnvironment.solverResult);
        } else {
            handleDependentFormulas(symbolicOperation, dependentOperations);
            System.out.println("[Trust Strategy Message] : The result of the symbolic arithmetic operation is " +
                    RuntimeEnvironment.solverResult);
        }
        updatePathSymbolicOperations(symbolicOperation, thread);
        SymExecutionEvent symExecutionEvent = RuntimeEnvironment.createSymExecutionEvent(thread,
                symbolicOperation.getFormula().toString(), isNegatable);
        RuntimeEnvironment.eventsRecord.add(symExecutionEvent);
        passEventToDPOR(symExecutionEvent);
        updateCurrentGraph(symExecutionEvent);
    }

    /**
     * Finds the dependent formulas of the given symbolic operation.
     * <p>
     * This method finds the dependent formulas of the given symbolic operation. It iterates over the path symbolic
     * operations and checks whether the given symbolic operation is dependent on another symbolic operation. If it is
     * dependent, it adds the dependent symbolic operation to the list of dependent formulas. Otherwise, it returns null.
     * </p>
     *
     * @param symbolicOperation is the symbolic operation that is going to be checked.
     * @return the list of dependent formulas of the given symbolic operation.
     */
    private List<SymbolicOperation> findDependentFormulas(SymbolicOperation symbolicOperation) {
        List<SymbolicOperation> dependencyOperations = new ArrayList<>();
        List<SymbolicOperation> symbolicOperations = RuntimeEnvironment.pathSymbolicOperations;
        for (SymbolicOperation symOp : symbolicOperations) {
            if (symOp.isFormulaDependent(symbolicOperation)) {
                System.out.println("[Trust Strategy Message] : The symbolic arithmetic operation is dependent on " +
                        "another symbolic arithmetic operation");
                System.out.println("[Trust Strategy Message] : The dependent symbolic arithmetic operations are : " +
                        symOp.getFormula().toString() + " and " + symbolicOperation.getFormula().toString());
                dependencyOperations.add(symOp);
            }
        }
        if (dependencyOperations.isEmpty()) {
            return null;
        } else {
            return dependencyOperations;
        }
    }

    /**
     * Finds the dependent formulas of the given symbolic operation based on the thread.
     * <p>
     * This method finds the dependent formulas of the given symbolic operation based on the thread. It iterates over the
     * symbolic operations of the thread and checks whether the given symbolic operation is dependent on another symbolic
     * operation. If it is dependent, it adds the dependent symbolic operation to the list of dependent formulas. Otherwise,
     * it returns null.
     * </p>
     *
     * @param thread            is the thread that is going to be checked.
     * @param symbolicOperation is the symbolic operation that is going to be checked.
     * @return the list of dependent formulas of the given symbolic operation.
     */
    private List<SymbolicOperation> findDependentThreadFormulas(Thread thread, SymbolicOperation symbolicOperation) {
        List<SymbolicOperation> dependencyOperations = new ArrayList<>();
        List<SymbolicOperation> symbolicOperations = RuntimeEnvironment.threadSymbolicOperation.get(
                RuntimeEnvironment.threadIdMap.get(thread.getId())
        );
        for (SymbolicOperation symOp : symbolicOperations) {
            if (symOp.isFormulaDependent(symbolicOperation)) {
                dependencyOperations.add(symOp);
            }
        }
        if (dependencyOperations.isEmpty()) {
            return null;
        } else {
            return dependencyOperations;
        }

    }

    /**
     * Handles the free formulas.
     * <p>
     * This method handles the free formulas. It calls the {@link #solveSat(SymbolicOperation)} and
     * {@link #solveUnsat(SymbolicOperation)} methods to solve the symbolic operation. Then, it calls the
     * {@link #pickSatOrUnsat(boolean, boolean)} method to pick the result of the symbolic operation. Finally, it sets
     * the {@link RuntimeEnvironment#solverResult} with the result of the symbolic operation.
     * </p>
     *
     * @param symbolicOperation is the symbolic operation that is going to be solved.
     */
    private void handleFreeFormulas(SymbolicOperation symbolicOperation) {
        System.out.println("[Random Strategy Message] : The symbolic arithmetic operation is free from dependencies");
        boolean sat = solveSat(symbolicOperation);
        boolean unSat = solveUnsat(symbolicOperation);
        isNegatable = sat && unSat;
        RuntimeEnvironment.solverResult = pickSatOrUnsat(sat, unSat);
    }

    /**
     * Handles the dependent formulas.
     * <p>
     * This method handles the dependent formulas. First, it creates a dependency operation from the list of dependent
     * formulas. Then, it calls the {@link #solveDependentSat(SymbolicOperation, SymbolicOperation)}
     * and {@link #solveDependentUnsat(SymbolicOperation, SymbolicOperation)} methods to solve the symbolic operation. Then,
     * it calls the {@link #pickSatOrUnsat(boolean, boolean)} method to pick the result of the symbolic operation. Finally,
     * it sets the {@link RuntimeEnvironment#solverResult} with the result of the symbolic operation.
     * </p>
     *
     * @param symbolicOperation   is the symbolic operation that is going to be solved.
     * @param dependentOperations is the list of dependent formulas of the symbolic operation.
     */
    private void handleDependentFormulas(SymbolicOperation symbolicOperation, List<SymbolicOperation> dependentOperations) {
        System.out.println("[Random Strategy Message] : The symbolic arithmetic operation has dependencies");
        SymbolicOperation dependency = solver.makeDependencyOperation(dependentOperations);
        boolean sat = solveDependentSat(symbolicOperation, dependency);
        boolean unSat = solveDependentUnsat(symbolicOperation, dependency);
        isNegatable = sat && unSat;
        RuntimeEnvironment.solverResult = pickSatOrUnsat(sat, unSat);
    }

    /**
     * Solves the symbolic operation.
     *
     * @param symbolicOperation is the symbolic operation that is going to be solved.
     * @return true if the symbolic operation is satisfiable, otherwise false.
     */
    private boolean solveSat(SymbolicOperation symbolicOperation) {
        return solver.solveSymbolicFormula(symbolicOperation);
    }

    /**
     * Solves the dependent symbolic operation.
     *
     * @param symbolicOperation is the symbolic operation that is going to be solved.
     * @param dependency        is the dependent symbolic operation.
     * @return true if the symbolic operation is satisfiable, otherwise false.
     */
    private boolean solveDependentSat(SymbolicOperation symbolicOperation, SymbolicOperation dependency) {
        return solver.solveDependentSymbolicFormulas(symbolicOperation, dependency);
    }

    /**
     * Solves the unsatisfiable symbolic operation.
     *
     * @param symbolicOperation is the symbolic operation that is going to be solved.
     * @return true if the symbolic operation is unsatisfiable, otherwise false.
     */
    private boolean solveUnsat(SymbolicOperation symbolicOperation) {
        return solver.disSolveSymbolicFormula(symbolicOperation);
    }

    /**
     * Solves the dependent unsatisfiable symbolic operation.
     *
     * @param symbolicOperation is the symbolic operation that is going to be solved.
     * @param dependency        is the dependent symbolic operation.
     * @return true if the symbolic operation is unsatisfiable, otherwise false.
     */
    private boolean solveDependentUnsat(SymbolicOperation symbolicOperation, SymbolicOperation dependency) {
        return solver.disSolveDependentSymbolicFormulas(symbolicOperation, dependency);
    }

    /**
     * Picks the result of the symbolic operation.
     *
     * @param sat   is the satisfiability of the symbolic operation.
     * @param unSat is the unsatisfiability of the symbolic operation.
     * @return true if the symbolic operation is satisfiable, otherwise false.
     */
    private boolean pickSatOrUnsat(boolean sat, boolean unSat) {
        if (sat && unSat) {
            System.out.println("[Random Strategy Message] : Both SAT and UNSAT are possible for the symbolic " +
                    "arithmetic operation");
            return new Random().nextBoolean();
        } else if (sat) {
            System.out.println("[Random Strategy Message] : Only SAT is possible for the symbolic arithmetic " +
                    "operation");
            return true;
        } else if (unSat) {
            System.out.println("[Random Strategy Message] : Only UNSAT is possible for the symbolic arithmetic " +
                    "operation");
            return false;
        } else {
            System.out.println("[Random Strategy Message] : No solution is found for the symbolic arithmetic " +
                    "operation");
            System.exit(0);
            return false;
        }
    }

    /**
     * Represents the required strategy for the next suspend event.
     * <p>
     * This method represents the required strategy for the next suspend event. It creates a {@link SuspendEvent} for
     * the corresponding suspending execution request of a thread and records it. The created {@link SuspendEvent} is
     * added to the {@link #currentGraph}.
     * </p>
     *
     * @param thread  is the thread that is going to be suspended.
     * @param monitor is the monitor that the thread is going to be suspended for it.
     */
    private void nextSuspendEvent(Thread thread, Object monitor) {
        SuspendEvent suspendEvent = RuntimeEnvironment.createSuspendEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(suspendEvent);
        addEventToCurrentGraph(suspendEvent);
    }

    /**
     * Picks the next guided thread.
     * <p>
     * This method picks the next guided thread. It checks whether the {@link #guidingEvents} is empty or not. If it is
     * empty, it calls the {@link #handleEmptyGuidingEvents()} method and picks the next random thread. Otherwise, it
     * picks the next guided thread based on the {@link #guidingEvents} list. If the guiding event is an instance of
     * {@link UnparkingEvent}, it sets the {@link #guidingThread} with the thread id of the unparked thread and returns
     * the next guided thread. If the {@link #guidingEvent} is an instance of {@link StartEvent}, it finds the guiding
     * thread from the {@link #guidingExecutionGraph}. Otherwise, it sets the {@link #guidingThread} with the thread id
     * of the {@link #guidingEvent}. If the {@link #guidingEvent} is an instance of {@link EnterMonitorEvent}, it calls
     * the {@link #guidedEnterMonitorEventHelper(EnterMonitorEvent)} method. If the {@link #guidingEvent} is an instance
     * of {@link SuspendEvent}, it calls the {@link #guidedSuspendEventHelper(SuspendEvent)} method and picks the next
     * guided thread. If the {@link #guidingEvent} is an instance of {@link UnparkEvent}, it calls the
     * {@link #guidedUnparkEventHelper(UnparkEvent)} method and returns the unparked thread. Otherwise, it returns the
     * next guided thread.
     * </p>
     *
     * @return the next guided thread that is going to be executed.
     */
    @Override
    public Thread pickNextGuidedThread() {
        if (guidingEvents.isEmpty()) {
            handleEmptyGuidingEvents();
            return pickNextRandomThread();
        }

        if (guidingEvent != null && guidingEvent.getType() == EventType.UNPARKING) {
            System.out.println("[Trust Strategy Message] : Thread-" +
                    RuntimeEnvironment.threadObjectMap.get((long) ((UnparkingEvent) guidingEvent).getTid()).getId() +
                    " is the next guided thread for UNPARKING");
            Thread nextThread = RuntimeEnvironment.threadObjectMap.get((long) ((UnparkingEvent) guidingEvent).getTid());
            guidingEvent = null;
            return nextThread;
        }
        guidingEvent = guidingEvents.remove(0);

        if (guidingEvent instanceof StartEvent) {
            guidingThread = findGuidingThreadFromStartEvent();
        } else {
            guidingThread = ((ThreadEvent) guidingEvent).getTid();
        }

        if (guidingEvent.getType() == EventType.ENTER_MONITOR) {
            guidedEnterMonitorEventHelper((EnterMonitorEvent) guidingEvent);
        }

        if (guidingEvent.getType() == EventType.SUSPEND) {
            guidedSuspendEventHelper((SuspendEvent) guidingEvent);
            return pickNextGuidedThread();
        }

        if (guidingEvent.getType() == EventType.UNPARK) {
            guidedUnparkEventHelper((UnparkEvent) guidingEvent);
            return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
        }

        System.out.println("[Trust Strategy Message] : Thread-" +
                RuntimeEnvironment.threadObjectMap.get((long) guidingThread) + " is the next guided thread");
        return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
    }

    /**
     * Handles the empty guiding events.
     * <p>
     * This method handles the empty guiding events. It prints a message that the guiding events is empty and finds the
     * new COs, STs, JTs, MCs, and TCs based on the current graph. Then, it sets the {@link #guidingActivate} to false.
     * </p>
     */
    @Override
    public void handleEmptyGuidingEvents() {
        System.out.println("[Trust Strategy Message] : The guidingEvents is empty");
        currentGraph.setCOs(findNewCOs());
        currentGraph.setSTs(findNewSTs());
        currentGraph.setJTs(findNewJTs());
        currentGraph.setMCs(findNewMCs());
        currentGraph.setTCs(findNewTCs());
        currentGraph.setPCs(findNewPCs());
        guidingActivate = false;
    }

    /**
     * Prepare the next guided unpark event.
     * <p>
     * This method prepares the next guided unpark event.
     * </p>
     *
     * @param unparkEvent is the unpark event that is going to be executed.
     */
    private void guidedUnparkEventHelper(UnparkEvent unparkEvent) {
        System.out.println("[Trust Strategy Message] : The next guided event is UNPARK event.");
        Thread thread = RuntimeEnvironment.threadObjectMap.get((long) unparkEvent.getTid());
        unparkThread(thread);
    }

    /**
     * Prepare the next guided enter monitor event.
     * <p>
     * This method prepares the next guided enter monitor event. It finds the thread from the
     * {@link RuntimeEnvironment#threadObjectMap} and the monitor from the {@link RuntimeEnvironment#monitorRequest}.
     * Then, it removes the pair of the thread and the monitor from the {@link RuntimeEnvironment#monitorRequest} and
     * calls the {@link #nextEnterMonitorEvent(Thread, Object)} method.
     * </p>
     *
     * @param enterMonitorEvent is the enter monitor event that is going to be executed.
     */
    private void guidedEnterMonitorEventHelper(EnterMonitorEvent enterMonitorEvent) {
        Thread thread = RuntimeEnvironment.threadObjectMap.get((long) enterMonitorEvent.getTid());
        Object monitor = RuntimeEnvironment.monitorRequest.get(thread);
        RuntimeEnvironment.monitorRequest.remove(thread, monitor);
        nextEnterMonitorEvent(thread, monitor);
    }

    /**
     * Prepare the next guided suspend event.
     * <p>
     * This method prepares the next guided suspend event. It finds the thread from the
     * {@link RuntimeEnvironment#threadObjectMap} and calls the {@link #nextSuspendEvent(Thread, Object)} method.
     * Then, by iterating through the {@link ExecutionGraph#getMCs()} , it finds the event which made the thread to suspend.
     * It then updates the {@link RuntimeEnvironment#suspendPriority} map with the the pair of the thread id of the event
     * which made the thread to suspend and the thread id of the thread which is going to be suspended, related to the monitor.
     * Finally, it calls the {@link #nextSuspendEvent(Thread, Object)} method.
     * </p>
     *
     * @param suspendEvent is the suspend event that is going to be executed.
     */
    private void guidedSuspendEventHelper(SuspendEvent suspendEvent) {
        Thread suspendThread = RuntimeEnvironment.threadObjectMap.get((long) suspendEvent.getTid());
        suspendThread(suspendThread);

        Set<Pair<Event, Event>> mcs = guidingExecutionGraph.getMCs();
        ThreadEvent firstThreadEvent = null;
        for (Pair<Event, Event> mc : mcs) {
            if (mc.component2().equals(suspendEvent)) {
                firstThreadEvent = (ThreadEvent) mc.component1();
                break;
            }
        }

        Object monitor = RuntimeEnvironment.monitorRequest.get(suspendThread);
        if (!RuntimeEnvironment.suspendPriority.containsKey(monitor)) {
            RuntimeEnvironment.suspendPriority.put(monitor, new HashSet<>());
        }

        RuntimeEnvironment.suspendPriority.get(monitor).add(new Pair<>((long) firstThreadEvent.getTid(), (long) suspendEvent.getTid()));
        nextSuspendEvent(suspendThread, monitor);
    }

    /**
     * Analyzes suspended threads to make them unsuspend if possible.
     * <p>
     * This methods analyzes the suspended threads to make them unsuspend if possible. First, it updates the
     * {@link RuntimeEnvironment#suspendPriority} map by calling the {@link #updateSuspendPriority(Object, Thread)} method.
     * Then, it finds the candidate threads for being unsuspended by calling the {@link #findSuspendedThreads(Object)}
     * method. It also finds the forbidden threads for being unsuspended by calling the {@link #findForbiddenThreads(Object)}
     * method. Finally, it unsuspends the candidate threads if they are not in the forbidden threads list.
     * </p>
     *
     * @param monitor is the monitor that some threads might be suspended for it.
     * @param thread  is the thread that might be suspended other threads for the monitor.
     */
    private void analyzeSuspendedThreadsForMonitor(Object monitor, Thread thread) {
        updateSuspendPriority(monitor, thread);
        List<Thread> candidateThreads = findSuspendedThreads(monitor);
        //System.out.println("[Debugging Message] : The candidate threads for resuming are : ");
        //candidateThreads.forEach(t -> System.out.println("Thread-" + RuntimeEnvironment.threadIdMap.get(t.getId())));
        List<Thread> forbiddenThreads = findForbiddenThreads(monitor);
        //System.out.println("[Debugging Message] : The forbidden threads for resuming are : ");
        //forbiddenThreads.forEach(t -> System.out.println("Thread-" + RuntimeEnvironment.threadIdMap.get(t.getId())));
        if (!candidateThreads.isEmpty()) {
            for (Thread t : candidateThreads) {
                if (!forbiddenThreads.contains(t)) {
                    //System.out.println("[Debugging Message] : Thread-" + RuntimeEnvironment.threadIdMap.get(t.getId()) +
                    //        " is going to be unsuspended");
                    unsuspendThread(t);
                }
            }
        }
    }

    /**
     * Updates the suspend priority map.
     * <p>
     * This method removes the pairs from the {@link RuntimeEnvironment#suspendPriority} for the given existing monitor
     * as a key, if the first element of the pairs is equal to the thread id of the given thread.
     * </p>
     *
     * @param monitor is the monitor that some threads might be suspended for it.
     * @param thread  is the thread that might be suspended other threads for the monitor.
     */
    private void updateSuspendPriority(Object monitor, Thread thread) {
        if (RuntimeEnvironment.suspendPriority.containsKey(monitor)) {
            RuntimeEnvironment.suspendPriority.get(monitor).removeIf(pair -> pair.component1().equals(RuntimeEnvironment.threadIdMap.get(thread.getId())));
        }
    }

    /**
     * Finds the list of threads which are forbidden to be unsuspended
     * <p>
     * This method finds the list of threads which are forbidden to be unsuspended. It iterates over the pairs of the
     * existing monitor key from the {@link RuntimeEnvironment#suspendPriority}. For each pair, it collects the second
     * component of the pair, which is the thread id of the thread that is forbidden to be unsuspended.
     * </p>
     *
     * @param monitor is the monitor that some threads might be suspended for it.
     * @return the list of threads which are forbidden to be unsuspended.
     */
    private List<Thread> findForbiddenThreads(Object monitor) {
        List<Thread> forbiddenThreads = new ArrayList<>();
        if (RuntimeEnvironment.suspendPriority.containsKey(monitor)) {
            // Find all the second elements of the pairs in the set of pairs, corresponding to the monitor, and add them
            // to the forbidden threads list.
            forbiddenThreads = RuntimeEnvironment.suspendPriority.get(monitor).stream()
                    .map(pair -> RuntimeEnvironment.threadObjectMap.get(pair.component2()))
                    .collect(Collectors.toList());
        }
        return forbiddenThreads;
    }
}