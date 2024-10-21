package org.mpisws.checker.strategy;

import dpor.OptTrust;
import executionGraph.OptExecutionGraph;
import executionGraph.operations.GraphOp;
import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.RuntimeEnvironment;
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
        buggyTracePath = RuntimeEnvironment.buggyTracePath;
        buggyTraceFile = RuntimeEnvironment.buggyTraceFile;
        executionGraphsPath = RuntimeEnvironment.executionGraphsPath;
        solver = RuntimeEnvironment.solver;
        initCurrentGraph();
    }


    private void initCurrentGraph() {
        if (RuntimeEnvironment.guidingGraph == null) {
            currentGraph = new OptExecutionGraph();
        } else {
            currentGraph = RuntimeEnvironment.guidingGraph;
            guidingEvents = RuntimeEnvironment.guidingEvents;
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
     * @param events is the list of events that are going to be passed to the {@link #dpor} model checker.
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
//        System.out.println("[OPT-DPOR Strategy Message] : The new graph operations generated are :");
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
            RuntimeEnvironment.eventsRecord.add(st);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The start event is passed to the model checker");
            StartEvent st = RuntimeEnvironment.createStartEvent(calleeThread, callerThread);
            RuntimeEnvironment.eventsRecord.add(st);
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
            RuntimeEnvironment.eventsRecord.add(mst);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The main start event is passed to the model checker");
            RuntimeEnvironment.eventsRecord.add(mainStartEvent);
            passEventToDPOR(mainStartEvent);
        }
    }

    /**
     * Represents the required strategy for the next enter monitor event.
     *
     * @param thread  is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        // NO SUPPORT
    }

    /**
     * Represents the required strategy for the next exit monitor event.
     *
     * @param thread  is the thread that is going to exit the monitor.
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
            JoinEvent je = (JoinEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(je);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The join event is passed to the model checker");
            JoinEvent je = RuntimeEnvironment.createJoinEvent(joinReq, joinRes);
            RuntimeEnvironment.eventsRecord.add(je);
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
            RuntimeEnvironment.joinRequest.put(joinReq, joinRes);
            return pickNextReadyThread();
        }
    }

    /**
     * Handles the next enter monitor request of a given thread and monitor.
     * <p>
     * This method records the monitor request in the {@link RuntimeEnvironment#monitorRequest} map. It also checks
     * for a deadlock between the threads in using the monitors. If a deadlock is detected, the method sets the
     * {@link RuntimeEnvironment#deadlockHappened} flag to true and the {@link RuntimeEnvironment#executionFinished}
     * flag to true. Otherwise, the method selects the next thread to run.
     * </p>
     *
     * @param thread  is the thread that is requested to enter the monitor.
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
            FinishEvent fe = (FinishEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(fe);
            analyzeSuspendedThreadsForJoin(thread);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The finish event is passed to the model checker");
            FinishEvent fe = RuntimeEnvironment.createFinishEvent(thread);
            RuntimeEnvironment.eventsRecord.add(fe);
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
            FailureEvent fe = (FailureEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(fe);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The failure event is passed to the model checker");
            FailureEvent fe = RuntimeEnvironment.createFailureEvent(thread);
            RuntimeEnvironment.eventsRecord.add(fe);
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
            DeadlockEvent de = (DeadlockEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(de);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The deadlock event is passed to the model checker");
            DeadlockEvent de = RuntimeEnvironment.createDeadlockEvent(thread);
            RuntimeEnvironment.eventsRecord.add(de);
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
            RuntimeEnvironment.eventsRecord.add(cae);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The con assume event is passed to the model checker");
            RuntimeEnvironment.eventsRecord.add(conAssumeEvent);
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

    private void handleGuidingSymAssumeOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        SymAssumeEvent guidingSymAssumeEvent = (SymAssumeEvent) guidingEvent;
        RuntimeEnvironment.solverResult = guidingSymAssumeEvent.getResult();
        if (RuntimeEnvironment.solverResult) {
            solver.updatePathSymbolicOperations(symbolicOperation);
            solver.push(symbolicOperation);
        }
        RuntimeEnvironment.eventsRecord.add(guidingSymAssumeEvent);
    }

    /**
     * @param assumeBlockedEvent
     */
    @Override
    public void nextAssumeBlockedRequest(AssumeBlockedEvent assumeBlockedEvent) {
        if (guidingActivate) {
            AssumeBlockedEvent abe = (AssumeBlockedEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(abe);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The assume blocked event is passed to the model checker");
            RuntimeEnvironment.eventsRecord.add(assumeBlockedEvent);
            passEventToDPOR(assumeBlockedEvent);
        }
    }

    private void handleNewSymAssumeOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymAssumeOperationRequest(symbolicOperation);
        System.out.println("[OPT-DPOR Strategy Message] : The result of the symbolic assume operation is " +
                RuntimeEnvironment.solverResult);

        if (RuntimeEnvironment.solverResult) {
            solver.updatePathSymbolicOperations(symbolicOperation);
        }

        SymAssumeEvent symAssumeEvent = RuntimeEnvironment.createSymAssumeEvent(thread, symbolicOperation);
        RuntimeEnvironment.eventsRecord.add(symAssumeEvent);
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
     * @param thread            is the thread that is going to execute the symbolic operation.
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

    public void handleGuidingSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        SymExecutionEvent guidingSymExecutionEvent = (SymExecutionEvent) guidingEvent;
        RuntimeEnvironment.solverResult = guidingSymExecutionEvent.getResult();
        solver.updatePathSymbolicOperations(symbolicOperation);
        if (guidingSymExecutionEvent.getResult()) {
            solver.push(symbolicOperation);
        } else {
            solver.push(solver.negateFormula(symbolicOperation.getFormula()));
        }
        RuntimeEnvironment.eventsRecord.add(guidingSymExecutionEvent);
    }

    public void handleNewSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymbolicOperationRequest(symbolicOperation);

        System.out.println("[OPT-DPOR Strategy Message] : The result of the symbolic arithmetic operation is " +
                RuntimeEnvironment.solverResult);
        solver.updatePathSymbolicOperations(symbolicOperation);
        SymExecutionEvent symExecutionEvent = RuntimeEnvironment.createSymExecutionEvent(thread,
                symbolicOperation.getFormula().toString(), solver.bothSatUnsat);
        RuntimeEnvironment.eventsRecord.add(symExecutionEvent);
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
        if (RuntimeEnvironment.mcGraphOp.isEmpty()) {
            return true;
        } else {
            GraphOp graphOp = RuntimeEnvironment.mcGraphOp.pop();
            handleGraphOp(graphOp);
            if (dpor.getExtendedGraph() == null || dpor.getTopoSort() == null) {
                if (!dpor.getNextOperations().isEmpty()) {
                    for (int i = 0; i < dpor.getNextOperations().size(); i++) {
                        GraphOp g = dpor.getNextOperations().get(i);
                        RuntimeEnvironment.mcGraphOp.push(g);
                    }
                }
                return done();
            } else {
                RuntimeEnvironment.guidingGraph = dpor.getExtendedGraph();
                System.out.println("[DPOR Message] : The guiding execution graph is loaded");
                System.out.println("[DPOR Message] : The guiding execution graph is : G_" + RuntimeEnvironment.guidingGraph.getId());
                RuntimeEnvironment.guidingEvents = dpor.getTopoSort();
//                if (!graphOp.getToBeAddedEvents().isEmpty()) {
//                    ThreadEvent e = RuntimeEnvironment.guidingGraph.getEventOrder().get(RuntimeEnvironment.guidingGraph.getEventOrder().size() - 1);
//                    RuntimeEnvironment.guidingEvents.add(e);
//                }
                if (!dpor.getNextOperations().isEmpty()) {
                    for (int i = 0; i < dpor.getNextOperations().size(); i++) {
                        GraphOp g = dpor.getNextOperations().get(i);
                        RuntimeEnvironment.mcGraphOp.push(g);
                    }
                }
//                System.out.println("[DPOR Message] : The guiding events are :");
//                for (int i = 0; i < RuntimeEnvironment.guidingEvents.size(); i++) {
//                    ThreadEvent e = RuntimeEnvironment.guidingEvents.get(i);
//                    System.out.println(i + "-" + e.getType() + "(" + e.getTid() + ":" + e.getSerial() + ")");
//                }
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

    /**
     * Saves the execution state.
     */
    @Override
    public void saveExecutionState() {
        if (!mcOpGraphs.isEmpty()) {
            for (int i = 0; i < mcOpGraphs.size(); i++) {
                //System.out.println("[OPT-DPOR Strategy Message] : The graph operation is " + mcOpGraphs.get(i));
                RuntimeEnvironment.mcGraphOp.push(mcOpGraphs.get(i));
            }
        }
        RuntimeEnvironment.numOfGraphs = dpor.getGraphCounter();
    }

    /**
     * Saves the buggy execution trace.
     */
    @Override
    public void saveBuggyExecutionTrace() {
        try {
            FileOutputStream fileOut = new FileOutputStream(buggyTracePath + buggyTraceFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(RuntimeEnvironment.eventsRecord);
            out.close();
            fileOut.close();
            System.out.println("[OPT-DPOR Strategy Message] : Buggy execution trace is saved in " + buggyTracePath +
                    buggyTraceFile);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    abstract void handleEmptyGuidingEvents();
}
