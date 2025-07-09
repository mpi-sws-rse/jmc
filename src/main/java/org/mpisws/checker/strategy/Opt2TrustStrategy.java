package org.mpisws.checker.strategy;

import dpor.OptTrust;
import dpor.RevisitState;
import executionGraph.operations.GraphOp;
import org.mpisws.runtime.RuntimeEnvironment;
import org.sosy_lab.java_smt.api.BooleanFormula;
import programStructure.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class Opt2TrustStrategy extends Opt2DPORStrategy {

    public Opt2TrustStrategy() {
        super();
        dpor = new OptTrust(executionGraphsPath, RuntimeEnvironment.verbose);
        dpor.setGraphCounter(RuntimeEnvironment.numOfGraphs);
    }

    /**
     * @param graphOp
     */
    @Override
    protected void handleGraphOp(GraphOp graphOp) {
        RevisitState state = null;
        switch (graphOp.getType()) {
            case FR_L_W:
                //System.out.println("[OPT-Trust Strategy] The graphOp is :" + graphOp.getType() + " (" + graphOp.getFirstEvent().getTid() + ":" + graphOp.getFirstEvent().getSerial() + ") with prover id :" + graphOp.getProverId());
                if (solver != null) {
                    state = new RevisitState(new ArrayList<>(), 0, null);
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_L_W(graphOp.getG(), (WriteEvent) graphOp.getFirstEvent(), graphOp.getToBeAddedEvents(), state);
                break;
            case FR_W_W:
                //System.out.println("[OPT-Trust Strategy] The graphOp is :" + graphOp.getType() + " (" + graphOp.getFirstEvent().getTid() + ":" + graphOp.getFirstEvent().getSerial() + ", " + graphOp.getSecondEvent().getTid() + ":" + graphOp.getSecondEvent().getSerial() + ") with prover id :" + graphOp.getProverId());
                if (solver != null) {
                    state = new RevisitState(new ArrayList<>(), 0, null);
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_W_W(graphOp.getG(), (WriteEvent) graphOp.getFirstEvent(), (WriteEvent) graphOp.getSecondEvent(), graphOp.getToBeAddedEvents(), state);
                break;
            case FR_R_W:
                //System.out.println("[OPT-Trust Strategy] The graphOp is :" + graphOp.getType() + " (" + graphOp.getFirstEvent().getTid() + ":" + graphOp.getFirstEvent().getSerial() + ", " + graphOp.getSecondEvent().getTid() + ":" + graphOp.getSecondEvent().getSerial() + ") with prover id :" + graphOp.getProverId());
                if (solver != null) {
                    state = new RevisitState(new ArrayList<>(), 0, null);
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_R_W(graphOp.getG(), (ReadEvent) graphOp.getFirstEvent(), (WriteEvent) graphOp.getSecondEvent(), state);
                break;
            case FR_RX_W:
                //System.out.println("[OPT-Trust Strategy] The graphOp is :" + graphOp.getType() + " (" + graphOp.getFirstEvent().getTid() + ":" + graphOp.getFirstEvent().getSerial() + ", " + graphOp.getSecondEvent().getTid() + ":" + graphOp.getSecondEvent().getSerial() + ") with prover id :" + graphOp.getProverId());
                if (solver != null) {
                    state = new RevisitState(new ArrayList<>(), 0, null);
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
                dpor.processFR_RX_W(graphOp.getG(), (ReadExEvent) graphOp.getFirstEvent(), (WriteEvent) graphOp.getSecondEvent(), graphOp.getToBeAddedEvents(), state);
                break;
            case FR_NEG_SYM:
                //System.out.println("[OPT-Trust Strategy] The graphOp is :" + graphOp.getType() + " (" + graphOp.getFirstEvent().getTid() + ":" + graphOp.getFirstEvent().getSerial() + ") with prover id :" + graphOp.getProverId());
                if (solver != null) {
                    state = new RevisitState(new ArrayList<>(), 0, null);
                }
                makeDPORFree();
                RuntimeEnvironment.frCounter++;
//                System.out.println("[OPT-Trust Strategy] The FR_NEG_SYM is called");
                //System.out.println("[OPT-Trust Strategy] The size of stack is :" + solver.size());
                dpor.processFR_neg_sym(graphOp.getG(), (SymExecutionEvent) graphOp.getFirstEvent(), state);
                break;
            case RESET_PROVER:
                RuntimeEnvironment.brCounter++;
                //System.out.println("[OPT-Trust Strategy] The RESET_PROVER is called");
                makeDPORFree();
                solver.resetProver();
                solver.resetProver = true;
                break;
        }
//        System.out.println("[OPT-Trust Strategy] The size of stack is :" + solver.size());
//        System.out.println("[OPT-Trust Strategy] The prover id is :" + solver.getProverId());
//        System.out.println("[OPT-Trust Strategy] The resetProver is :" + solver.resetProver);
        if (state != null && solver != null && !solver.resetProver) {
//            System.out.println("[OPT-Trust Strategy] The stack elements are :");
//            Stack s = solver.stack;
//            for (int i = 0; i < s.size(); i++) {
//                System.out.println(s.get(i));
//            }

            if (state != null && !state.getPopitems().isEmpty()) {
                // For each boolean formula, we need to first find the index of that element in the stack
                // Then we need to pop the elements from the stack
                ArrayList<BooleanFormula> popItems = new ArrayList<>();
                for (ThreadEvent popItem : state.getPopitems()) {
                    if (popItem instanceof SymExecutionEvent sym) {
                        if (sym.getResult()) {
                            popItems.add(sym.getSymbolicOp().getFormula());
                        } else {
                            popItems.add(solver.negateFormula(sym.getSymbolicOp().getFormula()));
                        }
                    } else if (popItem instanceof SymAssumeEvent asm) {
                        popItems.add(asm.getSymbolicOp().getFormula());
                    }
                }
                for (BooleanFormula popItem : popItems) {
                    //int index = solver.stack.indexOf(popItem);
                    int index = -1;
                    for (int i = solver.stack.size() - 1; i >= 0; i--) {
                        if (solver.stack.get(i).equals(popItem)) {
                            index = i;
                            break;
                        }
                    }
                    if (index == solver.stack.size() - 1) {
                        solver.pop();
                    } else if (index >= 0) {
                        // Now we need to pop the elements from the stack to the index. Then we need to pop the element at the index
                        // and then we need to push the elements back to the stack
                        Stack<BooleanFormula> tempStack = new Stack<>();
                        while (solver.stack.size() - 1 > index) {
                            tempStack.push(solver.pop());
                        }
                        solver.pop();
                        while (!tempStack.isEmpty()) {
                            solver.push(tempStack.pop());
                        }
                    } else {
                        System.out.println("[OPT-Trust Strategy] The element is not found in the stack");
                        System.out.println("[OPT-Trust Strategy] The element is :" + popItem);
                        System.out.println("[OPT-Trust Strategy] The index is :" + index);
                        System.exit(0);
                    }
                }
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
//                System.out.println("[OPT-Trust Strategy] The guided phase finisehd");
//                System.out.println("[OPT-Trust Strategy] The size of stack is :" + solver.size());
//                System.out.println("[OPT-Trust Strategy] The prover id is :" + solver.getProverId());
//                System.out.println("[OPT-Trust Strategy] The model is updated");
                solver.solveAndUpdateModelSymbolicVariables();
                solver.resetProver = false;
            }
            //solver.solveAndUpdateModelSymbolicVariables();
            return pickNextReadyThread();
        }

        guidingEvent = guidingEvents.remove(0);
        //System.out.println("[OPT-Trust Strategy] The next Guided Event is :" + guidingEvent);

        if (guidingEvent instanceof StartEvent) {
            guidingThread = ((StartEvent) guidingEvent).getCallerThread();
        } else {
            guidingThread = guidingEvent.getTid();
        }

//        System.out.println("[OPT-Trust Strategy Message] : " +
//                RuntimeEnvironment.threadObjectMap.get((long) guidingThread).getName() + " is the next guided thread");
        return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
    }

    @Override
    public void handleEmptyGuidingEvents() {
        //System.out.println("[OPT-Trust Strategy Message] : The guidingEvents is empty");
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

        /*System.out.println("[OPT-Trust Strategy Debugging] The oldReadsMap is :");
        currentGraph.printReads();*/

        /*int x = 0;*/
        for (Map.Entry<Location, ArrayList<ReadEvent>> entry : oldReadsMap.entrySet()) {
            /*x++;
            System.out.println("[OPT-Trust Strategy Debugging] The " + x + "th entry is visited");*/
            ArrayList<ReadEvent> reads = entry.getValue();
            Location loc = reads.get(0).getLoc();
            /*System.out.println("[OPT-Trust Strategy Debugging] The reads is :" + reads.get(0));
            System.out.println("[OPT-Trust Strategy Debugging] The location is :" + loc);*/
            newReadsMap.put(loc, reads);
        }

        currentGraph.setReads(newReadsMap);
        /*System.out.println("[OPT-Trust Strategy Debugging] The newReadsMap is :");
        currentGraph.printReads();*/
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
            //System.out.println("[OPT-Trust Strategy] The next Guided Event is :" + readEx);
            WriteEvent wr = currentGraph.getRf().get(readEx);
            currentGraph.removeRf(readEx);
            readEx.setLoc(readExEvent.getLoc());
            currentGraph.addRF(readEx, wr);
            RuntimeEnvironment.eventsRecord.add(readEx);

            for (int i = 0; i < guidingEvents.size(); i++) {
                if (guidingEvents.get(i) instanceof WriteExEvent && guidingEvents.get(i).getTid() == readEx.getTid() && guidingEvents.get(i).getSerial() == readEx.getSerial() + 1) {
                    guidingEvent = guidingEvents.remove(i);
                    //System.out.println("[Debug] the corresponding writeExEvent is found: " + guidingEvent);
                    break;
                }
            }
            //guidingEvent = guidingEvents.remove(0);
            WriteExEvent writeEx = (WriteExEvent) guidingEvent;
            //System.out.println("[OPT-Trust Strategy] The next Guided Event is :" + writeEx);
            writeExEvent.setConditionValue(writeEx.getConditionValue());
            writeEx.setLoc(writeExEvent.getLoc());
            writeExEvent.setOperationSuccess(writeEx.getOperationSuccess());
            //System.out.println("[OPT-Trust Strategy Debugging] The success of write is :" + writeEx.getOperationSuccess());
            RuntimeEnvironment.eventsRecord.add(writeEx);
            return pickNextThread();
        } else {
            ArrayList<ThreadEvent> events = new ArrayList<>();
            events.add(readExEvent);
            RuntimeEnvironment.eventsRecord.add(readExEvent);
            events.add(writeExEvent);
            RuntimeEnvironment.eventsRecord.add(writeExEvent);
            passEventToDPOR(events);
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
        } else {
            RuntimeEnvironment.eventsRecord.add(readEvent);
            passEventToDPOR(readEvent);
        }
    }

    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        if (guidingActivate) {
            WriteEvent write = (WriteEvent) guidingEvent;
            write.setLoc(writeEvent.getLoc());
            RuntimeEnvironment.eventsRecord.add(write);
        } else {
            RuntimeEnvironment.eventsRecord.add(writeEvent);
            passEventToDPOR(writeEvent);
        }
    }
}
