package org.mpisws.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.runtime.ThreadCollection;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCFutureTask;
import org.mpisws.util.concurrent.JMCStarterThread;
import org.mpisws.util.concurrent.JMCThread;

import programStructure.*;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.FutureTask;

/**
 * The SearchStrategy interface defines the methods that any search strategy must implement. It
 * provides a way to manage the execution order of events in a multithreaded program. The interface
 * includes methods for handling various types of events including start, enter monitor, exit
 * monitor, join, read, write, finish, and symbolic arithmetic events. It also includes methods for
 * printing the execution trace and checking if the execution is done. The SearchStrategy interface
 * is designed to be implemented by classes that provide different strategies for managing the
 * execution order of events. The strategy is used by the SchedulerThread class to control the flow
 * of a program's execution and ensure a specific execution order of operations.
 */
public interface SearchStrategy {

    Logger LOGGER = LogManager.getLogger(SearchStrategy.class);

    /**
     * Represents the required strategy for the next start event.
     *
     * @param calleeThread is the thread that is going to be started.
     * @param callerThread is the thread that is going to call the start method of the calleeThread.
     */
    void nextStartEvent(Thread calleeThread, Thread callerThread);

    void nextMainStartEvent(MainStartEvent mainStartEvent);

    /**
     * Represents the required strategy for the next enter monitor event.
     *
     * @param thread is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    void nextEnterMonitorEvent(Thread thread, Object monitor);

    /**
     * Represents the required strategy for the next exit monitor event.
     *
     * @param thread is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    void nextExitMonitorEvent(Thread thread, Object monitor);

    /**
     * Represents the required strategy for the next join event.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    void nextJoinEvent(Thread joinReq, Thread joinRes);

    Thread nextCasRequest(Thread thread, ReadExEvent readExEvent, WriteExEvent writeExEvent);

    /**
     * Represents the required strategy for the next join request.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    Thread nextJoinRequest(Thread joinReq, Thread joinRes);

    /**
     * Handles the next enter monitor request of a given thread and monitor.
     *
     * <p>This method records the monitor request in the {@link RuntimeEnvironment#monitorRequest}
     * map. It also checks for a deadlock between the threads in using the monitors. If a deadlock
     * is detected, the method sets the {@link RuntimeEnvironment#deadlockHappened} flag to true and
     * the {@link RuntimeEnvironment#executionFinished} flag to true. Otherwise, the method selects
     * the next thread to run.
     *
     * @param thread is the thread that is requested to enter the monitor.
     * @param monitor is the monitor that is requested to be entered by the thread.
     * @return the next random thread to run.
     */
    default Thread nextEnterMonitorRequest(Thread thread, Object monitor) {
        MonitorRequestEvent monitorRequestEvent =
                RuntimeEnvironment.createMonitorRequestEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(monitorRequestEvent);
        RuntimeEnvironment.monitorRequest.put(thread, monitor);
        if (monitorsDeadlockDetection()) {
            LOGGER.debug("There is a deadlock between the threads in using the monitors");
            RuntimeEnvironment.deadlockHappened = true;
            RuntimeEnvironment.executionFinished = true;
            return null;
        } else {
            LOGGER.debug("There is no deadlock between the threads in using the monitors");
            return pickNextThread();
        }
    }

    /**
     * Represents the required strategy for the next read event.
     *
     * @param readEvent is the read event that is going to be executed.
     */
    void nextReadEvent(ReadEvent readEvent);

    /**
     * Represents the required strategy for the next read event.
     *
     * @param receiveEvent is the read event that is going to be executed.
     */
    void nextReceiveEvent(ReceiveEvent receiveEvent);

    /**
     * Represents the required strategy for the next write event.
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    void nextWriteEvent(WriteEvent writeEvent);

    /**
     * Represents the required strategy for the next write event.
     *
     * @param sendEvent is the write event that is going to be executed.
     */
    void nextSendEvent(SendEvent sendEvent);

    boolean nextBlockingReceiveRequest(ReceiveEvent receiveEvent);

    /**
     * Represents the required strategy for the next finish event.
     *
     * @param thread is the thread that is going to be finished.
     */
    void nextFinishEvent(Thread thread);

