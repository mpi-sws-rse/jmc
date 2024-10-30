package org.mpisws.checker.strategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCThread;
import programStructure.*;


/**
 * The ReplayStrategy class implements the {@link SearchStrategy} interface and is responsible for managing the execution
 * order of events in a multithreaded program using a replay strategy. It maintains a guiding trace and a guiding event
 * to guide the execution. The class provides functionality to handle various types of events including start, enter
 * monitor, exit monitor, join, read, write, finish, and symbolic arithmetic events. The class uses the
 * {@link RuntimeEnvironment} API to create and record events. The ReplayStrategy class is designed to control the flow
 * of a program's execution and ensure a replay execution order of operations.
 */
public class ReplayStrategy implements SearchStrategy {

    /**
     * @property {@link #guidingTrace} is the trace that is used to guide the execution.
     */
    private List<Event> guidingTrace;

    /**
     * @property {@link #guidingEvent} is the event that is used to guide the execution.
     */
    private Event guidingEvent;

    /**
     * @property {@link #guidingThread} is the thread that is used to guide the execution.
     */
    private int guidingThread;

    /**
     * @property {@link #buggyTracePath} is the path of the buggy trace file.
     */
    private final String buggyTracePath;

    /**
     * @property {@link #buggyTraceFile} is the name of the buggy trace file.
     */
    private final String buggyTraceFile;

    /**
     * The constructor of the ReplayStrategy class.
     *
     * <p>It initializes the {@link #buggyTracePath} and {@link #buggyTraceFile} properties and loads the guiding trace.
     * </p>
     */
    public ReplayStrategy() {
        buggyTracePath = RuntimeEnvironment.buggyTracePath;
        buggyTraceFile = RuntimeEnvironment.buggyTraceFile;
        loadGuidingTrace();
    }

    /**
     * Represents the required strategy for the next start event.
     *
     * @param calleeThread is the thread that is going to be started.
     * @param callerThread is the thread that is going to call the start method of the calleeThread.
     */
    @Override
    public void nextStartEvent(Thread calleeThread, Thread callerThread) {
        StartEvent startEvent = RuntimeEnvironment.createStartEvent(calleeThread, callerThread);
        RuntimeEnvironment.eventsRecord.add(startEvent);
    }

    @Override
    public void nextMainStartEvent(MainStartEvent mainStartEvent) {
        RuntimeEnvironment.eventsRecord.add(mainStartEvent);
    }

    /**
     * @param conAssumeEvent
     */
    @Override
    public void nextConAssumeRequest(ConAssumeEvent conAssumeEvent) {
        RuntimeEnvironment.eventsRecord.add(conAssumeEvent);
    }

    /**
     * @param assumeBlockedEvent
     */
    @Override
    public void nextAssumeBlockedRequest(AssumeBlockedEvent assumeBlockedEvent) {
        RuntimeEnvironment.eventsRecord.add(assumeBlockedEvent);
    }

    /**
     * Represents the required strategy for the next enter monitor event.
     *
     * @param thread  is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        EnterMonitorEvent enterMonitorEvent = RuntimeEnvironment.createEnterMonitorEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(enterMonitorEvent);
    }

    /**
     * Represents the required strategy for the next exit monitor event.
     *
     * @param thread  is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    @Override
    public void nextExitMonitorEvent(Thread thread, Object monitor) {
        ExitMonitorEvent exitMonitorEvent = RuntimeEnvironment.createExitMonitorEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(exitMonitorEvent);
        analyzeSuspendedThreadsForMonitor(monitor);
    }

    /**
     * Represents the required strategy for the next join event.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    @Override
    public void nextJoinEvent(Thread joinReq, Thread joinRes) {
        JoinEvent joinEvent = RuntimeEnvironment.createJoinEvent(joinReq, joinRes);
        RuntimeEnvironment.eventsRecord.add(joinEvent);
    }

    /**
     * @param thread
     * @param readExEvent
     * @param writeExEvent
     */
    @Override
    public Thread nextCasRequest(Thread thread, ReadExEvent readExEvent, WriteExEvent writeExEvent) {
        if (readExEvent.getInternalValue() == writeExEvent.getConditionValue()) {
            writeExEvent.setOperationSuccess(true);
        }
        RuntimeEnvironment.eventsRecord.add(readExEvent);
        RuntimeEnvironment.eventsRecord.add(writeExEvent);
        guidingTrace.remove(0);
        return pickNextThread();
    }

