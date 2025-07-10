package org.mpisws.checker.strategy;


import dpor.NewTrust;
import dpor.RevisitState;
import executionGraph.OptExecutionGraph;
import executionGraph.operations.GraphOp;
import executionGraph.operations.GraphOpType;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.solver.ProverState;
import org.mpisws.symbolic.SymArrayVariable;
import org.mpisws.symbolic.SymBoolVariable;
import org.mpisws.symbolic.SymIntVariable;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import programStructure.*;
import scala.concurrent.impl.FutureConvertersImpl;

import java.util.*;

public class OptTrustStrategy extends OptDPORStrategy {

    /**
     *
     */
    public OptTrustStrategy() {
        super();
        //dpor = new OptTrust(executionGraphsPath, RuntimeEnvironment.verbose);
        dpor = new NewTrust(executionGraphsPath, RuntimeEnvironment.verbose);
        dpor.setGraphCounter(RuntimeEnvironment.numOfGraphs);
        if (solver != null) {
            dpor.setProverId(solver.getProverId());
        }
    }

    private void updateProver(int id) {
        if (id == 0) {
            System.out.println("[OPT-Trust Strategy] The prover id is 0");
            System.exit(0);
        }

        if (solver.getProverId() != id) {
            ProverState p = RuntimeEnvironment.proverMap.get(id);

            if (p == null) {
                System.out.println("[OPT-Trust Strategy] The prover is null");
                System.out.println("[OPT-Trust Strategy] The prover id is :" + id);
                System.exit(0);
            }

            solver.setProver(p, id);
        }
    }

