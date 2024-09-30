package org.mpisws.checker.strategy;

import dpor.Must;
import executionGraph.ExecutionGraph;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.util.concurrent.JMCThread;
import programStructure.*;

import java.util.*;

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
        dpor = new Must(executionGraphsPath, RuntimeEnvironment.verbose);
        dpor.setGraphCounter(RuntimeEnvironment.numOfGraphs);
    }

    /**
     * @param thread
     * @param readExEvent
     * @param writeExEvent
     */
    @Override
    public Thread nextCasRequest(Thread thread, ReadExEvent readExEvent, WriteExEvent writeExEvent) {
        // Must does not need to handle compare and set requests.
        return pickNextThread();
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
        executeReceiveEvent(receiveEvent);
    }

    private void executeReceiveEvent(ReceiveEvent receiveEvent) {
        ReceiveEvent tempReceive = (ReceiveEvent) currentGraph.getGraphEvents().get(currentGraph.getGraphEvents().size() - 1);
        System.out.println("[Debugging Message] : Receive Event - " + tempReceive);
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(tempReceive.getTid());
        if (tempReceive.getRf() instanceof InitializationEvent) {
            jmcThread.noMessageExists();
            receiveEvent.setValue(null);
        } else {
            Message message = ((SendEvent) tempReceive.getRf()).getValue();
            jmcThread.findNextMessageIndex(message);
            receiveEvent.setValue(message);
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
        RuntimeEnvironment.threadBlockingRecvList.put(Long.valueOf(receiveEvent.getTid()), blockingRecvReq);
        if (guidingActivate) {
            addEventToCurrentGraph(blockingRecvReq);
            return handleGuidedBlockingRecvReq(blockingRecvReq);
        } else {
            passEventToDPOR(blockingRecvReq);
            updateCurrentGraph(blockingRecvReq);
            return handleBlockingRecvReq(blockingRecvReq, jmcThread);
        }
    }

    private boolean handleGuidedBlockingRecvReq(BlockingRecvReq blockingRecvReq) {
        BlockingRecvReq guidingBlockingRecvReq = (BlockingRecvReq) guidingEvent;
        blockingRecvReq.setBlocked(guidingBlockingRecvReq.isBlocked());
        //return guidingBlockingRecvReq.isBlocked();
        return false;
    }

    private boolean handleBlockingRecvReq(BlockingRecvReq blockingRecvReq, JMCThread jmcThread) {
        BlockingRecvReq tempEvent = (BlockingRecvReq) currentGraph.getGraphEvents().get(currentGraph.getGraphEvents().size() - 1);
        blockingRecvReq.setBlocked(tempEvent.isBlocked());
        if (tempEvent.isBlocked()) {
            BlockedRecvEvent blockedRecvEvent = RuntimeEnvironment.removeBlockedThreadFromReadyQueue(jmcThread, blockingRecvReq.getReceiveEvent());
            nextBlockedReceiveEvent(blockedRecvEvent);
        }
        return !tempEvent.isBlocked();
    }

    private void nextBlockedReceiveEvent(BlockedRecvEvent blockedRecvEvent) {
        passEventToDPOR(blockedRecvEvent);
        updateCurrentGraph(blockedRecvEvent);
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
     * @return
     */
    @Override
    public Thread pickNextGuidedThread() {
        if (guidingEvents.isEmpty()) {
            handleEmptyGuidingEvents();
            solver.solveAndUpdateModelSymbolicVariables();
            return pickNextReadyThread();
        }
        guidingEvent = guidingEvents.remove(0);

        System.out.println("[Debugging Message in Must] : Guiding Event - " + guidingEvent);

        if (guidingEvent instanceof StartEvent) {
            guidingThread = findGuidingThreadFromStartEvent();
        } else {
            guidingThread = ((ThreadEvent) guidingEvent).getTid();
        }

        if (guidingEvent.getType() == EventType.BLOCKED_RECV) {
            BlockedRecvEvent guidedBlockedRecvEvent = (BlockedRecvEvent) guidingEvent;
            handleNextGuidedBlockedRecvEvent(guidedBlockedRecvEvent);
            return pickNextGuidedThread();
        }
        if (guidingEvent.getType() == EventType.UNBLOCKED_RECV) {
            UnblockedRecvEvent guidedUnblockedRecvEvent = (UnblockedRecvEvent) guidingEvent;
            handleNextGuidedUnblockedRecvEvent(guidedUnblockedRecvEvent);
            return pickNextGuidedThread();
        }

        System.out.println("[Must Strategy Message] : Thread-" + guidingThread + " is the next guided thread");
        return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
    }

    private void handleNextGuidedBlockedRecvEvent(BlockedRecvEvent guidedBlockedRecvEvent) {
        //ReceiveEvent guidedReceiveEvent = guidedBlockedRecvEvent.getReceiveEvent();
        //ReceiveEvent receiveEventInRecord = findReceiveEventFromBlockingRecvReq(guidedReceiveEvent);
        Long jmcTid = Long.valueOf(guidedBlockedRecvEvent.getTid());
        ReceiveEvent receiveEventInRecord = RuntimeEnvironment.threadBlockingRecvList.get(jmcTid).getReceiveEvent();
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(jmcTid);
        BlockedRecvEvent blockedRecvEvent = RuntimeEnvironment.removeBlockedThreadFromReadyQueue(jmcThread, receiveEventInRecord);
        addEventToCurrentGraph(blockedRecvEvent);
    }

    private void handleNextGuidedUnblockedRecvEvent(UnblockedRecvEvent guidedUnblockedRecvEvent) {
        //ReceiveEvent guidedReceiveEvent = guidedUnblockedRecvEvent.getReceiveEvent();
        //ReceiveEvent receiveEventInRecord = findReceiveEventFromBlockingRecvReq(guidedReceiveEvent);
        Long jmcTid = Long.valueOf(guidedUnblockedRecvEvent.getTid());
        ReceiveEvent receiveEventInRecord = RuntimeEnvironment.threadBlockingRecvList.get(jmcTid).getReceiveEvent();
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(jmcTid);
        UnblockedRecvEvent unblockedRecvEvent = RuntimeEnvironment.addUnblockedThreadToReadyQueue(jmcThread, receiveEventInRecord);
        addEventToCurrentGraph(unblockedRecvEvent);
    }

    private ReceiveEvent findReceiveEventFromBlockingRecvReq(ReceiveEvent guidedReceiveEvent) {
        ReceiveEvent receiveEvent = null;
        for (Event event : RuntimeEnvironment.eventsRecord) {
            if (event instanceof BlockingRecvReq blockingRecvReq) {
                System.out.println("[Debugging Message in Must] : Blocking Receive Request - " + blockingRecvReq);
                ReceiveEvent recvEvent = blockingRecvReq.getReceiveEvent();
                if (recvEvent.getTid() == guidedReceiveEvent.getTid() && recvEvent.getSerial() == guidedReceiveEvent.getSerial()) {
                    receiveEvent = recvEvent;
                    break;
                }
            }
        }
        return receiveEvent;
    }

    /**
     * Handles the empty guiding events.
     * <p>
     * This method handles the empty guiding events. It prints a message that the guiding events is empty and finds the
     * new RecvFrom, STs, JTs, and TCs based on the current graph. Then, it sets the {@link #guidingActivate} to false.
     * </p>
     */
    @Override
    public void handleEmptyGuidingEvents() {
        System.out.println("[Must Strategy Message] : The guidingEvents is empty");
        currentGraph.setRecvFrom(findNewRecvfrom());
        currentGraph.setSTs(findNewSTs());
        currentGraph.setJTs(findNewJTs());
        currentGraph.setTCs(findNewTCs());
        guidingActivate = false;
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

    @Override
    public boolean computeUnblockedRecvThread() {
        boolean result = false;
        for (Map.Entry<Long, ReceiveEvent> entry : RuntimeEnvironment.blockedRecvThreadMap.entrySet()) {
            if (isMessageAvailable(entry.getValue())) {
                JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(entry.getValue().getTid());
                UnblockedRecvEvent unblockedRecvEvent = RuntimeEnvironment.addUnblockedThreadToReadyQueue(jmcThread, entry.getValue());
                passEventToDPOR(unblockedRecvEvent);
                updateCurrentGraph(unblockedRecvEvent);
                result = true;
                System.out.println(
                        "[Must Strategy Message] : The thread " + jmcThread.getName() + " is unblocked" + ", since " +
                                "the message is available"
                );
            }
        }
        return result;
    }

    /**
     * Computes the ordered list of the guiding events based on the guiding execution graph.
     */
    @Override
    public void findGuidingEvents() {
        List<Event> freeEvents = new ArrayList<>();
        freeEvents.add(guidingNode.getValue());

        List<Event> remainingEvents = new ArrayList<>(guidingExecutionGraph.getGraphEvents());
        remainingEvents.remove(guidingNode.getValue()); // remove the initialization event
        guidingExecutionGraph.computeSc();
        while (!remainingEvents.isEmpty()) {
            remainingEvents.removeIf(event -> {
                boolean isEventFree = guidingExecutionGraph.getPorf().stream()
                        .noneMatch(pair -> pair.getSecond().equals(event) && !freeEvents.contains(pair.getFirst()));
                if (isEventFree) {
                    freeEvents.add(event);
                }
                return isEventFree;
            });
        }

        guidingEvents.addAll(freeEvents);
        guidingEvents.remove(0);
    }
}