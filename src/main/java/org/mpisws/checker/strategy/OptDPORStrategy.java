package org.mpisws.checker.strategy;

import dpor.OptTrust;

import executionGraph.OptExecutionGraph;
import executionGraph.operations.GraphOp;

import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.solver.SymbolicSolver;
import org.mpisws.symbolic.SymbolicOperation;

import programStructure.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

public abstract class OptDPORStrategy implements SearchStrategy {

    protected OptExecutionGraph currentGraph;

    protected boolean guidingActivate = false;

    protected int guidingThread;

    protected ArrayList<ThreadEvent> guidingEvents;

    protected ArrayList<GraphOp> mcOpGraphs = new ArrayList<>();

    protected ThreadEvent guidingEvent;

    protected String buggyTracePath;

    protected String buggyTraceFile;

    protected SymbolicSolver solver;

    protected OptTrust dpor;

    protected String executionGraphsPath;

    public OptDPORStrategy() {
        buggyTracePath = JmcRuntime.buggyTracePath;
        buggyTraceFile = JmcRuntime.buggyTraceFile;
        executionGraphsPath = JmcRuntime.executionGraphsPath;
        solver = JmcRuntime.solver;
        initCurrentGraph();
    }

    private void initCurrentGraph() {
        if (JmcRuntime.guidingGraph == null) {
            currentGraph = new OptExecutionGraph();
        } else {
            currentGraph = JmcRuntime.guidingGraph;
            guidingEvents = JmcRuntime.guidingEvents;
            guidingActivate = true;
        }
    }

    protected abstract void handleGraphOp(GraphOp graphOp);

    protected void passEventToDPOR(ThreadEvent event) {
        ArrayList<ThreadEvent> tempEventList = new ArrayList<>();
        tempEventList.add(event);
        makeDPORFree();
        dpor.visit(currentGraph, tempEventList);
        updateGraphOps();
    }

    /**
     * Passes the given events to the {@link #dpor} model checker.
     *
     * @param events is the list of events that are going to be passed to the {@link #dpor} model
     *     checker.
     */
    protected void passEventToDPOR(ArrayList<ThreadEvent> events) {
        makeDPORFree();
        dpor.visit(currentGraph, events);
        updateGraphOps();
    }

    protected void updateCurrentGraph() {
        currentGraph = dpor.getExtendedGraph();
    }

    protected void updateGraphOps() {
        //        System.out.println("[OPT-DPOR Strategy Message] : The new graph operations
        // generated
        // are :");
        //        for (int i = 0; i < dpor.getNextOperations().size(); i++) {
        //            System.out.println(dpor.getNextOperations().get(i));
        //        }
        mcOpGraphs.addAll(dpor.getNextOperations());
    }

