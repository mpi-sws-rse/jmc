package org.mpisws.checker.strategy;

import dpor.NewTrust;
import dpor.OptTrust;
import executionGraph.OptExecutionGraph;
import executionGraph.operations.GraphOp;
import executionGraph.operations.GraphOpType;
import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.solver.SymbolicSolver;
import org.mpisws.symbolic.SymbolicOperation;
import org.sosy_lab.java_smt.api.BooleanFormula;
import programStructure.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public abstract class OptDPORStrategy implements SearchStrategy {


    protected OptExecutionGraph currentGraph;

    protected CoverageGraph coverageGraph = null;

    protected boolean guidingActivate = false;

    protected int guidingThread;

    protected ArrayList<ThreadEvent> guidingEvents;

    protected ArrayList<GraphOp> mcOpGraphs = new ArrayList<>();

    protected ThreadEvent guidingEvent;

    protected String buggyTracePath;

    protected String buggyTraceFile;

    protected SymbolicSolver solver;

    protected NewTrust dpor;

    //protected OptTrust dpor;

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
        coverageGraph = new CoverageGraph();
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
        StartEvent st;
        if (guidingActivate) {
            st = (StartEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(st);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The start event is passed to the model checker");
            st = RuntimeEnvironment.createStartEvent(calleeThread, callerThread);
            RuntimeEnvironment.eventsRecord.add(st);
            passEventToDPOR(st);
        }
        updateCoverage(st);
    }

    /**
     * @param mainStartEvent
     */
    @Override
    public void nextMainStartEvent(MainStartEvent mainStartEvent) {
        if (guidingActivate) {
            MainStartEvent mst = (MainStartEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(mst);
            updateCoverage(mst);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The main start event is passed to the model checker");
            RuntimeEnvironment.eventsRecord.add(mainStartEvent);
            passEventToDPOR(mainStartEvent);
            updateCoverage(mainStartEvent);
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
            RuntimeEnvironment.getNextSerialNumber(joinReq); // To update the serial number of the joinRq thread
            JoinEvent je = (JoinEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(je);
            updateCoverage(je);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The join event is passed to the model checker");
            JoinEvent je = RuntimeEnvironment.createJoinEvent(joinReq, joinRes);
            RuntimeEnvironment.eventsRecord.add(je);
            passEventToDPOR(je);
            updateCoverage(je);
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
            RuntimeEnvironment.getNextSerialNumber(thread); // To update the serial number of the thread
            FinishEvent fe = (FinishEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(fe);
            analyzeSuspendedThreadsForJoin(thread);
            updateCoverage(fe);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The finish event is passed to the model checker");
            FinishEvent fe = RuntimeEnvironment.createFinishEvent(thread);
            RuntimeEnvironment.eventsRecord.add(fe);
            passEventToDPOR(fe);
            analyzeSuspendedThreadsForJoin(thread);
            updateCoverage(fe);
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
            RuntimeEnvironment.getNextSerialNumber(thread); // To update the serial number of the thread
            FailureEvent fe = (FailureEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(fe);
            updateCoverage(fe);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The failure event is passed to the model checker");
            FailureEvent fe = RuntimeEnvironment.createFailureEvent(thread);
            RuntimeEnvironment.eventsRecord.add(fe);
            passEventToDPOR(fe);
            updateCoverage(fe);
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
            RuntimeEnvironment.getNextSerialNumber(thread); // To update the serial number of the thread
            DeadlockEvent de = (DeadlockEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(de);
            updateCoverage(de);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The deadlock event is passed to the model checker");
            DeadlockEvent de = RuntimeEnvironment.createDeadlockEvent(thread);
            RuntimeEnvironment.eventsRecord.add(de);
            passEventToDPOR(de);
            updateCoverage(de);
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
            updateCoverage(cae);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The con assume event is passed to the model checker");
            RuntimeEnvironment.eventsRecord.add(conAssumeEvent);
            passEventToDPOR(conAssumeEvent);
            updateCoverage(conAssumeEvent);
        }
    }

    /**
     * @param thread
     * @param symbolicOperation
     */
    @Override
    public void nextSymAssertRequest(Thread thread, SymbolicOperation symbolicOperation) {
        if (guidingActivate) {
            handleGuidingSymAssertOperationRequest(thread, symbolicOperation);
        } else {
            handleNewSymAssertOperationRequest(thread, symbolicOperation);
        }
    }

    private void handleGuidingSymAssertOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        AssertEvent guidingAssertEvent = (AssertEvent) guidingEvent;
        RuntimeEnvironment.solverResult = guidingAssertEvent.getResult();
        // TODO: Implement the guided symbolic assert operation
        RuntimeEnvironment.getNextSerialNumber(thread); // To update the serial number of the thread
        RuntimeEnvironment.eventsRecord.add(guidingAssertEvent);
        updateCoverage(guidingAssertEvent);
    }

    private void handleNewSymAssertOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymbolicAssertOperationRequest(symbolicOperation);

        AssertEvent assertEvent = RuntimeEnvironment.createAssertEvent(thread, RuntimeEnvironment.solverResult);
        RuntimeEnvironment.eventsRecord.add(assertEvent);
        passEventToDPOR(assertEvent);
        updateCoverage(assertEvent);
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
//        System.out.println("[OPT-DPOR Strategy Message] : The result of the symbolic assume operation is " +
//                RuntimeEnvironment.solverResult);
        //System.out.println("[OPT-DPOR Strategy Message] : The symbolic operation of the guided assume is " + guidingSymAssumeEvent.getFormula());
        //System.out.println("[OPT-DPOR Strategy Message] : The result is " + guidingSymAssumeEvent.getResult());
//        System.out.println("[OPT-DPOR Strategy Message] : The symbolic operation of the new assume is " + symbolicOperation.getFormula());
        if (RuntimeEnvironment.solverResult && solver.resetProver) {
            solver.updatePathSymbolicOperations(symbolicOperation);
//            solver.push(symbolicOperation);
            solver.computeGuidedSymAssumeOperationRequest(symbolicOperation);
        }
        RuntimeEnvironment.getNextSerialNumber(thread); // To update the serial number of the thread
        RuntimeEnvironment.eventsRecord.add(guidingSymAssumeEvent);
        updateCoverage(guidingSymAssumeEvent);
    }

    /**
     * @param assumeBlockedEvent
     */
    @Override
    public void nextAssumeBlockedRequest(AssumeBlockedEvent assumeBlockedEvent) {
        if (guidingActivate) {
            AssumeBlockedEvent abe = (AssumeBlockedEvent) guidingEvent;
            RuntimeEnvironment.eventsRecord.add(abe);
            updateCoverage(abe);
        } else {
            //System.out.println("[OPT-DPOR Strategy Message] : The assume blocked event is passed to the model checker");
            RuntimeEnvironment.eventsRecord.add(assumeBlockedEvent);
            passEventToDPOR(assumeBlockedEvent);
            updateCoverage(assumeBlockedEvent);
        }
    }

    private void handleNewSymAssumeOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymAssumeOperationRequest(symbolicOperation);
//        System.out.println("[OPT-DPOR Strategy Message] : The result of the symbolic assume operation is " +
//                RuntimeEnvironment.solverResult);

        if (RuntimeEnvironment.solverResult) {
            solver.updatePathSymbolicOperations(symbolicOperation);
        }

        SymAssumeEvent symAssumeEvent = RuntimeEnvironment.createSymAssumeEvent(thread, symbolicOperation);
        RuntimeEnvironment.eventsRecord.add(symAssumeEvent);
        passEventToDPOR(symAssumeEvent);
        updateCoverage(symAssumeEvent);
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
        RuntimeEnvironment.getNextSerialNumber(thread); // To update the serial number of the thread
        //System.out.println("[OPT-DPOR Strategy Message] : The symbolic operation before is " + guidingSymExecutionEvent.getFormula());
        guidingSymExecutionEvent.setFormula(symbolicOperation.getFormula().toString());
        //System.out.println("[OPT-DPOR Strategy Message] : The guided symbolic operation is " + guidingSymExecutionEvent.getFormula());
        //System.out.println("[OPT-DPOR Strategy Message] : The result is " + guidingSymExecutionEvent.getResult());
        //System.out.println("[OPT-DPOR Strategy Message] : The event is " + guidingSymExecutionEvent.getTid() + " : " + guidingSymExecutionEvent.getSerial());
        if (solver.resetProver) {
            if (guidingSymExecutionEvent.getResult()) {
                solver.push(symbolicOperation);
            } else {
                solver.push(solver.negateFormula(symbolicOperation.getFormula()));
            }
        }
        RuntimeEnvironment.eventsRecord.add(guidingSymExecutionEvent);
        updateCoverage(guidingSymExecutionEvent);
    }

    public void handleNewSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymbolicOperationRequest(symbolicOperation);

//        System.out.println("[OPT-DPOR Strategy Message] : The result of the symbolic arithmetic operation is " +
//                RuntimeEnvironment.solverResult);
//        if (!RuntimeEnvironment.solverResult) {
//            symbolicOperation.setFormula(solver.negateFormula(symbolicOperation.getFormula()));
//        }
        solver.updatePathSymbolicOperations(symbolicOperation);
        SymExecutionEvent symExecutionEvent = RuntimeEnvironment.createSymExecutionEvent(thread,
                symbolicOperation, solver.bothSatUnsat);
        RuntimeEnvironment.eventsRecord.add(symExecutionEvent);
        passEventToDPOR(symExecutionEvent);
        updateCoverage(symExecutionEvent);
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
            //System.out.println("[OPT-DPOR Strategy Message] : The next graph operation is " + graphOp.getType());
            handleGraphOp(graphOp);
            if (dpor.getExtendedGraph() == null || dpor.getTopoSort() == null) {
                if (!dpor.getNextOperations().isEmpty()) {
                    for (int i = 0; i < dpor.getNextOperations().size(); i++) {
                        GraphOp g = dpor.getNextOperations().get(i);
                        RuntimeEnvironment.mcGraphOp.push(g);
                    }
                    dpor.getNextOperations().clear();
                }
                return done();
            } else {
                RuntimeEnvironment.guidingGraph = dpor.getExtendedGraph();
//                System.out.println("[DPOR Message] : The guiding execution graph is loaded");
//                System.out.println("[DPOR Message] : The guiding execution graph is : G_" + RuntimeEnvironment.guidingGraph.getId());
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
                    dpor.getNextOperations().clear();
                }
                // For debugging
//                if (RuntimeEnvironment.verbose) {
//                System.out.println("[DPOR Message] : The guiding events are :");
//                for (int i = 0; i < RuntimeEnvironment.guidingEvents.size(); i++) {
//                    ThreadEvent e = RuntimeEnvironment.guidingEvents.get(i);
//                    System.out.println(i + "-" + e.getType() + "(" + e.getTid() + ":" + e.getSerial() + ")");
//                }
//
//                System.out.println("[DPOR Message] : The evetns order of graph is :");
//                RuntimeEnvironment.guidingGraph.printEventOrder();
//
//                System.out.println("[DPOR Message] : The PO order of g is :");
//                RuntimeEnvironment.guidingGraph.printPO();
//
//                System.out.println("[DPOR Message] : The rf order of g is :");
//                RuntimeEnvironment.guidingGraph.printRf();
//
//                System.out.println("[DPOR Message] : The co order of g is :");
//                RuntimeEnvironment.guidingGraph.printCO();
//
//                System.out.println("[DPOR Message] : The jt order of g is :");
//                RuntimeEnvironment.guidingGraph.printJT();
//
//                System.out.println("[DPOR Message] : The st order of g is :");
//                RuntimeEnvironment.guidingGraph.printST();
//
//                System.out.println("[DPOR Message] : The tc order of g is :");
//                RuntimeEnvironment.guidingGraph.printTC();
//
//                System.out.println("[DPOR Message] : The reads order of g is :");
//                RuntimeEnvironment.guidingGraph.printReads();
//
//                System.out.println("[DPOR Message] : The sym order of g is :");
//                RuntimeEnvironment.guidingGraph.prinSymEx();
//                }
                if (solver != null && solver.size() != 0) {
                    if (graphOp.getType() == GraphOpType.FR_NEG_SYM) {
                        solver.pop();
                        SymExecutionEvent symEvent = (SymExecutionEvent) graphOp.getFirstEvent();
                        if (symEvent.getResult()) {
                            solver.push(symEvent.getSymbolicOp().getFormula());
                        } else {
                            solver.push(solver.negateFormula(symEvent.getSymbolicOp().getFormula()));
                        }
                        solver.solveAndUpdateModelSymbolicVariables();

//                        SymExecutionEvent symEvent = (SymExecutionEvent) graphOp.getFirstEvent();
//                        if (symEvent.getResult()) {
//                            BooleanFormula f = solver.negateFormula(symEvent.getSymbolicOp().getFormula());
//                            //int index = solver.stack.indexOf(f);
//                            int index = -1;
//                            for (int i = solver.stack.size() - 1; i >= 0; i--) {
//                                if (solver.stack.get(i).equals(f)) {
//                                    index = i;
//                                    break;
//                                }
//                            }
//                            if (index == -1) {
//                                System.out.println("[OPT-DPOR Strategy Message] : The formula is not found in the stack");
//                                System.exit(0);
//                            }
//                            if (index == solver.stack.size() - 1) {
//                                solver.pop();
//                                solver.push(symEvent.getSymbolicOp().getFormula());
//                            } else {
//                                // Pop all the formulas till the index. Then pop the formula at the index and push back the formulas popped.
//                                ArrayList<BooleanFormula> temp = new ArrayList<>();
//                                while (solver.stack.size() - 1 > index) {
//                                    temp.add(solver.pop());
//                                }
//                                solver.pop();
//                                while (!temp.isEmpty()) {
//                                    solver.push(temp.remove(temp.size() - 1));
//                                }
//                                solver.push(symEvent.getSymbolicOp().getFormula());
//                            }
//                            //solver.push(symEvent.getSymbolicOp().getFormula());
//                        } else {
//                            int index = solver.stack.indexOf(symEvent.getSymbolicOp().getFormula());
//                            if (index == -1) {
//                                System.out.println("[OPT-DPOR Strategy Message] : The formula is not found in the stack");
//                                System.exit(0);
//                            }
//                            if (index == solver.stack.size() - 1) {
//                                solver.pop();
//                                solver.push(solver.negateFormula(symEvent.getSymbolicOp().getFormula()));
//                            } else {
//                                // Pop all the formulas till the index. Then pop the formula at the index and push back the formulas popped.
//                                ArrayList<BooleanFormula> temp = new ArrayList<>();
//                                while (solver.stack.size() - 1 > index) {
//                                    temp.add(solver.pop());
//                                }
//                                solver.pop();
//                                while (!temp.isEmpty()) {
//                                    solver.push(temp.remove(temp.size() - 1));
//                                }
//                                solver.push(solver.negateFormula(symEvent.getSymbolicOp().getFormula()));
//                            }
//                            //solver.push(solver.negateFormula(symEvent.getSymbolicOp().getFormula()));
//                        }
//                        solver.solveAndUpdateModelSymbolicVariables();
                    }
                }
                if (solver != null && solver.size() == 0) {
                    solver.resetProver = true;
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

    /**
     * Saves the execution state.
     */
    @Override
    public void saveExecutionState() {
        if (!mcOpGraphs.isEmpty()) {
            //System.out.println("[OPT-DPOR Strategy Message] : The execution finished, saving execution trace");
            for (int i = 0; i < mcOpGraphs.size(); i++) {
                //System.out.println("[OPT-DPOR Strategy Message] : The graph operation is " + mcOpGraphs.get(i).getType());
                RuntimeEnvironment.mcGraphOp.push(mcOpGraphs.get(i));
            }
        }
        RuntimeEnvironment.numOfGraphs = dpor.getGraphCounter();

        if (coverageGraph != null && !RuntimeEnvironment.isExecutionBlocked) {
            String g = coverageGraph.toString();
            // Serialize the string
            String fileName = "G_" + RuntimeEnvironment.coverage + ".txt";
            String filePath = "src/main/resources/coverageTrust/" + fileName;
            try {
                Files.write(Paths.get(filePath), g.getBytes());
                //System.out.println("[OPT-DPOR Strategy Message] : The coverage graph is saved in " + filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String json = coverageGraph.toJson();
            // Serialize the string
            fileName = "G_" + RuntimeEnvironment.coverage + ".json";
            filePath = "src/main/resources/coverageTrust/" + fileName;
            try {
                Files.write(Paths.get(filePath), json.getBytes());
                //System.out.println("[OPT-DPOR Strategy Message] : The coverage graph is saved in " + filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            RuntimeEnvironment.coverage++;
            RuntimeEnvironment.coveregeSet.add(g);
        }
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

    protected void updateCoverage(ThreadEvent event) {
        // Add PO
        coverageGraph.addPo(event);
        switch (event.getType()) {
            case READ, READ_EX:
                coverageGraph.addRf(event);
                break;
            case WRITE, WRITE_EX:
                coverageGraph.addCo(event);
                break;
            default:
                break;
        }
    }
}