    /**
     * @param graphOp
     */
    @Override
    protected void handleGraphOp(GraphOp graphOp) {
        RevisitState state = null;
        switch (graphOp.getType()) {
            case FR_L_W:
                if (solver != null) {
                    state = new RevisitState(null, 0, null);
                    updateProver(graphOp.getProverId());
                    dpor.setProverId(graphOp.getProverId());
                }
                makeDPORFree();

                RuntimeEnvironment.frCounter++;
                dpor.processFR_L_W(graphOp.getG(), (WriteEvent) graphOp.getFirstEvent(), graphOp.getToBeAddedEvents(), state);
                break;
            case FR_W_W:
                if (solver != null) {
                    state = new RevisitState(null, 0, null);
                    updateProver(graphOp.getProverId());
                    dpor.setProverId(graphOp.getProverId());
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_W_W(graphOp.getG(), (WriteEvent) graphOp.getFirstEvent(), (WriteEvent) graphOp.getSecondEvent(), graphOp.getToBeAddedEvents(), state);
                break;
            case FR_R_W:
                if (solver != null) {
                    state = new RevisitState(null, 0, null);
                    updateProver(graphOp.getProverId());
                    dpor.setProverId(graphOp.getProverId());
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_R_W(graphOp.getG(), (ReadEvent) graphOp.getFirstEvent(), (WriteEvent) graphOp.getSecondEvent(), state);
                break;
            case FR_RX_W:
                if (solver != null) {
                    state = new RevisitState(null, 0, null);
                    updateProver(graphOp.getProverId());
                    dpor.setProverId(graphOp.getProverId());
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_RX_W(graphOp.getG(), (ReadExEvent) graphOp.getFirstEvent(), (WriteEvent) graphOp.getSecondEvent(), graphOp.getToBeAddedEvents(), state);
                break;
            case FR_NEG_SYM:
                if (solver != null) {
                    state = new RevisitState(null, 0, null);
                    updateProver(graphOp.getProverId());
                    dpor.setProverId(graphOp.getProverId());
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_neg_sym(graphOp.getG(), (SymExecutionEvent) graphOp.getFirstEvent(), state);
                break;
            case CREATE_PROVER:
                RuntimeEnvironment.brCounter++;
                ProverState prover = solver.createNewProver();
                for (Map.Entry<String, SymIntVariable> entry : solver.symIntVariableMap.entrySet()) {
                    prover.symIntVariableMap.put(entry.getKey(), entry.getValue().deepCopy());
                }
                for (Map.Entry<String, SymBoolVariable> entry : solver.symBoolVariableMap.entrySet()) {
                    prover.symBoolVariableMap.put(entry.getKey(), entry.getValue().deepCopy());
                }
                for (Map.Entry<String, SymArrayVariable> entry : solver.symArrayVariableMap.entrySet()) {
                    prover.symArrayVariableHashMap.put(entry.getKey(), entry.getValue().deepCopy());
                }
                int newProverId = RuntimeEnvironment.maxProverId;
                RuntimeEnvironment.proverMap.put(newProverId, prover);
                if (solver != null) {
                    updateProver(newProverId);
                }
                int index = RuntimeEnvironment.mcGraphOp.size() - 1;
                while (RuntimeEnvironment.mcGraphOp.get(index).getType() != GraphOpType.REMOVE_PROVER) {
                    RuntimeEnvironment.mcGraphOp.get(index).setProverId(newProverId);
                    index--;
                }
                RuntimeEnvironment.mcGraphOp.get(index).setProverId(newProverId); // For REMOVE_PROVER

                dpor.setProverId(newProverId);
                makeDPORFree();
                break;
            case REMOVE_PROVER:
                makeDPORFree();
                int proverId = graphOp.getProverId();
                if (proverId == 0) {
                    System.out.println("[OPT-Trust Strategy] The prover id is 0");
                    System.exit(0);
                }
                ProverState proverToRemove = RuntimeEnvironment.proverMap.get(proverId);
                if (proverToRemove == null) {
                    System.out.println("[OPT-Trust Strategy] The prover is null");
                    System.exit(0);
                }
                RuntimeEnvironment.proverMap.remove(proverId);
                solver.resetProver(proverToRemove.prover);
                proverToRemove.clear();
                RuntimeEnvironment.proverPool.add(proverToRemove);
                break;
            case BR_W_R:
                makeDPORFree();
                if (solver != null) {
                    state = new RevisitState(null, 0, new ArrayList<>());
                    updateProver(graphOp.getProverId());
                    dpor.setProverId(graphOp.getProverId());
                }

                dpor.processBR_W_R(graphOp.getG(), (WriteEvent) graphOp.getFirstEvent(), state);

                OptExecutionGraph dump_g = graphOp.getG();
                if (state != null && state.getNumOfPop() > 0) {
                    int numOfPop = state.getNumOfPop();
                    while (numOfPop > 0) {
                        solver.pop();
                        numOfPop--;
                    }
                    state.setNumOfPop(0);
                }

                if (state != null && state.getDeleted().size() > 0) {
                    int nextRemove = 0;
                    List<GraphOp> nextOps = new ArrayList<>();
                    for (int i = 0; i < state.getDeleted().size(); i++) {
                        Set<SymExecutionEvent> deletedSymEvent = state.getDeleted().get(i);
                        boolean isMaximal = true;
                        if (deletedSymEvent.size() > 0) {
                            solver.resetProver();
                            for (int j = 0; j < dump_g.getSymEvents().size(); j++) {
                                ThreadEvent symEvent = dump_g.getSymEvents().get(j);
                                if (symEvent instanceof SymExecutionEvent sym) {
                                    if (!deletedSymEvent.contains(sym)) {
                                        if (sym.getResult()) {
                                            solver.push(sym.getSymbolicOp().getFormula());
                                        } else {
                                            solver.push(solver.negateFormula(sym.getSymbolicOp().getFormula()));
                                        }
                                    }
                                }
                            }

                            for (int k = 0; k < dump_g.getSymEvents().size(); k++) {
                                ThreadEvent symEvent = dump_g.getSymEvents().get(k);
                                if (symEvent instanceof SymExecutionEvent sym) {
                                    if (deletedSymEvent.contains(sym)) {
                                        boolean SAT = false;
                                        boolean UNSAT = false;
                                        SAT = solver.solveSymbolicFormula(sym.getSymbolicOp());
                                        solver.pop();
                                        UNSAT = solver.disSolveSymbolicFormula(sym.getSymbolicOp());
                                        solver.pop();

                                        if (SAT && UNSAT) {
                                            if (!sym.getResult()) {
                                                isMaximal = false;
                                                break;
                                            }
                                        }

                                        if (SAT) {
                                            solver.push(sym.getSymbolicOp().getFormula());
                                        } else if (UNSAT) {
                                            solver.push(solver.negateFormula(sym.getSymbolicOp().getFormula()));
                                        } else {
                                            System.out.println("[OPT-Trust Strategy] The sym result is neither SAT nor UNSAT");
                                            System.out.println("[OPT-Trust Strategy] The sym event is :" + sym);
                                            System.out.println("[OPT-Trust Strategy] The sym formula is :" + sym.getSymbolicOp().getFormula());
                                            System.exit(0);
                                        }
                                    }
                                }
                            }
                        }

                        if (isMaximal) {
                            int opIndex = nextRemove;
                            while (true) {
                                nextOps.add(dpor.getNextOperations().get(opIndex));
                                opIndex++;
                                if (dpor.getNextOperations().size() == opIndex || dpor.getNextOperations().get(opIndex).getType() == GraphOpType.REMOVE_PROVER) {
                                    nextRemove = opIndex;
                                    break;
                                }
                            }
                        } else {
                            nextRemove++;
                            while (nextRemove < dpor.getNextOperations().size()) {
                                if (dpor.getNextOperations().get(nextRemove).getType() == GraphOpType.REMOVE_PROVER) {
                                    break;
                                }
                                nextRemove++;
                            }
                        }
                    }

                    dpor.getNextOperations().clear();
                    dpor.getNextOperations().addAll(nextOps);


                    solver.resetProver();
                    for (int t = 0; t < dump_g.getSymEvents().size(); t++) {
                        ThreadEvent symEvent1 = dump_g.getSymEvents().get(t);
                        if (symEvent1 instanceof SymExecutionEvent sym1) {
                            if (sym1.getResult()) {
                                solver.push(sym1.getSymbolicOp().getFormula());
                            } else {
                                solver.push(solver.negateFormula(sym1.getSymbolicOp().getFormula()));
                            }
                        }
                    }

                }

                break;
        }
        if (state != null && solver != null && solver.size() != 0) {

            int numOfPop = state.getNumOfPop();

            while (numOfPop > 0) {
                solver.pop();
                numOfPop--;
            }
        }
    }

    /**
     * @return
     */
    @Override
    Thread pickNextGuidedThread() {
        if (guidingEvents.isEmpty()) {
            handleEmptyGuidingEvents();
            if (solver != null && solver.resetProver) {
                ArrayList<ThreadEvent> syms = currentGraph.getSymEvents();
                for (int i = 0; i < syms.size(); i++) {
                    ThreadEvent symEvent = syms.get(i);
                    if (symEvent instanceof SymExecutionEvent sym) {
                        if (sym.getResult()) {
                            solver.push(sym.getSymbolicOp().getFormula());
                        } else {
                            solver.push(solver.negateFormula(sym.getSymbolicOp().getFormula()));
                        }
                    }
                }
                solver.solveAndUpdateModelSymbolicVariables();
                solver.resetProver = false;
            }
            return pickNextReadyThread();
        }

        guidingEvent = guidingEvents.remove(0);

        if (guidingEvent instanceof StartEvent) {
            guidingThread = ((StartEvent) guidingEvent).getCallerThread();
        } else {
            guidingThread = guidingEvent.getTid();
        }

        return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
    }

    @Override
    public void handleEmptyGuidingEvents() {
        guidingActivate = false;
        updateWritesMap();
        updateReadsMap();
    }

    private void updateWritesMap() {
        HashMap<Location, ArrayList<WriteEvent>> oldWritesMap = currentGraph.getWrites();
        HashMap<Location, ArrayList<WriteEvent>> newWritesMap = new HashMap<>();

        for (Map.Entry<Location, ArrayList<WriteEvent>> entry : oldWritesMap.entrySet()) {
            ArrayList<WriteEvent> writes = entry.getValue();
            Location loc = writes.get(0).getLoc();
            newWritesMap.put(loc, writes);
        }

        currentGraph.setWrites(newWritesMap);
    }

    private void updateReadsMap() {
        HashMap<Location, ArrayList<ReadEvent>> oldReadsMap = currentGraph.getReads();
        HashMap<Location, ArrayList<ReadEvent>> newReadsMap = new HashMap<>();

        for (Map.Entry<Location, ArrayList<ReadEvent>> entry : oldReadsMap.entrySet()) {
            ArrayList<ReadEvent> reads = entry.getValue();
            Location loc = reads.get(0).getLoc();
            newReadsMap.put(loc, reads);
        }

        currentGraph.setReads(newReadsMap);
    }

    /**
     * @param thread
     * @param readExEvent
     * @param writeExEvent
     * @return
     */
    @Override
    public Thread nextCasRequest(Thread thread, ReadExEvent readExEvent, WriteExEvent writeExEvent) {
        if (guidingActivate) {
            ReadExEvent readEx = (ReadExEvent) guidingEvent;
            readExEvent.setInternalValue(readEx.getInternalValue());

            WriteEvent wr = currentGraph.getRf().get(readEx);
            currentGraph.removeRf(readEx);
            readEx.setLoc(readExEvent.getLoc());
            currentGraph.addRF(readEx, wr);
            RuntimeEnvironment.eventsRecord.add(readEx);
            updateCoverage(readEx);

            for (int i = 0; i < guidingEvents.size(); i++) {
                if (guidingEvents.get(i) instanceof WriteExEvent && guidingEvents.get(i).getTid() == readEx.getTid() && guidingEvents.get(i).getSerial() == readEx.getSerial() + 1) {
                    guidingEvent = guidingEvents.remove(i);
                    break;
                }
            }

            WriteExEvent writeEx = (WriteExEvent) guidingEvent;

            writeExEvent.setConditionValue(writeEx.getConditionValue());
            writeEx.setLoc(writeExEvent.getLoc());
            writeExEvent.setOperationSuccess(writeEx.getOperationSuccess());

            RuntimeEnvironment.eventsRecord.add(writeEx);
            updateCoverage(writeEx);
            return pickNextThread();
        } else {
            ArrayList<ThreadEvent> events = new ArrayList<>();
            events.add(readExEvent);
            RuntimeEnvironment.eventsRecord.add(readExEvent);
            events.add(writeExEvent);
            RuntimeEnvironment.eventsRecord.add(writeExEvent);
            passEventToDPOR(events);
            updateCoverage(readExEvent);
            updateCoverage(writeExEvent);
            return thread;
        }
    }

    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        if (guidingActivate) {
            ReadEvent read = (ReadEvent) guidingEvent;
            WriteEvent wr = currentGraph.getRf().get(read);
            currentGraph.removeRf(read);
            read.setLoc(readEvent.getLoc());
            currentGraph.addRF(read, wr);
            RuntimeEnvironment.eventsRecord.add(read);
            updateCoverage(read);
        } else {
            RuntimeEnvironment.eventsRecord.add(readEvent);
            passEventToDPOR(readEvent);
            updateCoverage(readEvent);
        }
    }

    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        if (guidingActivate) {
            WriteEvent write = (WriteEvent) guidingEvent;
            write.setLoc(writeEvent.getLoc());
            RuntimeEnvironment.eventsRecord.add(write);
            updateCoverage(write);
        } else {
            RuntimeEnvironment.eventsRecord.add(writeEvent);
            passEventToDPOR(writeEvent);
            updateCoverage(writeEvent);
        }
    }
}
