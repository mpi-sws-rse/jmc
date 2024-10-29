package org.mpisws.checker.strategy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.solver.SymbolicSolver;
import org.mpisws.symbolic.SymbolicOperation;

import org.mpisws.util.concurrent.JMCLock;
import org.mpisws.util.concurrent.JMCThread;
import programStructure.*;


/**
 * The RandomStrategy class implements the {@link SearchStrategy} interface and is responsible for managing
 * the execution order of events in a multithreaded program using a random strategy.
 * It maintains a record of random events and a random number generator for the random strategy.
 * The class provides functionality to handle various types of events
 * including start, enter monitor, exit monitor, join, read, write, finish, and symbolic arithmetic events. The class
 * uses the {@link RuntimeEnvironment} API to create and record events. The class initializes the random number
 * generator with the seed value from the {@link RuntimeEnvironment}. It also includes functionality for printing the
 * execution trace and checking if the execution is done. The RandomStrategy class is designed to control the flow of
 * a program's execution and ensure a random execution order of operations.
 */
public class RandomStrategy implements SearchStrategy {

    /**
     * The internal random number generator
     *
     * @property {@link #random} is a random number generator.
     */
    public Random random;

    /**
     * @property {@link #buggyTracePath} is the path to the buggy trace object.
     */
    private final String buggyTracePath;

    /**
     * @property {@link #buggyTraceFile} is the name of the buggy trace file.
     */
    private final String buggyTraceFile;

    /**
     * @property {@link #solver} is keeping the reference to {@link RuntimeEnvironment#solver}
     */
    private final SymbolicSolver solver;

    private final HashMap<Integer, ArrayList<ThreadEvent>> cachedEvents = new HashMap<>();

    /**
     * The following constructor initializes {@link #buggyTraceFile} with the value from
     * {@link RuntimeEnvironment#buggyTraceFile}, {@link #buggyTracePath} with the value from
     * {@link RuntimeEnvironment#buggyTracePath}, and {@link #random} with a new random number generator with the seed
     * value from {@link RuntimeEnvironment#seed}, and {@link #solver} with the value from
     * {@link RuntimeEnvironment#solver}.
     */
    public RandomStrategy() {
        buggyTracePath = RuntimeEnvironment.buggyTracePath;
        if (!Files.exists(Paths.get(buggyTracePath))) {
            System.out.println("[Random Strategy Message] : Directory " + buggyTracePath + " does not exist ");
            System.exit(0);
        }
        buggyTraceFile = RuntimeEnvironment.buggyTraceFile;
        random = new Random(RuntimeEnvironment.seed);
        solver = RuntimeEnvironment.solver;
    }

    /**
     * Creates a {@link StartEvent} for the corresponding starting a thread request of a thread
     * <p>
     * This method creates a {@link StartEvent} for the corresponding starting a thread request of a thread.
     * The created {@link StartEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * </p>
     *
     * @param calleeThread is the thread that is going to be started.
     * @param callerThread is the thread that is going to call the start method of the calleeThread.
     */
    @Override
    public void nextStartEvent(Thread calleeThread, Thread callerThread) {
        StartEvent startEvent = RuntimeEnvironment.createStartEvent(calleeThread, callerThread);
        RuntimeEnvironment.eventsRecord.add(startEvent);
    }

    /**
     * @param conAssumeEvent
     */
    @Override
    public void nextConAssumeRequest(ConAssumeEvent conAssumeEvent) {
        RuntimeEnvironment.eventsRecord.add(conAssumeEvent);
    }

    /**
     * @param thread
     * @param symbolicOperation
     */
    @Override
    public void nextSymAssumeRequest(Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymAssumeOperationRequest(symbolicOperation);
        System.out.println("[Random Strategy Message] : The result of the symbolic assume operation is " +
                RuntimeEnvironment.solverResult);

        if (RuntimeEnvironment.solverResult) {
            solver.updatePathSymbolicOperations(symbolicOperation);
        }

        SymAssumeEvent symAssumeEvent = RuntimeEnvironment.createSymAssumeEvent(thread, symbolicOperation);
        RuntimeEnvironment.eventsRecord.add(symAssumeEvent);
    }

    /**
     * Handles the next symbolic operation request of a given thread.
     * <p>
     * This method handles the next symbolic operation request of a given thread. It checks if the symbolic operation
     * is dependent on other formulas. If the symbolic operation is dependent, it creates a dependency operation and
     * solves the dependent formulas. If the symbolic operation is free from dependencies, it solves the formula. The
     * method updates the path symbolic operations and creates a {@link SymExecutionEvent} for the symbolic operation.
     * </p>
     *
     * @param thread            is the thread that is going to execute the symbolic operation.
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     */
    @Override
    public void nextSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        solver.computeNewSymbolicOperationRequest(symbolicOperation);