    /**
     * Represents the required strategy for the next start event.
     *
     * @param calleeThread is the thread that is going to be started.
     * @param callerThread is the thread that is going to call the start method of the calleeThread.
     */
    @Override
    public void nextStartEvent(Thread calleeThread, Thread callerThread) {
        if (guidingActivate) {
            StartEvent st = (StartEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(st);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The start event is passed to the
            // model
            // checker");
            StartEvent st = JmcRuntime.createStartEvent(calleeThread, callerThread);
            JmcRuntime.eventsRecord.add(st);
            passEventToDPOR(st);
        }
    }

    /**
     * @param mainStartEvent
     */
    @Override
    public void nextMainStartEvent(MainStartEvent mainStartEvent) {
        if (guidingActivate) {
            MainStartEvent mst = (MainStartEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(mst);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The main start event is passed to
            // the
            // model checker");
            JmcRuntime.eventsRecord.add(mainStartEvent);
            passEventToDPOR(mainStartEvent);
        }
    }

    /**
     * Represents the required strategy for the next enter monitor event.
     *
     * @param thread is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        // NO SUPPORT
    }

    /**
     * Represents the required strategy for the next exit monitor event.
     *
     * @param thread is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    @Override
    public void nextExitMonitorEvent(Thread thread, Object monitor) {
        // NO SUPPORT
    }

    /**
     * Represents the required strategy for the next join event.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    @Override
    public void nextJoinEvent(Thread joinReq, Thread joinRes) {
        if (guidingActivate) {
            JmcRuntime.getNextSerialNumber(
                    joinReq); // To update the serial number of the joinRq thread
            JoinEvent je = (JoinEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(je);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The join event is passed to the
            // model
            // checker");
            JoinEvent je = JmcRuntime.createJoinEvent(joinReq, joinRes);
            JmcRuntime.eventsRecord.add(je);
            passEventToDPOR(je);
        }
    }

    /**
     * Represents the required strategy for the next join request.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    @Override
    public Thread nextJoinRequest(Thread joinReq, Thread joinRes) {
        if (guidingActivate) {
            nextJoinEvent(joinReq, joinRes);
            return joinReq;
        } else {
            JmcRuntime.joinRequest.put(joinReq, joinRes);
            return pickNextReadyThread();
        }
    }

    /**
     * Handles the next enter monitor request of a given thread and monitor.
     *
     * <p>This method records the monitor request in the {@link JmcRuntime#monitorRequest}
     * map. It also checks for a deadlock between the threads in using the monitors. If a deadlock
     * is detected, the method sets the {@link JmcRuntime#deadlockHappened} flag to true and
     * the {@link JmcRuntime#executionFinished} flag to true. Otherwise, the method selects
     * the next thread to run.
     *
     * @param thread is the thread that is requested to enter the monitor.
     * @param monitor is the monitor that is requested to be entered by the thread.
     * @return the next random thread to run.
     */
    @Override
    public Thread nextEnterMonitorRequest(Thread thread, Object monitor) {
        // NO SUPPORT
        return null;
    }

    /**
     * Represents the required strategy for the next read event.
     *
     * @param receiveEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReceiveEvent(ReceiveEvent receiveEvent) {
        // NO SUPPORT
    }

    /**
     * Represents the required strategy for the next write event.
     *
     * @param sendEvent is the write event that is going to be executed.
     */
    @Override
    public void nextSendEvent(SendEvent sendEvent) {
        // NO SUPPORT
    }

    /**
     * @param receiveEvent
     * @return
     */
    @Override
    public boolean nextBlockingReceiveRequest(ReceiveEvent receiveEvent) {
        return false;
    }

    /**
     * Represents the required strategy for the next finish event.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextFinishEvent(Thread thread) {
        if (guidingActivate) {
            JmcRuntime.getNextSerialNumber(
                    thread); // To update the serial number of the thread
            FinishEvent fe = (FinishEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(fe);
            analyzeSuspendedThreadsForJoin(thread);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The finish event is passed to the
            // model
            // checker");
            FinishEvent fe = JmcRuntime.createFinishEvent(thread);
            JmcRuntime.eventsRecord.add(fe);
            passEventToDPOR(fe);
            analyzeSuspendedThreadsForJoin(thread);
        }
    }

    /**
     * Represents the required strategy for the next failure event.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextFailureEvent(Thread thread) {
        if (guidingActivate) {
            JmcRuntime.getNextSerialNumber(
                    thread); // To update the serial number of the thread
            FailureEvent fe = (FailureEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(fe);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The failure event is passed to the
            // model
            // checker");
            FailureEvent fe = JmcRuntime.createFailureEvent(thread);
            JmcRuntime.eventsRecord.add(fe);
            passEventToDPOR(fe);
        }
    }

    /**
     * Represents the required strategy for the next deadlock event.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextDeadlockEvent(Thread thread) {
        if (guidingActivate) {
            JmcRuntime.getNextSerialNumber(
                    thread); // To update the serial number of the thread
            DeadlockEvent de = (DeadlockEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(de);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The deadlock event is passed to the
            // model
            // checker");
            DeadlockEvent de = JmcRuntime.createDeadlockEvent(thread);
            JmcRuntime.eventsRecord.add(de);
            passEventToDPOR(de);
        }
    }

    /**
     * Represents the required strategy for the next finish request.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public Thread nextFinishRequest(Thread thread) {
        nextFinishEvent(thread);
        if (guidingActivate) {
            return pickNextGuidedThread();
        } else {
            return pickNextReadyThread();
        }
    }

    /**
     * Represents the required strategy for the next park request.
     *
     * @param thread is the thread that is going to be parked.
     */
    @Override
    public void nextParkRequest(Thread thread) {
        // NO SUPPORT
    }

    /**
     * @param conAssumeEvent
     */
    @Override
    public void nextConAssumeRequest(ConAssumeEvent conAssumeEvent) {
        if (guidingActivate) {
            ConAssumeEvent cae = (ConAssumeEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(cae);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The con assume event is passed to
            // the
            // model checker");
            JmcRuntime.eventsRecord.add(conAssumeEvent);
            passEventToDPOR(conAssumeEvent);
        }
    }

    /**
     * @param thread
     * @param symbolicOperation
     */
    @Override
    public void nextSymAssumeRequest(Thread thread, SymbolicOperation symbolicOperation) {
        if (guidingActivate) {
            handleGuidingSymAssumeOperationRequest(thread, symbolicOperation);
        } else {
            handleNewSymAssumeOperationRequest(thread, symbolicOperation);
        }
    }

    private void handleGuidingSymAssumeOperationRequest(
            Thread thread, SymbolicOperation symbolicOperation) {
        SymAssumeEvent guidingSymAssumeEvent = (SymAssumeEvent) guidingEvent;
        JmcRuntime.solverResult = guidingSymAssumeEvent.getResult();
        if (JmcRuntime.solverResult) {
            solver.updatePathSymbolicOperations(symbolicOperation);
            solver.push(symbolicOperation);
        }
        JmcRuntime.getNextSerialNumber(thread); // To update the serial number of the thread
        JmcRuntime.eventsRecord.add(guidingSymAssumeEvent);
    }

    /**
     * @param assumeBlockedEvent
     */
    @Override
    public void nextAssumeBlockedRequest(AssumeBlockedEvent assumeBlockedEvent) {
        if (guidingActivate) {
            AssumeBlockedEvent abe = (AssumeBlockedEvent) guidingEvent;
            JmcRuntime.eventsRecord.add(abe);
        } else {
            // System.out.println("[OPT-DPOR Strategy Message] : The assume blocked event is passed
            // to the
            // model checker");
            JmcRuntime.eventsRecord.add(assumeBlockedEvent);
            passEventToDPOR(assumeBlockedEvent);
        }
    }

    private void handleNewSymAssumeOperationRequest(
            Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymAssumeOperationRequest(symbolicOperation);
        System.out.println(
                "[OPT-DPOR Strategy Message] : The result of the symbolic assume operation is "
                        + JmcRuntime.solverResult);

        if (JmcRuntime.solverResult) {
            solver.updatePathSymbolicOperations(symbolicOperation);
        }

        SymAssumeEvent symAssumeEvent =
                JmcRuntime.createSymAssumeEvent(thread, symbolicOperation);
        JmcRuntime.eventsRecord.add(symAssumeEvent);
        passEventToDPOR(symAssumeEvent);
    }

    /**
     * Represents the required strategy for the next unpark request.
     *
     * @param unparkerThread is the thread that is going to unpark unparkeeThread.
     * @param unparkeeThread is the thread that is going to be unparked by unparkerThread.
     */
    @Override
    public void nextUnparkRequest(Thread unparkerThread, Thread unparkeeThread) {
        // NO SUPPORT
    }

    /**
     * Represents the required strategy for the next symbolic operation request.
     *
     * @param thread is the thread that is going to execute the symbolic operation.
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

    public void handleGuidingSymbolicOperationRequest(
            Thread thread, SymbolicOperation symbolicOperation) {
        SymExecutionEvent guidingSymExecutionEvent = (SymExecutionEvent) guidingEvent;
        JmcRuntime.solverResult = guidingSymExecutionEvent.getResult();
        solver.updatePathSymbolicOperations(symbolicOperation);
        JmcRuntime.getNextSerialNumber(thread); // To update the serial number of the thread
        guidingSymExecutionEvent.setFormula(symbolicOperation.getFormula().toString());
        if (guidingSymExecutionEvent.getResult()) {
            solver.push(symbolicOperation);
        } else {
            solver.push(solver.negateFormula(symbolicOperation.getFormula()));
        }
        JmcRuntime.eventsRecord.add(guidingSymExecutionEvent);
    }

    public void handleNewSymbolicOperationRequest(
            Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymbolicOperationRequest(symbolicOperation);

        System.out.println(
                "[OPT-DPOR Strategy Message] : The result of the symbolic arithmetic operation is "
                        + JmcRuntime.solverResult);
        solver.updatePathSymbolicOperations(symbolicOperation);
        SymExecutionEvent symExecutionEvent =
                JmcRuntime.createSymExecutionEvent(
                        thread, symbolicOperation.getFormula().toString(), solver.bothSatUnsat);
        JmcRuntime.eventsRecord.add(symExecutionEvent);
        passEventToDPOR(symExecutionEvent);
    }

    /**
     * @param sendEvent
     */
    @Override
    public void executeSendEvent(SendEvent sendEvent) {
        // NO SUPPORT
    }

    protected void makeDPORFree() {
        dpor.setExtendedGraph(null);
        dpor.setNextOperations(new ArrayList<GraphOp>());
        dpor.setTopoSort(null);
    }

    /**
     * Indicates whether the execution is done or not.
     *
     * @return true if the execution is done, otherwise false.
     */
    @Override
    public boolean done() {
        if (JmcRuntime.mcGraphOp.isEmpty()) {
            return true;
        } else {
            GraphOp graphOp = JmcRuntime.mcGraphOp.pop();
            handleGraphOp(graphOp);
            if (dpor.getExtendedGraph() == null || dpor.getTopoSort() == null) {
                if (!dpor.getNextOperations().isEmpty()) {
                    for (int i = 0; i < dpor.getNextOperations().size(); i++) {
                        GraphOp g = dpor.getNextOperations().get(i);
                        JmcRuntime.mcGraphOp.push(g);
                    }
                }
                return done();
            } else {
                JmcRuntime.guidingGraph = dpor.getExtendedGraph();
                System.out.println("[DPOR Message] : The guiding execution graph is loaded");
                System.out.println(
                        "[DPOR Message] : The guiding execution graph is : G_"
                                + JmcRuntime.guidingGraph.getId());
                JmcRuntime.guidingEvents = dpor.getTopoSort();
                //                if (!graphOp.getToBeAddedEvents().isEmpty()) {
                //                    ThreadEvent e =
                // RuntimeEnvironment.guidingGraph.getEventOrder().get(RuntimeEnvironment.guidingGraph.getEventOrder().size() - 1);
                //                    RuntimeEnvironment.guidingEvents.add(e);
                //                }
                if (!dpor.getNextOperations().isEmpty()) {
                    for (int i = 0; i < dpor.getNextOperations().size(); i++) {
                        GraphOp g = dpor.getNextOperations().get(i);
                        JmcRuntime.mcGraphOp.push(g);
                    }
                }
                // For debugging
                if (JmcRuntime.verbose) {
                    System.out.println("[DPOR Message] : The guiding events are :");
                    for (int i = 0; i < JmcRuntime.guidingEvents.size(); i++) {
                        ThreadEvent e = JmcRuntime.guidingEvents.get(i);
                        System.out.println(
                                i
                                        + "-"
                                        + e.getType()
                                        + "("
                                        + e.getTid()
                                        + ":"
                                        + e.getSerial()
                                        + ")");
                    }
                }
                return false;
            }
        }
    }

    abstract Thread pickNextGuidedThread();

    /**
     * Picks the next thread to run.
     *
     * @return the next thread to run.
     */
    @Override
    public Thread pickNextThread() {
        if (guidingActivate) {
            return pickNextGuidedThread();
        } else {
            return pickNextReadyThread();
        }
    }

    /** Saves the execution state. */
    @Override
    public void saveExecutionState() {
        if (!mcOpGraphs.isEmpty()) {
            for (int i = 0; i < mcOpGraphs.size(); i++) {
                // System.out.println("[OPT-DPOR Strategy Message] : The graph operation is " +
                // mcOpGraphs.get(i));
                JmcRuntime.mcGraphOp.push(mcOpGraphs.get(i));
            }
        }
        JmcRuntime.numOfGraphs = dpor.getGraphCounter();
    }

    /** Saves the buggy execution trace. */
    @Override
    public void saveBuggyExecutionTrace() {
        try {
            FileOutputStream fileOut = new FileOutputStream(buggyTracePath + buggyTraceFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(JmcRuntime.eventsRecord);
            out.close();
            fileOut.close();
            System.out.println(
                    "[OPT-DPOR Strategy Message] : Buggy execution trace is saved in "
                            + buggyTracePath
                            + buggyTraceFile);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    abstract void handleEmptyGuidingEvents();
}
