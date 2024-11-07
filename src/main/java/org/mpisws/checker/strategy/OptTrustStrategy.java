package org.mpisws.checker.strategy;

import dpor.OptTrust;

import executionGraph.operations.GraphOp;

import org.mpisws.runtime.RuntimeEnvironment;

import programStructure.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class OptTrustStrategy extends OptDPORStrategy {

    /** */
    public OptTrustStrategy() {
        super();
        dpor = new OptTrust(executionGraphsPath, RuntimeEnvironment.verbose);
        dpor.setGraphCounter(RuntimeEnvironment.numOfGraphs);
    }

    /**
     * @param graphOp
     */
    @Override
    protected void handleGraphOp(GraphOp graphOp) {
        switch (graphOp.getType()) {
            case FR_L_W:
                makeDPORFree();
                dpor.processFR_L_W(
                        graphOp.getG(),
                        (WriteEvent) graphOp.getFirstEvent(),
                        graphOp.getToBeAddedEvents());
                break;
            case FR_W_W:
                makeDPORFree();
                dpor.processFR_W_W(
                        graphOp.getG(),
                        (WriteEvent) graphOp.getFirstEvent(),
                        (WriteEvent) graphOp.getSecondEvent(),
                        graphOp.getToBeAddedEvents());
                break;
            case FR_R_W:
                makeDPORFree();
                dpor.processFR_R_W(
                        graphOp.getG(),
                        (ReadEvent) graphOp.getFirstEvent(),
                        (WriteEvent) graphOp.getSecondEvent());
                break;
            case FR_RX_W:
                makeDPORFree();
                dpor.processFR_RX_W(
                        graphOp.getG(),
                        (ReadExEvent) graphOp.getFirstEvent(),
                        (WriteEvent) graphOp.getSecondEvent(),
                        graphOp.getToBeAddedEvents());
                break;
            case FR_NEG_SYM:
                makeDPORFree();
                dpor.processFR_neg_sym(graphOp.getG(), (SymExecutionEvent) graphOp.getFirstEvent());
                break;
        }
    }

    /**
     * @return
     */
    @Override
    Thread pickNextGuidedThread() {
        if (guidingEvents.isEmpty()) {
            handleEmptyGuidingEvents();
            solver.solveAndUpdateModelSymbolicVariables();
            return pickNextReadyThread();
        }

        guidingEvent = guidingEvents.remove(0);
        System.out.println("[OPT-Trust Strategy] The next Guided Event is :" + guidingEvent);

        if (guidingEvent instanceof StartEvent) {
            guidingThread = ((StartEvent) guidingEvent).getCallerThread();
        } else {
            guidingThread = guidingEvent.getTid();
        }

        System.out.println(
                "[OPT-Trust Strategy Message] : "
                        + RuntimeEnvironment.threadObjectMap.get((long) guidingThread).getName()
                        + " is the next guided thread");
        return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
    }

    @Override
    public void handleEmptyGuidingEvents() {
        System.out.println("[OPT-Trust Strategy Message] : The guidingEvents is empty");
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
    public Thread nextCasRequest(
            Thread thread, ReadExEvent readExEvent, WriteExEvent writeExEvent) {
        if (guidingActivate) {
            ReadExEvent readEx = (ReadExEvent) guidingEvent;
            readExEvent.setInternalValue(readEx.getInternalValue());
            System.out.println("[OPT-Trust Strategy] The next Guided Event is :" + readEx);
            WriteEvent wr = currentGraph.getRf().get(readEx);
            currentGraph.removeRf(readEx);
            readEx.setLoc(readExEvent.getLoc());
            currentGraph.addRF(readEx, wr);
            RuntimeEnvironment.eventsRecord.add(readEx);

            for (int i = 0; i < guidingEvents.size(); i++) {
                if (guidingEvents.get(i) instanceof WriteExEvent
                        && guidingEvents.get(i).getTid() == readEx.getTid()
                        && guidingEvents.get(i).getSerial() == readEx.getSerial() + 1) {
                    guidingEvent = guidingEvents.remove(i);
                    // System.out.println("[Debug] the corresponding writeExEvent is found: " +
                    // guidingEvent);
                    break;
                }
            }
            // guidingEvent = guidingEvents.remove(0);
            WriteExEvent writeEx = (WriteExEvent) guidingEvent;
            System.out.println("[OPT-Trust Strategy] The next Guided Event is :" + writeEx);
            writeExEvent.setConditionValue(writeEx.getConditionValue());
            writeEx.setLoc(writeExEvent.getLoc());
            writeExEvent.setOperationSuccess(writeEx.getOperationSuccess());
            // System.out.println("[OPT-Trust Strategy Debugging] The success of write is :" +
            // writeEx.getOperationSuccess());
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