    /**
     * Represents the required strategy for the next join request.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    @Override
    public Thread nextJoinRequest(Thread joinReq, Thread joinRes) {
        nextJoinEvent(joinReq, joinRes);
        return joinReq;
    }

    /**
     * Represents the required strategy for the next park event.
     * <p>
     * This method creates a park event and adds it to the execution trace. It also checks if the thread has a parking
     * permit or not. If the thread has a parking permit, it sets the permit to false. Otherwise, it parks the thread.
     * </p>
     *
     * @param thread is the thread that is going to be parked.
     */
    @Override
    public void nextParkRequest(Thread thread) {
        ParkEvent parkRequestEvent = RuntimeEnvironment.createParkEvent(thread);
        RuntimeEnvironment.eventsRecord.add(parkRequestEvent);
        long tid = RuntimeEnvironment.threadIdMap.get(thread.getId());
        if (RuntimeEnvironment.threadParkingPermit.get(tid)) {
            RuntimeEnvironment.threadParkingPermit.put(tid, false);
        } else {
            parkThread(thread);
        }
    }

    /**
     * Represents the required strategy for the next unpark event.
     * <p>
     * This method creates an unpark event and adds it to the execution trace. It also checks if the unparkee thread is
     * parked or not. If the unparkee thread is parked, it unparks the thread. Otherwise, it sets the parking permit of
     * the unparkee thread to true.
     * </p>
     *
     * @param unparkerThread is the thread that is going to unpark another thread.
     * @param unparkeeThread is the thread that is going to be unparked by another thread.
     */
    @Override
    public void nextUnparkRequest(Thread unparkerThread, Thread unparkeeThread) {
        UnparkingEvent unparkingRequestEvent = RuntimeEnvironment.createUnparkingEvent(unparkerThread, unparkeeThread);
        RuntimeEnvironment.eventsRecord.add(unparkingRequestEvent);
        if (RuntimeEnvironment.parkedThreadList.contains(unparkeeThread)) {
            unparkThread(unparkeeThread);
        } else {
            RuntimeEnvironment.threadParkingPermit.put(RuntimeEnvironment.threadIdMap.get(unparkeeThread.getId()), true);
        }
    }

    /**
     * Represents the required strategy for the next read event.
     *
     * @param readEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        RuntimeEnvironment.eventsRecord.add(readEvent);
    }

    /**
     * @param receiveEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReceiveEvent(ReceiveEvent receiveEvent) {
        RuntimeEnvironment.eventsRecord.add(receiveEvent);
        ReceiveEvent guidingRecvEvent = (ReceiveEvent) guidingEvent;
        if (guidingRecvEvent.getRf() == null) {
            handleGuidedNullRecvEvent(receiveEvent, guidingRecvEvent);
        } else {
            handleGuidedRecvEvent(receiveEvent, guidingRecvEvent);
        }
    }

    private void handleGuidedNullRecvEvent(ReceiveEvent receiveEvent, ReceiveEvent guidingRecvEvent) {
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        jmcThread.noMessageExists();
        receiveEvent.setRf(guidingRecvEvent.getRf());
        receiveEvent.setValue(null);
    }

    private void handleGuidedRecvEvent(ReceiveEvent receiveEvent, ReceiveEvent guidingRecvEvent) {
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        SendEvent guidedReceiveFrom = (SendEvent) guidingRecvEvent.getRf();

        // Assign receiveFrom with the send event in the RuntimeEnvironment.eventsRecord that has the same tid and serial number as the guidedReceiveFrom
        SendEvent receiveFrom = null;
        for (Event event : RuntimeEnvironment.eventsRecord) {
            if (event instanceof SendEvent sendEvent) {
                if (sendEvent.getTid() == guidedReceiveFrom.getTid() && sendEvent.getSerial() == guidedReceiveFrom.getSerial()) {
                    receiveFrom = sendEvent;
                    break;
                }
            }
        }

        if (receiveFrom == null) {
            System.out.println("[Replay Strategy Message] : The receive event does not have a corresponding send event");
            System.exit(0);
        }

        Message message = receiveFrom.getValue();
        jmcThread.findNextMessageIndex(message);
        receiveEvent.setRf(receiveFrom);
        receiveEvent.setValue(message);
    }

    /**
     * Represents the required strategy for the next write event.
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        RuntimeEnvironment.eventsRecord.add(writeEvent);
    }

    /**
     * @param sendEvent is the write event that is going to be executed.
     */
    @Override
    public void nextSendEvent(SendEvent sendEvent) {
        RuntimeEnvironment.eventsRecord.add(sendEvent);
        executeSendEvent(sendEvent);
    }