        System.out.println("[Random Strategy Message] : The result of the symbolic arithmetic operation is " +
                RuntimeEnvironment.solverResult);
        //updatePathSymbolicOperations(symbolicOperation, thread);
        solver.updatePathSymbolicOperations(symbolicOperation);
        SymExecutionEvent symExecutionEvent = RuntimeEnvironment.createSymExecutionEvent(thread,
                symbolicOperation.getFormula().toString(), solver.bothSatUnsat);
        RuntimeEnvironment.eventsRecord.add(symExecutionEvent);
    }

    /**
     * @param assumeBlockedEvent
     */
    @Override
    public void nextAssumeBlockedRequest(AssumeBlockedEvent assumeBlockedEvent) {
        RuntimeEnvironment.eventsRecord.add(assumeBlockedEvent);
    }

    @Override
    public void nextMainStartEvent(MainStartEvent mainStartEvent) {
        RuntimeEnvironment.eventsRecord.add(mainStartEvent);
    }

    /**
     * Creates an {@link EnterMonitorEvent} for the corresponding entering a monitor request of a thread
     * <p>
     * This method creates a {@link EnterMonitorEvent} for the corresponding entering a monitor request of a thread.
     * The created {@link EnterMonitorEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * </p>
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
     * Creates an {@link ExitMonitorEvent} for the corresponding exiting a monitor request of a thread
     * <p>
     * This method creates an {@link ExitMonitorEvent} for the corresponding exiting a monitor request of a thread.
     * The created {@link ExitMonitorEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * The method also analyzes the suspended threads for the released monitor.
     * </p>
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
     * Creates a {@link JoinEvent} for the corresponding joining a thread request of a thread
     * <p>
     * This method creates a {@link JoinEvent} for the corresponding joining a thread request of a thread.
     * The created {@link JoinEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * </p>
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
        Object monitor = readExEvent.getLoc().getInstance();
        if (RuntimeEnvironment.monitorList.containsKey(monitor)) {
            RuntimeEnvironment.monitorRequest.put(thread, monitor);
            if (monitorsDeadlockDetection()) {
                System.out.println(
                        "[Random Strategy Message] : There is a deadlock between the threads in using " +
                                "the monitors"
                );
                RuntimeEnvironment.deadlockHappened = true;
                RuntimeEnvironment.executionFinished = true;
                nextDeadlockEvent(thread);
                return null;
            } else {
                System.out.println(
                        "[Random Strategy Message] : There is no deadlock between the threads in using " +
                                "the monitors"
                );
                ArrayList<ThreadEvent> threadEvents = new ArrayList<>();
                threadEvents.add(readExEvent);
                threadEvents.add(writeExEvent);
                cachedEvents.put(readExEvent.getTid(), threadEvents);
            }
        } else {
            RuntimeEnvironment.monitorList.put(monitor, thread);
            writeExEvent.setOperationSuccess(true);
            RuntimeEnvironment.eventsRecord.add(readExEvent);
            RuntimeEnvironment.eventsRecord.add(writeExEvent);
        }
        return pickNextThread();
    }

    /**
     * @param tid
     */
    @Override
    public void handleCachedCASEvent(int tid) {
        RuntimeEnvironment.eventsRecord.add(cachedEvents.get(tid).get(0));
        RuntimeEnvironment.eventsRecord.add(cachedEvents.get(tid).get(1));
        WriteExEvent writeExEvent = (WriteExEvent) cachedEvents.get(tid).get(1);
        writeExEvent.setOperationSuccess(true);
        cachedEvents.remove(tid);
    }


    /**
     * Handles the next park request of a given thread.
     * <p>
     * This method handles the next park request of a given thread. It creates a {@link ParkEvent} for the corresponding
     * parking request of a thread and records it. The method also checks if the thread has a parking permit. If the
     * thread has a parking permit, it creates an {@link UnparkEvent} for the corresponding unparking request of the
     * thread and records it. If the thread does not have a parking permit, it parks the thread.
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
            UnparkEvent unparkRequestEvent = RuntimeEnvironment.createUnparkEvent(thread);
            RuntimeEnvironment.eventsRecord.add(unparkRequestEvent);
        } else {
            parkThread(thread);
        }
    }

    /**
     * Handles the next unpark request of a given thread.
     * <p>
     * This method handles the next unpark request of a given thread. It creates an {@link UnparkingEvent} for the
     * corresponding unparking request of a thread and records it. The method also checks if the unparkee thread is
     * parked. If the unparkee thread is parked, it unparks the thread and creates an {@link UnparkEvent} for the
     * corresponding unparking request of the thread and records it. If the unparkee thread is not parked, it grants
     * the thread a parking permit.
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
            UnparkEvent unparkRequestEvent = RuntimeEnvironment.createUnparkEvent(unparkeeThread);
            RuntimeEnvironment.eventsRecord.add(unparkRequestEvent);
        } else {
            long tid = RuntimeEnvironment.threadIdMap.get(unparkeeThread.getId());
            RuntimeEnvironment.threadParkingPermit.put(tid, true);
        }
    }

    /**
     * Handles the next join request of a given thread.
     * <p>
     * This method handles the next join request of a given thread. It records the join request and join response
     * threads in the {@link RuntimeEnvironment#joinRequest} map. The method also selects the next random thread to
     * run.
     * </p>
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     * @return the next random thread to run.
     */
    @Override
    public Thread nextJoinRequest(Thread joinReq, Thread joinRes) {
        RuntimeEnvironment.joinRequest.put(joinReq, joinRes);
        return pickNextReadyThread();
    }

    /**
     * Records the created {@link ReadEvent} for the corresponding reading a variable request of a thread
     * <p>
     * This method records the created {@link ReadEvent} for the corresponding reading a variable request of a thread.
     * The {@link ReadEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * </p>
     *
     * @param readEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        RuntimeEnvironment.eventsRecord.add(readEvent);
    }

    /**
     * Records the created {@link WriteEvent} for the corresponding writing a variable request of a thread
     * <p>
     * This method records the created {@link WriteEvent} for the corresponding writing a variable request of a thread.
     * The {@link WriteEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        RuntimeEnvironment.eventsRecord.add(writeEvent);
        if (writeEvent.getLoc().getInstance() instanceof JMCLock lock) {
            RuntimeEnvironment.monitorList.remove(lock);
            analyzeSuspendedThreadsForMonitor(lock);
        }
    }

    @Override
    public void nextSendEvent(SendEvent sendEvent) {
        RuntimeEnvironment.eventsRecord.add(sendEvent);
        executeSendEvent(sendEvent);
    }

    @Override
    public void nextReceiveEvent(ReceiveEvent receiveEvent) {
        if (receiveEvent.getPredicate() == null) {
            handleFreeMessage(receiveEvent);
        } else {
            handleConditionalMessage(receiveEvent);
        }
        RuntimeEnvironment.eventsRecord.add(receiveEvent);
    }

    @Override
    public boolean nextBlockingReceiveRequest(ReceiveEvent receiveEvent) {
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        BlockingRecvReq blockingRecvReq = RuntimeEnvironment.createBlockingRecvReq(jmcThread, receiveEvent);
        RuntimeEnvironment.eventsRecord.add(blockingRecvReq);
        if (receiveEvent.getPredicate() == null) {
            return handleFreeBlockingRecvReq(receiveEvent, jmcThread, blockingRecvReq);
        } else {
            return handleConditionalBlockingRecvReq(receiveEvent, jmcThread, blockingRecvReq);
        }
    }

    private boolean handleFreeBlockingRecvReq(ReceiveEvent receiveEvent, JMCThread jmcThread, BlockingRecvReq blockingRecvReq) {
        Message message = jmcThread.findRandomMessage(random);
        if (message == null) {
            RuntimeEnvironment.removeBlockedThreadFromReadyQueue(jmcThread, receiveEvent);
            blockingRecvReq.setBlocked(true);
            return false;
        }
        receiveEvent.setRf(message.getSendEvent());
        receiveEvent.setValue(message);
        blockingRecvReq.setBlocked(false);
        return true;
    }

    private boolean handleConditionalBlockingRecvReq(ReceiveEvent receiveEvent, JMCThread jmcThread, BlockingRecvReq blockingRecvReq) {
        List<Message> matchedMessages = jmcThread.computePredicateMessage(receiveEvent.getPredicate());
        if (matchedMessages.isEmpty()) {
            RuntimeEnvironment.removeBlockedThreadFromReadyQueue(jmcThread, receiveEvent);
            blockingRecvReq.setBlocked(true);
            return false;
        } else {
            int randomMessageIndex = random.nextInt(matchedMessages.size());
            jmcThread.findNextMessageIndex(matchedMessages.get(randomMessageIndex));
            receiveEvent.setRf(matchedMessages.get(randomMessageIndex).getSendEvent());
            receiveEvent.setValue(matchedMessages.get(randomMessageIndex));
            blockingRecvReq.setBlocked(false);
            return true;
        }
    }

    private void handleConditionalMessage(ReceiveEvent receiveEvent) {
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        List<Message> matchedMessages = jmcThread.computePredicateMessage(receiveEvent.getPredicate());
        if (matchedMessages.isEmpty()) {
            jmcThread.noMessageExists();
            receiveEvent.setRf(null);
            receiveEvent.setValue(null);
        } else {
            int randomMessageIndex = random.nextInt(matchedMessages.size());
            jmcThread.findNextMessageIndex(matchedMessages.get(randomMessageIndex));
            receiveEvent.setRf(matchedMessages.get(randomMessageIndex).getSendEvent());
            receiveEvent.setValue(matchedMessages.get(randomMessageIndex));
        }
    }

    private void handleFreeMessage(ReceiveEvent receiveEvent) {
        JMCThread jmcThread = (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        Message message = jmcThread.findRandomMessage(random);
        if (message == null) {
            jmcThread.noMessageExists();
            receiveEvent.setRf(null);
            receiveEvent.setValue(null);
        } else {
            // No need to execute the following.
            //jmcThread.findNextMessageIndex(message);
            receiveEvent.setRf(message.getSendEvent());
            receiveEvent.setValue(message);
        }
    }

    /**
     * Creates a {@link FinishEvent} for the corresponding finishing execution request of a thread
     * <p>
     * This method creates a {@link FinishEvent} for the corresponding finishing execution request of a thread.
     * The created {@link FinishEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * The method also analyzes the suspended threads for joining the finished thread.
     * </p>
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextFinishEvent(Thread thread) {
        FinishEvent finishEvent = RuntimeEnvironment.createFinishEvent(thread);
        RuntimeEnvironment.eventsRecord.add(finishEvent);
        analyzeSuspendedThreadsForJoin(thread);
    }

    /**
     * Handles the next finish request of a given thread.
     * <p>
     * This method calls the {@link #nextFinishEvent(Thread)} method to handle the next finish request of a given
     * thread. The method also selects the next random thread to run.
     * </p>
     *
     * @param thread is the thread that is going to be finished.
     * @return the next random thread to run.
     */
    @Override
    public Thread nextFinishRequest(Thread thread) {
        nextFinishEvent(thread);
        return pickNextReadyThread();
    }

    /**
     * Handles the next failure event of a given thread.
     * <p>
     * This method creates a {@link FailureEvent} for the corresponding failure event of a thread. The created
     * {@link FailureEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * </p>
     *
     * @param thread is the thread that is going to fail.
     */
    @Override
    public void nextFailureEvent(Thread thread) {
        FailureEvent failureEvent = RuntimeEnvironment.createFailureEvent(thread);
        RuntimeEnvironment.eventsRecord.add(failureEvent);
    }

    /**
     * Handles the next deadlock event of a given thread.
     * <p>
     * This method creates a {@link DeadlockEvent} for the corresponding deadlock event of a thread. The created
     * {@link DeadlockEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * </p>
     *
     * @param thread is the thread that is going to be deadlocked.
     */
    @Override
    public void nextDeadlockEvent(Thread thread) {
        DeadlockEvent deadlockEvent = RuntimeEnvironment.createDeadlockEvent(thread);
        RuntimeEnvironment.eventsRecord.add(deadlockEvent);
    }

    /**
     * Saves the buggy execution trace.
     * <p>
     * This method saves the buggy execution trace in the buggy trace file.
     * </p>
     */
    @Override
    public void saveBuggyExecutionTrace() {
        try {
            FileOutputStream fileOut = new FileOutputStream(buggyTracePath + buggyTraceFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(RuntimeEnvironment.eventsRecord);
            out.close();
            fileOut.close();
            System.out.println("[Random Strategy Message] : Buggy execution trace is saved in " + buggyTracePath +
                    buggyTraceFile);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    /**
     * Picks the next thread to run.
     *
     * <p>This method picks the next thread to run based on the {@link #random} object.
     * </p>
     *
     * @return the next random thread to run.
     */
    @Override
    public Thread pickNextThread() {
        return pickNextReadyThread();
    }

    /**
     * Saves the current execution state.
     */
    @Override
    public void saveExecutionState() {
        //printExecutionTrace();
    }

    /**
     * Indicates whether the execution is done or not.
     *
     * <p>This method indicates whether the execution is done or not.
     *
     * @return true if the execution is done, otherwise false.
     */
    @Override
    public boolean done() {
        return (RuntimeEnvironment.numOfExecutions == RuntimeEnvironment.maxNumOfExecutions);
    }
}