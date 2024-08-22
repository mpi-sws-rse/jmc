package org.mpisws.checker.strategy;

import dpor.Must;
import executionGraph.ExecutionGraph;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCThread;
import programStructure.*;

import java.util.Objects;
import java.util.Optional;

public class MustStrategy extends DPORStrategy {

    public MustStrategy() {
        super();
        initMust();
    }

    /**
     * Initializes the must object.
     * <p>
     * This method initializes the {@link #dpor} object. It sets the {@link Must#getGraphCounter()} with the number of
     * graphs that are available in the {@link RuntimeEnvironment#mcGraphs}.
     * </p>
     */
    private void initMust() {
        dpor = new Must(executionGraphsPath);
        dpor.setGraphCounter(RuntimeEnvironment.numOfGraphs);
    }

    /**
     * Represents the required strategy for the next enter monitor event.
     *
     * @param thread  is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        // Must does not need to handle enter monitor events.
    }

    /**
     * Represents the required strategy for the next exit monitor event.
     *
     * @param thread  is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    @Override
    public void nextExitMonitorEvent(Thread thread, Object monitor) {
        // Must does not need to handle exit monitor events.
    }

    /**
     * Represents the required strategy for the next read event.
     *
     * @param readEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        // Must does not need to handle read events.
    }

    /**
     * Represents the required strategy for the next read event.
     *
     * @param receiveEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReceiveEvent(ReceiveEvent receiveEvent) {
        RuntimeEnvironment.eventsRecord.add(receiveEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(receiveEvent);
            addRfEdgeToCurrentGraph(receiveEvent);
        } else {
            passEventToDPOR(receiveEvent);
            updateCurrentGraph(receiveEvent);
        }
        executeReceiveEvent();
    }

    private void executeReceiveEvent() {
        ReceiveEvent tempReceive = (ReceiveEvent) currentGraph.getGraphEvents().get(currentGraph.getGraphEvents().size() - 1);
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(tempReceive.getTid());
        if (tempReceive.getRf() instanceof InitializationEvent) {
            jmcThread.noMessageExists();
        } else {
            Message message = ((SendEvent) tempReceive.getRf()).getValue();
            jmcThread.findNextMessageIndex(message);
        }
    }

    /**
     * Adds the receive-from edge to the current graph.
     * <p>
     * This method adds the receive-from edge to the {@link #currentGraph}. It sets the receive-from edge of the receive
     * event to the {@link #currentGraph}.
     * </p>
     *
     * @param receiveEvent is the receive event that is going to be executed.
     */
    private void addRfEdgeToCurrentGraph(ReceiveEvent receiveEvent) {
        Optional<ReceivesFrom> receiveFrom = Optional.ofNullable(findRfEdge((ReceiveEvent) guidingEvent));
        receiveFrom.ifPresent(receiveEvent::setRf);
    }

    /**
     * Finds the reads-from edge of the read event.
     * <p>
     * This method finds the reads-from edge of the read event. It returns the reads-from corresponding event to the
     * read event.
     * </p>
     *
     * @param receiveEvent is the read event that is going to be executed.
     * @return the corresponding receive-from event to the read event.
     */
    private ReceivesFrom findRfEdge(ReceiveEvent receiveEvent) {
        ReceivesFrom receivesFrom;

        if (receiveEvent.getRf() instanceof InitializationEvent) {
            receivesFrom = (ReceivesFrom) currentGraph.getGraphEvents().get(0);
        } else {
            SendEvent tempSend = (SendEvent) receiveEvent.getRf();

            receivesFrom = currentGraph.getGraphEvents().stream()
                    .filter(event -> event instanceof SendEvent)
                    .map(event -> (SendEvent) event)
                    .filter(sendEvent -> sendEvent.getTid() == Objects.requireNonNull(tempSend).getTid() &&
                            sendEvent.getSerial() == tempSend.getSerial())
                    .findFirst()
                    .orElse(null);
        }
        return receivesFrom;
    }

    /**
     * Represents the required strategy for the next write event.
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        // Must does not need to handle write events.
    }

    /**
     * Represents the required strategy for the next write event.
     *
     * @param sendEvent is the write event that is going to be executed.
     */
    @Override
    public void nextSendEvent(SendEvent sendEvent) {
        RuntimeEnvironment.eventsRecord.add(sendEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(sendEvent);
        } else {
            passEventToDPOR(sendEvent);
            updateCurrentGraph(sendEvent);
        }
        executeSendEvent(sendEvent);
    }

    /**
     * @param receiveEvent
     * @return
     */
    @Override
    public boolean nextBlockingReceiveRequest(ReceiveEvent receiveEvent) {
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        BlockingRecvReq blockingRecvReq = RuntimeEnvironment.createBlockingRecvReq(jmcThread, receiveEvent);
        RuntimeEnvironment.eventsRecord.add(blockingRecvReq);
        if (guidingActivate) {
            addEventToCurrentGraph(blockingRecvReq);
        } else {
            passEventToDPOR(blockingRecvReq);
            updateCurrentGraph(blockingRecvReq);
        }
        return handleBlockingRecvReq(jmcThread, blockingRecvReq);
    }

    private boolean handleBlockingRecvReq(JMCThread jmcThread, BlockingRecvReq blockingRecvReq) {
        BlockingRecvReq tempEvent = (BlockingRecvReq) currentGraph.getGraphEvents().get(currentGraph.getGraphEvents().size() - 1);
        blockingRecvReq.setBlocked(tempEvent.isBlocked());
        return tempEvent.isBlocked();
    }

    /**
     * Represents the required strategy for the next park request.
     *
     * @param thread is the thread that is going to be parked.
     */
    @Override
    public void nextParkRequest(Thread thread) {
        // Must does not need to handle park requests.
    }

    /**
     * Represents the required strategy for the next unpark request.
     *
     * @param unparkerThread is the thread that is going to unpark unparkeeThread.
     * @param unparkeeThread is the thread that is going to be unparked by unparkerThread.
     */
    @Override
    public void nextUnparkRequest(Thread unparkerThread, Thread unparkeeThread) {
        // Must does not need to handle unpark requests.
    }

    /**
     * Represents the required strategy for the next symbolic operation request.
     *
     * @param thread            is the thread that is going to execute the symbolic operation.
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     */
    @Override
    public void nextSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        // Must does not need to handle symbolic operation requests.
    }

    /**
     * @return
     */
    @Override
    public Thread pickNextGuidedThread() {
        return null;
    }

    /**
     * @param graph
     * @param threadEvent
     * @return
     */
    @Override
    boolean isValidGraph(ExecutionGraph graph, ThreadEvent threadEvent) {
        return graph.getPorf().stream()
                .noneMatch(pair -> pair.component1().getType() == threadEvent.getType() &&
                        ((ThreadEvent) pair.component1()).getTid() == threadEvent.getTid() &&
                        ((ThreadEvent) pair.component1()).getSerial() == threadEvent.getSerial());
    }
}