    @Override
    public boolean nextBlockingReceiveRequest(ReceiveEvent receiveEvent) {
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        BlockingRecvReq blockingRecvReq = RuntimeEnvironment.createBlockingRecvReq(jmcThread, receiveEvent);
        BlockingRecvReq guidingBlockingRecvReq = (BlockingRecvReq) guidingEvent;
        blockingRecvReq.setBlocked(guidingBlockingRecvReq.isBlocked());
        RuntimeEnvironment.eventsRecord.add(blockingRecvReq);
        return false;
    }

    /**
     * Represents the required strategy for the next finish event.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextFinishEvent(Thread thread) {
        FinishEvent finishEvent = RuntimeEnvironment.createFinishEvent(thread);
        RuntimeEnvironment.eventsRecord.add(finishEvent);
    }

    /**
     * Represents the required strategy for the next failure event.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextFailureEvent(Thread thread) {
        FailureEvent failureEvent = RuntimeEnvironment.createFailureEvent(thread);
        RuntimeEnvironment.eventsRecord.add(failureEvent);
    }

    /**
     * Represents the required strategy for the next deadlock event.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextDeadlockEvent(Thread thread) {
        DeadlockEvent deadlockEvent = RuntimeEnvironment.createDeadlockEvent(thread);
        RuntimeEnvironment.eventsRecord.add(deadlockEvent);
    }

    /**
     * Represents the required strategy for the next finish request.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public Thread nextFinishRequest(Thread thread) {
        nextFinishEvent(thread);
        return pickNextThread();
    }

    /**
     * Represents the required strategy for the next symbolic operation request.
     * <p>
     * This method creates a symbolic execution event and adds it to the execution trace.
     * </p>
     *
     * @param symbolicOperation
     */
    @Override
    public void nextSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        if (guidingEvent.getType() != EventType.SYM_EXECUTION) {
            System.out.println("[Replay Strategy Message] : The next event is not a symbolic operation");
            System.exit(0);
        }
        SymExecutionEvent symEvent = (SymExecutionEvent) this.guidingEvent;
        RuntimeEnvironment.solverResult = symEvent.getResult();
        SymExecutionEvent symbolicOperationEvent = RuntimeEnvironment.createSymExecutionEvent(thread,
                symbolicOperation.getFormula().toString(), symEvent.isNegatable());
        RuntimeEnvironment.eventsRecord.add(symbolicOperationEvent);
    }

    /**
     * @param thread
     * @param symbolicOperation
     */
    @Override
    public void nextSymAssumeRequest(Thread thread, SymbolicOperation symbolicOperation) {
        if (guidingEvent.getType() != EventType.SYM_ASSUME) {
            System.out.println("[Replay Strategy Message] : The next event is not a symbolic operation");
            System.exit(0);
        }
        SymAssumeEvent symAssumeEvent = (SymAssumeEvent) this.guidingEvent;
        RuntimeEnvironment.solverResult = symAssumeEvent.getResult();
        SymAssumeEvent symAssumeRequest = RuntimeEnvironment.createSymAssumeEvent(thread,
                symbolicOperation);
        RuntimeEnvironment.eventsRecord.add(symAssumeRequest);
    }

    /**
     * Indicates whether the execution is done or not.
     *
     * @return true if the execution is done, otherwise false.
     */
    @Override
    public boolean done() {
        return guidingTrace.isEmpty();
    }

    /**
     * Picks the next thread to run.
     * <p>
     * This method picks the next thread to run based on the guiding trace and the guiding event. It removes the guiding
     * event from the guiding trace and returns the thread that is going to run. If the guiding trace is empty, it prints
     * an error message and exits the program. If the guiding event is an unparking event, it unparks the thread and
     * returns the thread. If the guiding event is a start event, it sets the guiding thread to the caller thread of the
     * start event. Otherwise, it sets the guiding thread to the thread of the guiding event. If the guiding event is an
     * enter monitor event, it removes the monitor request from the monitor request list and calls the
     * {@link #nextEnterMonitorEvent(Thread, Object)} method to add the enter monitor event to the execution trace.
     * If the guiding event is an unpark event, it calls the {@link #guidedUnparkEventHelper(UnparkEvent)} method to
     * handle the unpark event. Otherwise, it prints a message and returns the thread of the guiding event.
     * </p>
     *
     * @return the next thread to run.
     */
    @Override
    public Thread pickNextThread() {
        if (guidingTrace.isEmpty()) {
            System.out.println("[Replay Strategy Message] : Guiding trace is empty");
            System.out.println("[Replay Strategy Message] : However, the execution is not done yet");
            System.out.println("[Replay Strategy Message] : Thus, there is something wrong with the Replay Strategy");
            printExecutionTrace();
            System.exit(0);
        }

        if (guidingEvent != null && guidingEvent.getType() == EventType.UNPARKING) {
            System.out.println("[Replay Strategy Message] : Thread-" +
                    RuntimeEnvironment.threadObjectMap.get((long) ((UnparkingEvent) guidingEvent).getTid()).getId() +
                    " is the next guided thread for UNPARKING");
            Thread nextThread = RuntimeEnvironment.threadObjectMap.get((long) ((UnparkingEvent) guidingEvent).getTid());
            guidingEvent = null;
            return nextThread;
        }
        guidingEvent = guidingTrace.remove(0);

        if (guidingEvent instanceof StartEvent) {
            guidingThread = ((StartEvent) guidingEvent).getCallerThread();
        } else {
            guidingThread = ((ThreadEvent) guidingEvent).getTid();
        }

        if (guidingEvent.getType() == EventType.ENTER_MONITOR) {
            removeMonitorRequest(guidingThread);
        }
        if (guidingEvent.getType() == EventType.UNPARK) {
            guidedUnparkEventHelper((UnparkEvent) guidingEvent);
            return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
        }
        if (guidingEvent.getType() == EventType.BLOCKED_RECV) {
            BlockedRecvEvent guidedBlockedRecvEvent = (BlockedRecvEvent) guidingEvent;
            handleNextGuidedBlockedRecvEvent(guidedBlockedRecvEvent);
            return pickNextThread();
        }
        if (guidingEvent.getType() == EventType.UNBLOCKED_RECV) {
            UnblockedRecvEvent guidedUnblockedRecvEvent = (UnblockedRecvEvent) guidingEvent;
            handleNextGuidedUnblockedRecvEvent(guidedUnblockedRecvEvent);
            return pickNextThread();
        }
        if (guidingEvent.getType() == EventType.DEADLOCK) {
            nextDeadlockEvent(RuntimeEnvironment.threadObjectMap.get((long) guidingThread));
            printExecutionTrace();
            saveBuggyExecutionTrace();
            System.out.println("******************************************************************************************");
            System.out.println("[*** Resource Usage ***]");
            RuntimeEnvironment.printFinalMessage();
            System.out.println("******************************************************************************************");
            System.exit(0);
        }

        System.out.println("[Replay Strategy Message] : Guiding event is " + guidingEvent);
        System.out.println("[Replay Strategy Message] : Thread-" + guidingThread + " is selected to run");
        return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
    }

    private void handleNextGuidedBlockedRecvEvent(BlockedRecvEvent guidedBlockedRecvEvent) {
        System.out.println("[Replay Strategy Message] : The recv event is: " + guidedBlockedRecvEvent.getReceiveEvent());
        ReceiveEvent guidedReceiveEvent = guidedBlockedRecvEvent.getReceiveEvent();
        ReceiveEvent receiveEventInRecord = findReceiveEventFromBlockingRecvReq(guidedReceiveEvent);
        System.out.println("[Replay Strategy Message] : The recv event in the record is: " + receiveEventInRecord);
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEventInRecord.getTid());
        RuntimeEnvironment.removeBlockedThreadFromReadyQueue(jmcThread, receiveEventInRecord);
    }

    private void handleNextGuidedUnblockedRecvEvent(UnblockedRecvEvent guidedUnblockedRecvEvent) {
        ReceiveEvent guidedReceiveEvent = guidedUnblockedRecvEvent.getReceiveEvent();
        ReceiveEvent receiveEventInRecord = findReceiveEventFromBlockingRecvReq(guidedReceiveEvent);
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEventInRecord.getTid());
        RuntimeEnvironment.addUnblockedThreadToReadyQueue(jmcThread, receiveEventInRecord);
    }

    private ReceiveEvent findReceiveEventInRecord(ReceiveEvent guidedReceiveEvent) {
        ReceiveEvent receiveEventInRecord = null;
        for (Event event : RuntimeEnvironment.eventsRecord) {
            if (event instanceof ReceiveEvent recvEvent) {
                if (recvEvent.getTid() == guidedReceiveEvent.getTid() && recvEvent.getSerial() == guidedReceiveEvent.getSerial()) {
                    receiveEventInRecord = recvEvent;
                    break;
                }
            }
        }
        return receiveEventInRecord;
    }

    private ReceiveEvent findReceiveEventFromBlockingRecvReq(ReceiveEvent guidedReceiveEvent) {
        ReceiveEvent receiveEvent = null;
        for (Event event : RuntimeEnvironment.eventsRecord) {
            if (event instanceof BlockingRecvReq blockingRecvReq) {
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
     * Handles the next guided unpark event.
     *
     * @param unparkEvent is the unpark event that is going to be executed.
     */
    private void guidedUnparkEventHelper(UnparkEvent unparkEvent) {
        System.out.println("[Replay Strategy Message] : The next guided event is UNPARK event.");
        Thread thread = RuntimeEnvironment.threadObjectMap.get((long) unparkEvent.getTid());
        unparkThread(thread);
    }

    /**
     * Removes the monitor request from the monitor request list.
     * <p>
     * This method removes the monitor request from the monitor request list and calls the
     * {@link #nextEnterMonitorEvent(Thread, Object)} method to add the enter monitor event to the execution trace.
     * </p>
     *
     * @param threadId is the id of the thread that its monitor request is going to be removed.
     */
    private void removeMonitorRequest(int threadId) {
        Thread thread = RuntimeEnvironment.threadObjectMap.get((long) threadId);
        Object monitor = RuntimeEnvironment.monitorRequest.get(thread);
        RuntimeEnvironment.monitorRequest.remove(thread, monitor);
        nextEnterMonitorEvent(thread, monitor);
    }

    /**
     * Saves the execution state.
     */
    @Override
    public void saveExecutionState() {
        printExecutionTrace();
    }

    /**
     * Loads the buggy execution trace.
     * <p>
     * This method reads the buggy execution trace from the file and stores it in the {@link #guidingTrace} list.
     * </p>
     */
    private void loadGuidingTrace() {
        File file = new File(buggyTracePath + buggyTraceFile);
        if (file.exists()) {
            try (FileInputStream fileIn = new FileInputStream(file);
                 ObjectInputStream in = new ObjectInputStream(fileIn)) {
                guidingTrace = (List<Event>) in.readObject();
                System.out.println("[Replay Strategy Message] : Guiding trace is loaded from " + buggyTracePath +
                        buggyTraceFile);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("[Replay Strategy Message] : File " + buggyTracePath + buggyTraceFile + " does not exist");
            System.exit(0);
        }
    }

    /**
     * No need to save the buggy execution trace in the replay strategy.
     */
    @Override
    public void saveBuggyExecutionTrace() {
        // DO NOTHING
    }
}