    /**
     * Represents the required strategy for the next failure event.
     *
     * @param thread is the thread that is going to be finished.
     */
    void nextFailureEvent(Thread thread);

    /**
     * Represents the required strategy for the next deadlock event.
     *
     * @param thread is the thread that is going to be finished.
     */
    void nextDeadlockEvent(Thread thread);

    /**
     * Represents the required strategy for the next finish request.
     *
     * @param thread is the thread that is going to be finished.
     */
    Thread nextFinishRequest(Thread thread);

    /**
     * Represents the required strategy for the next park request.
     *
     * @param thread is the thread that is going to be parked.
     */
    void nextParkRequest(Thread thread);

    void nextConAssumeRequest(ConAssumeEvent conAssumeEvent);

    void nextSymAssumeRequest(Thread thread, SymbolicOperation symbolicOperation);

    void nextAssumeBlockedRequest(AssumeBlockedEvent assumeBlockedEvent);

    /**
     * Represents the required strategy for the next unpark request.
     *
     * @param unparkerThread is the thread that is going to unpark unparkeeThread.
     * @param unparkeeThread is the thread that is going to be unparked by unparkerThread.
     */
    void nextUnparkRequest(Thread unparkerThread, Thread unparkeeThread);

    /**
     * Represents the required strategy for the next symbolic operation request.
     *
     * @param thread is the thread that is going to execute the symbolic operation.
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     */
    void nextSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation);

    /**
     * Updates the path symbolic operations.
     *
     * <p>If the solver result is true for the symbolic operation, the method updates the path and
     * thread symbolic operations. Otherwise, it updates the path symbolic operations with the
     * negated symbolic operation.
     *
     * @param thread is the thread that is going to execute the symbolic operation.
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     */
    default void updatePathSymbolicOperations(SymbolicOperation symbolicOperation, Thread thread) {
        if (RuntimeEnvironment.solverResult) {
            updatePathAndThreadSymbolicOperations(symbolicOperation, thread);
        } else {
            updatePathSymbolicOperationsWithNegate(symbolicOperation, thread);
        }
    }

    /**
     * Updates the path and thread symbolic operations.
     *
     * <p>It adds the symbolic operation to the {@link RuntimeEnvironment#pathSymbolicOperations}
     * list and the {@link RuntimeEnvironment#threadSymbolicOperation} map.
     *
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     * @param thread is the thread that is going to execute the symbolic operation.
     */
    private void updatePathAndThreadSymbolicOperations(
            SymbolicOperation symbolicOperation, Thread thread) {
        RuntimeEnvironment.pathSymbolicOperations.add(symbolicOperation);
        if (RuntimeEnvironment.threadSymbolicOperation.containsKey(
                RuntimeEnvironment.threadIdMap.get(thread.getId()))) {
            RuntimeEnvironment.threadSymbolicOperation
                    .get(RuntimeEnvironment.threadIdMap.get(thread.getId()))
                    .add(symbolicOperation);
        } else {
            List<SymbolicOperation> symbolicOperations = new ArrayList<>();
            symbolicOperations.add(symbolicOperation);
            RuntimeEnvironment.threadSymbolicOperation.put(
                    RuntimeEnvironment.threadIdMap.get(thread.getId()), symbolicOperations);
        }
    }

    /**
     * Updates the path symbolic operations with the negated symbolic operation.
     *
     * @param symbolicOperation is the symbolic operation that is going to be executed.
     * @param thread is the thread that is going to execute the symbolic operation.
     */
    private void updatePathSymbolicOperationsWithNegate(
            SymbolicOperation symbolicOperation, Thread thread) {
        updatePathAndThreadSymbolicOperations(negateSymbolicOperation(symbolicOperation), thread);
    }

    /**
     * Negates the given symbolic operation.
     *
     * <p>It uses the solver to negate the formula of the symbolic operation.
     *
     * @param symbolicOperation is the symbolic operation that is going to be negated.
     * @return the negated symbolic operation.
     */
    private SymbolicOperation negateSymbolicOperation(SymbolicOperation symbolicOperation) {
        symbolicOperation.setFormula(
                RuntimeEnvironment.solver.negateFormula(symbolicOperation.getFormula()));
        LOGGER.debug("The negated formula is saved {}", symbolicOperation.getFormula());
        return symbolicOperation;
    }

    /** Prints the current execution trace. */
    default void printExecutionTrace() {
        if (RuntimeEnvironment.verbose) {
            LOGGER.debug("Execution trace:");
            for (Event event : RuntimeEnvironment.eventsRecord) {
                int index = RuntimeEnvironment.eventsRecord.indexOf(event) + 1;
                LOGGER.debug("{}.{}", index, event);
            }
        }
    }

    default void executeSendEvent(SendEvent sendEvent) {
        //        JMCThread receiverThread = (JMCThread)
        // RuntimeEnvironment.threadObjectMap.get(RuntimeEnvironment.threadIdMap.get(sendEvent.getReceiverId()));
        JMCThread receiverThread =
                (JMCThread) RuntimeEnvironment.findThreadObject(sendEvent.getReceiverId());
        receiverThread.pushMessage(sendEvent.getValue());
    }

    /**
     * Indicates whether the execution is done or not.
     *
     * @return true if the execution is done, otherwise false.
     */
    boolean done();

    /**
     * Selects the next random thread to run.
     *
     * <p>This method is used to select the next random thread to run. It checks the monitor request
     * and join request of the candidate thread and handles them appropriately.
     *
     * @return the next random thread to run.
     */
    default Thread pickNextReadyThread() {
        Optional<ThreadCollection> readyThread =
                Optional.ofNullable(RuntimeEnvironment.readyThread);
        if (!readyThread.get().isEmpty()) {
            if (readyThread.get().size() > 1) {
                LOGGER.debug("There are more than one thread in the ready thread list");
                Thread randomElement = readyThread.get().getNext();
                return handleChosenThreadRequest(randomElement);
            } else { // readyThread.get().size() == 1
                LOGGER.debug("Only one thread is in the ready thread list");
                return handleChosenThreadRequest(readyThread.get().getNext());
            }
        } else if (!RuntimeEnvironment.blockedRecvThreadMap.isEmpty()) {
            LOGGER.debug("Ready list is empty, but there are blocked receive threads");
            if (computeUnblockedRecvThread()) {
                return pickNextReadyThread();
            } else {
                LOGGER.error(
                        "There is a deadlock between the threads in executing blocking receive"
                            + " operations");
                printExecutionTrace();
                saveBuggyExecutionTrace();
                LOGGER.error("[*** Resource Usage ***]");
                RuntimeEnvironment.printFinalMessage();
                System.exit(0);
                return null;
            }
        } else if (RuntimeEnvironment.suspendedThreads.size() > 0) {
            LOGGER.error("There is a deadlock between the threads in using monitors");
            printExecutionTrace();
            saveBuggyExecutionTrace();
            System.exit(0);
            return null;
        } else {
            LOGGER.debug("There is no thread in the ready list");
            LOGGER.debug("The scheduler thread is going to terminate");
            return null;
        }
    }

    default boolean computeUnblockedRecvThread() {
        boolean result = false;
        for (Map.Entry<Long, ReceiveEvent> entry :
                RuntimeEnvironment.blockedRecvThreadMap.entrySet()) {
            if (isMessageAvailable(entry.getValue())) {
                JMCThread jmcThread =
                        (JMCThread) RuntimeEnvironment.findThreadObject(entry.getValue().getTid());
                RuntimeEnvironment.addUnblockedThreadToReadyQueue(jmcThread, entry.getValue());
                result = true;
                LOGGER.debug(
                        "The thread {} is unblocked, since the message is available",
                        jmcThread.getName());
            }
        }
        return result;
    }

    default boolean isMessageAvailable(ReceiveEvent receiveEvent) {
        JMCThread jmcThread =
                (JMCThread) RuntimeEnvironment.findThreadObject(receiveEvent.getTid());
        if (receiveEvent.getPredicate() == null) {
            return !jmcThread.isMessageQueueEmpty();
        } else {
            return jmcThread.isPredicateSatisfiable(receiveEvent.getPredicate());
        }
    }

    /**
     * Handles the monitor request and join request of the candidate thread.
     *
     * @param thread the candidate thread.
     * @return the candidate thread if it can run, otherwise selects another random thread.
     */
    default Thread handleChosenThreadRequest(Thread thread) {
        if (RuntimeEnvironment.monitorRequest.containsKey(thread)) {
            return handleMonitorRequest(thread);
        } else if (RuntimeEnvironment.joinRequest.containsKey(thread)) {
            return handleJoinRequest(thread);
        } else if (thread instanceof JMCStarterThread jmcStarterThread) {
            return handleStarterThread(jmcStarterThread);
        } else {
            return thread;
        }
    }

    default Thread handleStarterThread(JMCStarterThread jmcStarterThread) {
        if (jmcStarterThread.hasTask) {
            return jmcStarterThread;
        } else {
            return handleIdleStarterThread(jmcStarterThread);
        }
    }

    default Thread handleIdleStarterThread(JMCStarterThread jmcStarterThread) {
        BlockingQueue<Runnable> queue =
                RuntimeEnvironment.workQueue.get(jmcStarterThread.threadPoolExecutorId);
        if (queue.isEmpty()) {
            RuntimeEnvironment.readyThread.remove(jmcStarterThread);
            RuntimeEnvironment.idleThreadsInPool
                    .get(jmcStarterThread.threadPoolExecutorId)
                    .add(jmcStarterThread);
            return pickNextReadyThread();
        } else {
            return jmcStarterThread;
        }
    }

    /**
     * Handles the monitor request of the candidate thread.
     *
     * <p>This method checks whether the monitor is available or not. If the monitor is available,
     * the method removes the monitor request from the {@link RuntimeEnvironment#monitorRequest} map
     * and calls the {@link #nextEnterMonitorEvent} method. Otherwise, the method suspends the
     * candidate thread and selects another random thread to run.
     *
     * @param thread the candidate thread.
     * @return the candidate thread if it can run, otherwise selects another random thread.
     */
    default Thread handleMonitorRequest(Thread thread) {
        Object monitor = RuntimeEnvironment.monitorRequest.get(thread);
        LOGGER.debug(
                "Thread-{} is requested to enter the monitor {}",
                RuntimeEnvironment.threadIdMap.get(thread.getId()),
                monitor);
        if (RuntimeEnvironment.monitorList.containsKey(monitor)) {
            LOGGER.debug(
                    "The monitor {} is already in use by {}",
                    monitor,
                    RuntimeEnvironment.threadIdMap.get(
                            RuntimeEnvironment.monitorList.get(monitor).getId()));
            suspendThread(thread);
            return pickNextReadyThread();
        } else {
            LOGGER.debug("The monitor {} is available", monitor);
            RuntimeEnvironment.monitorRequest.remove(thread, monitor);
            LOGGER.debug(
                    "The request of {} to enter the monitor {} is removed from the monitorRequest",
                    thread.getName(),
                    monitor);
            handleCachedCASEvent(RuntimeEnvironment.threadIdMap.get(thread.getId()).intValue());
            return thread;
        }
    }

    /**
     * Handles the join request of the candidate thread.
     *
     * <p>This method checks whether the join request is available or not. If the join request is
     * available, the method removes the join request from the {@link
     * RuntimeEnvironment#joinRequest} map and calls the {@link #nextJoinEvent} method. Otherwise,
     * the method suspends the candidate thread and selects another random thread to run.
     *
     * @param thread the candidate thread.
     * @return the candidate thread if it can run, otherwise selects another random thread.
     */
    default Thread handleJoinRequest(Thread thread) {
        Thread joinRes = RuntimeEnvironment.joinRequest.get(thread);
        LOGGER.debug("{} is requested to join {}", thread.getName(), joinRes.getName());
        if (!RuntimeEnvironment.createdThreadList.contains(joinRes)
                && !RuntimeEnvironment.readyThread.contains(joinRes)) {
            RuntimeEnvironment.joinRequest.remove(thread, joinRes);
            LOGGER.debug(
                    "Request of {} to join {} removed from the joinRequest. {} not in"
                        + " createdThreadList or readyThread list",
                    thread.getName(),
                    joinRes.getName(),
                    joinRes.getName());
            nextJoinEvent(thread, joinRes);
            return thread;
        } else {
            LOGGER.debug("However, {} is not finished yet", joinRes.getName());
            suspendThread(thread);
            return pickNextReadyThread();
        }
    }

    /**
     * Analyzes the suspended threads that are waiting for the monitor.
     *
     * @param monitor the monitor that the suspended threads are waiting for.
     */
    default void analyzeSuspendedThreadsForMonitor(Object monitor) {
        List<Thread> threads = findSuspendedThreads(monitor);
        if (!threads.isEmpty()) {
            for (Thread t : threads) {
                unsuspendThread(t);
            }
        }
    }

    /**
     * Analyzes the suspended threads that are waiting for the join request.
     *
     * @param joinRes the thread that the suspended threads are waiting to join.
     */
    default void analyzeSuspendedThreadsForJoin(Thread joinRes) {
        List<Thread> threads = findSuspendedThreads(joinRes);
        if (!threads.isEmpty()) {
            for (Thread t : threads) {
                unsuspendThread(t);
            }
        }
    }

    /**
     * Suspends the given thread.
     *
     * <p>This method is used to suspend the selected thread and remove it from the {@link
     * RuntimeEnvironment#readyThread} list and add it to the {@link
     * RuntimeEnvironment#suspendedThreads} list. This action is required when the selected thread
     * is waiting for a monitor or a join request.
     *
     * @param thread the selected thread.
     */
    default void suspendThread(Thread thread) {
        LOGGER.debug("{} suspended", thread.getName());
        RuntimeEnvironment.readyThread.remove(thread);
        RuntimeEnvironment.suspendedThreads.add(thread);
    }

    /**
     * Parks the given thread.
     *
     * <p>This method is used to park the selected thread and remove it from the {@link
     * RuntimeEnvironment#readyThread} list and add it to the {@link
     * RuntimeEnvironment#parkedThreadList} list.
     *
     * @param thread the selected thread which is going to be parked.
     */
    default void parkThread(Thread thread) {
        LOGGER.debug("{} parked", thread.getName());
        if (RuntimeEnvironment.readyThread.contains(thread)
                && !RuntimeEnvironment.parkedThreadList.contains(thread)) {
            RuntimeEnvironment.readyThread.remove(thread);
            RuntimeEnvironment.parkedThreadList.add(thread);
        }
    }

    /**
     * Unparks the given thread.
     *
     * <p>This method is used to unpark the selected thread and remove it from the {@link
     * RuntimeEnvironment#parkedThreadList} list and add it to the {@link
     * RuntimeEnvironment#readyThread} list.
     *
     * @param thread the selected thread which is going to be unparked.
     */
    default void unparkThread(Thread thread) {
        LOGGER.debug("{} unparked", thread.getName());
        if (RuntimeEnvironment.parkedThreadList.contains(thread)
                && !RuntimeEnvironment.readyThread.contains(thread)) {
            RuntimeEnvironment.parkedThreadList.remove(thread);
            RuntimeEnvironment.readyThread.add(thread);
        }
    }

    /**
     * Unsuspend the given thread.
     *
     * <p>This method is used to unsuspend the selected thread and remove it from the {@link
     * RuntimeEnvironment#suspendedThreads} list and add it to the {@link
     * RuntimeEnvironment#readyThread} list. This action is required when the monitor or join
     * request of the selected thread is available.
     *
     * @param thread the selected thread.
     */
    default void unsuspendThread(Thread thread) {
        LOGGER.debug("{} unsuspended", thread.getName());
        RuntimeEnvironment.suspendedThreads.remove(thread);
        RuntimeEnvironment.readyThread.add(thread);
    }

    /**
     * Finds the suspended threads that are waiting for the given monitor.
     *
     * <p>This method checks for each suspended thread in the {@link
     * RuntimeEnvironment#suspendedThreads} list whether the monitor request of the thread is equal
     * to the given monitor or not. If the monitor request of the thread is equal to the given
     * monitor, the thread is added to the list of suspended threads that are waiting for the
     * monitor.
     *
     * @param monitor the monitor that the suspended threads are waiting for.
     * @return the list of suspended threads that are waiting for the monitor.
     */
    default List<Thread> findSuspendedThreads(Object monitor) {
        return RuntimeEnvironment.suspendedThreads.stream()
                .filter(thread -> RuntimeEnvironment.monitorRequest.get(thread) == monitor)
                .toList();
    }

    /**
     * Finds the suspended threads that are waiting for the given thread.
     *
     * <p>This method checks for each suspended thread in the {@link
     * RuntimeEnvironment#suspendedThreads} list whether the join request of the thread is equal to
     * the given thread or not. If the join request of the thread is equal to the given thread, the
     * thread is added to the list of suspended threads that are waiting for the join request.
     *
     * @param joinRes the thread that the suspended threads are waiting to join.
     * @return the list of suspended threads that are waiting for the join request.
     */
    default List<Thread> findSuspendedThreads(Thread joinRes) {
        return RuntimeEnvironment.suspendedThreads.stream()
                .filter(thread -> RuntimeEnvironment.joinRequest.get(thread) == joinRes)
                .toList();
    }

    default Thread nextGetFutureRequest(Thread thread, FutureTask future) {
        if (future instanceof JMCFutureTask jmcFutureTask) {
            if (!jmcFutureTask.isFinished) {
                RuntimeEnvironment.readyThread.remove(thread);
                RuntimeEnvironment.waitingThreadForFuture.put(future, thread);
            }
        } else {
            LOGGER.debug("The FutureTask is not an instance of JMCFutureTask");
            System.exit(0);
        }
        return pickNextThread();
    }

    /**
     * Picks the next thread to run.
     *
     * @return the next thread to run.
     */
    Thread pickNextThread();

    /** Saves the execution state. */
    void saveExecutionState();

    /** Saves the buggy execution trace. */
    void saveBuggyExecutionTrace();

    /**
     * Detects potential deadlocks in the threads that are waiting to enter a monitor.
     *
     * <p>This method is used to detect potential deadlocks in the threads that are waiting to enter
     * a monitor. It computes the transitive closure of the (@monitorRequest \cup @monitorList)
     * relation and checks whether the relation is irreflexive or not. If the relation is
     * irreflexive, there is no deadlock and the method returns false. Otherwise, there is a
     * deadlock and the method returns true.
     *
     * @return {@code true} if there is a deadlock, {@code false} otherwise.
     */
    default boolean monitorsDeadlockDetection() {
        LOGGER.debug("The deadlock detection phase is started");
        Optional<Map<Thread, Thread>> threadClosure = computeTransitiveClosure();
        if (threadClosure.isPresent()) {
            return checkIrreflexivity(threadClosure.get());
        } else {
            LOGGER.debug("There is no need to check the deadlock");
            return false;
        }
    }

    private Optional<Map<Thread, Thread>> computeTransitiveClosure() {
        if (RuntimeEnvironment.monitorList.isEmpty()) {
            return Optional.empty();
        } else {
            Map<Thread, Thread> threadClosure = new HashMap<>();
            // Compute the primitive closure of the (@monitorRequest \cup @monitorList) relation.
            for (Map.Entry<Thread, Object> entry : RuntimeEnvironment.monitorRequest.entrySet()) {
                for (Map.Entry<Object, Thread> entry2 : RuntimeEnvironment.monitorList.entrySet()) {
                    if (entry.getValue().equals(entry2.getKey())) {
                        threadClosure.put(entry.getKey(), entry2.getValue());
                    }
                }
            }
            // Compute the complete transitive closure of the (@monitorRequest \cup @monitorList)
            // relation.
            boolean addedNewPairs = true;
            while (addedNewPairs) {
                addedNewPairs = false;
                for (Map.Entry<Thread, Thread> entry : threadClosure.entrySet()) {
                    for (Map.Entry<Thread, Thread> entry2 : threadClosure.entrySet()) {
                        if (entry.getValue().equals(entry2.getKey())
                                && threadClosure.entrySet().stream()
                                        .noneMatch(
                                                e ->
                                                        e.getKey().equals(entry.getKey())
                                                                && e.getValue()
                                                                        .equals(
                                                                                entry2
                                                                                        .getValue()))) {
                            threadClosure.put(entry.getKey(), entry2.getValue());
                            addedNewPairs = true;
                        }
                    }
                }
            }
            return Optional.of(threadClosure);
        }
    }

    private boolean checkIrreflexivity(Map<Thread, Thread> threadClosure) {
        for (Map.Entry<Thread, Thread> entry : threadClosure.entrySet()) {
            if (entry.getKey().equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    default void handleCachedCASEvent(int tid) {}
}
