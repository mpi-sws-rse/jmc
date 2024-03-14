package org.example.runtime;

import org.example.checker.SearchStrategy;
import org.example.checker.StrategyType;
import org.example.checker.strategy.RandomStrategy;
import programStructure.ReadEvent;
import programStructure.WriteEvent;
import java.util.*;

public class SchedulerThread extends Thread {

    /**
     * @property {@link SearchStrategy} is used to select the next thread to be executed based on the selected strategy.
     */
    private SearchStrategy searchStrategy;

    /**
     * The following constructor initializes the {@link SearchStrategy} based on the selected strategy type.
     */
    public SchedulerThread() {
        createSearchStrategy();
    }

    /**
     * Initializes the {@link SearchStrategy} based on the selected strategy type.
     * <p>
     * The following method is used to initialize the {@link SearchStrategy} based on the selected strategy type.
     * If the selected strategy type is {@link StrategyType#RANDOMSTRAREGY}, the method initializes the {@link RandomStrategy}.
     * Otherwise, the method throws an {@link IllegalArgumentException}.
     *
     * @throws IllegalArgumentException if the selected strategy type is not supported.
     */
    private void createSearchStrategy() {
        StrategyType strategyType = RuntimeEnvironment.strategyType;
        if (strategyType == StrategyType.RANDOMSTRAREGY) {
            searchStrategy = new RandomStrategy();
        } else {
            throw new IllegalArgumentException("Unsupported strategy type: " + strategyType);
        }
    }

    /**
     * Executes the scheduler thread.
     * <p>
     * This method is used to execute the scheduler thread. It waits until the other thread (which is the main thread)
     * waits, prints a message indicating that the flow of the program is controlled by the SchedulerThread, and then
     * handles the events until the execution is finished.
     */
    @Override
    public void run() {
        waitForMainThread();
        printStartMessage();
        while (!RuntimeEnvironment.executionFinished) {
            waitForOtherThread();
            if (checkAssertFlag()) {
                handleAssertFail();
                break;
            }
            System.out.println("[Scheduler Thread Message] : All threads are in waiting state");
            waitForThreadStateChange();
            eventHandler();
        }
        printEndMessage();
        wasLastExecution();
        notifyMainThread();
    }

    /**
     * Waits for the main thread.
     * <p>
     * This method is used to wait until the other thread (which is the main thread) waits.
     */
    private void waitForMainThread() {
        RuntimeEnvironment.getPermission(RuntimeEnvironment.createdThreadList.get(0));
    }

    /**
     * Prints the start message.
     * <p>
     * This method is used to print the start message.
     */
    private void printStartMessage() {
        System.out.println("******************************************************************************************");
        System.out.println("[*** From this point on, the flow of the program is controlled by the SchedulerThread ***]");
        System.out.println("******************************************************************************************");
    }

    /**
     * Waits for the other thread.
     * <p>
     * This method is used to wait until the only other running thread requests to wait.
     */
    private void waitForOtherThread() {
        while (true) {
            synchronized (RuntimeEnvironment.threadWaitReqLock) {
                if (RuntimeEnvironment.threadWaitReq != null) {
                    break;
                }
                Thread.yield();
            }
        }
    }

    /**
     * Checks the assert flag.
     * <p>
     * This method is used to check whether the wait request is due to an assert violation or not.
     *
     * @return {@code true} if the wait request is due to an assert violation, {@code false} otherwise.
     */
    private boolean checkAssertFlag() {
        return RuntimeEnvironment.assertFlag;
    }

    /**
     * Handles the assert fail.
     * <p>
     * This method is used to handle the assert fail. It sets the execution to be finished, prints a message indicating
     * that the SchedulerThread requested to finish, and then prints the executed events.
     */
    private void handleAssertFail() {
        RuntimeEnvironment.executionFinished = true;
        System.out.println("******************************************************************************************");
        System.out.println("[*** Assertion Fail ***]");
        System.out.println("[*** Number of execution iteration : " + RuntimeEnvironment.numOfExecutions + " ***]");
        System.out.println("[*** The SchedulerThread requested to FINISH***]");
        System.out.println("******************************************************************************************");
        searchStrategy.printExecutionTrace();
        System.exit(0);
    }

    /**
     * Waits for the state of the only other running thread to change to the WAIT state.
     * <p>
     * This method is used to wait until the state of the only other running thread changes to the WAIT state.
     */
    private void waitForThreadStateChange() {
        synchronized (RuntimeEnvironment.locks.get(getThreadId(RuntimeEnvironment.threadWaitReq))) {
            System.out.println("[Scheduler Thread Message] : Scheduling phase begins");
        }
    }

    /**
     * Prints the end message.
     * <p>
     * This method is used to print the end message.
     */
    private void printEndMessage() {
        System.out.println("******************************************************************************************");
        System.out.println("[*** The SchedulerThread requested to FINISH***]");
        System.out.println("******************************************************************************************");
        searchStrategy.printExecutionTrace();
    }

    /**
     * Notifies the main thread to continue the execution to finish the current execution iteration.
     * <p>
     * This method is used to notify the main thread to continue the execution to finish the current execution iteration.
     */
    private void notifyMainThread() {
        synchronized (RuntimeEnvironment.locks.get((long) 1)) {
            RuntimeEnvironment.locks.get((long) 1).notify();
        }
    }

    /**
     * Selects the next thread to be executed.
     * <p>
     * This method is used to select the next thread to be executed. If there are no more threads to be executed, it
     * sets the execution to be finished.
     * <br>
     * Finding the next thread to be executed is based on the selected strategy.
     */
    private void pickNextThread() {
        Optional<Thread> nextThread = Optional.ofNullable(searchStrategy.pickNextRandomThread());
        if (nextThread.isPresent()) {
            notifyThread(nextThread.get());
        } else {
            RuntimeEnvironment.executionFinished = true;
        }
    }

    /**
     * Notifies the specified thread to continue its execution.
     * <p>
     * This method is used to notify the specified thread to continue its execution. If the thread is null, it does
     * nothing.
     * <br>
     * The method retrieves the thread's ID, prints a message indicating that the thread is selected to run, and then
     * notifies the thread.
     *
     * @param thread the thread to be notified.
     */
    private void notifyThread(Thread thread) {
        Optional<Thread> optionalThread = Optional.ofNullable(thread);
        optionalThread.ifPresent(t -> {
            Long threadId = getThreadId(t);
            System.out.println("[Scheduler Thread Message] : Thread-" + threadId + " is selected to run");
            synchronized (RuntimeEnvironment.locks.get(threadId)) {
                RuntimeEnvironment.locks.get(threadId).notify();
            }
        });
    }

    /**
     * Retrieves the ID of the specified thread.
     * <p>
     * This method is used to retrieve the ID of the specified thread.
     *
     * @param thread the thread whose ID is to be retrieved.
     * @return the ID of the thread.
     */
    private Long getThreadId(Thread thread) {
        return RuntimeEnvironment.threadIdMap.get(thread.getId());
    }

    /**
     * Handles the event based on the type of the event.
     * <p>
     * This method is used to handle the event based on the type of the event.
     * It calls the appropriate handler method
     * based on the event type.
     */
    private void eventHandler() {
        RequestType request = determineEventType();
        if (request == RequestType.START_REQUEST) {
            startEventHandler();
        } else if (request == RequestType.ENTER_MONITOR_REQUEST) {
            enterMonitorRequestHandler();
        } else if (request == RequestType.EXIT_MONITOR_REQUEST) {
            exitMonitorRequestHandler();
        } else if (request == RequestType.JOIN_REQUEST) {
            joinRequestHandler();
        } else if (request == RequestType.READ_REQUEST) {
            readRequestHandler();
        } else if (request == RequestType.WRITE_REQUEST) {
            writeRequestHandler();
        } else if (request == RequestType.FINISH_REQUEST) {
            finishRequestHandler();
        } else {
            RuntimeEnvironment.threadWaitReq = null;
            pickNextThread();
        }
    }

    /**
     * Determines the type of the event.
     * <p>
     * This method is used to determine the type of the event. It checks the state of the runtime environment and returns
     * the type of the event that should be handled next.
     *
     * @return the type of the event that should be handled next.
     */
    private RequestType determineEventType() {
        if (RuntimeEnvironment.threadStartReq != null) {
            return RequestType.START_REQUEST;
        } else if (RuntimeEnvironment.threadEnterMonitorReq != null) {
            return RequestType.ENTER_MONITOR_REQUEST;
        } else if (RuntimeEnvironment.threadExitMonitorReq != null) {
            return RequestType.EXIT_MONITOR_REQUEST;
        } else if (RuntimeEnvironment.threadJoinReq != null) {
            return RequestType.JOIN_REQUEST;
        } else if (RuntimeEnvironment.writeEventReq != null) {
            return RequestType.WRITE_REQUEST;
        } else if (RuntimeEnvironment.readEventReq != null) {
            return RequestType.READ_REQUEST;
        } else if (RuntimeEnvironment.isFinished) {
            return RequestType.FINISH_REQUEST;
        } else {
            RuntimeEnvironment.threadWaitReq = null;
            return RequestType.NONE;
        }
    }

    /**
     * Handles the start event.
     * <p>
     * This method is used to handle the start event. It retrieves the thread that requested to start, sets the thread
     * wait request to null, prints a message indicating that the thread is selected to run for loading in the runtime
     * environment, and then starts the thread.
     */
    private void startEventHandler() {
        System.out.println("[Scheduler Thread Message] : Start event handler is called");
        Optional<Thread> calleeThread = Optional.ofNullable(RuntimeEnvironment.threadStartReq);
        Optional<Thread> callerThread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        if (calleeThread.isPresent() && callerThread.isPresent()) {
            searchStrategy.nextStartEvent(calleeThread.get(), callerThread.get());
            RuntimeEnvironment.threadWaitReq = null;
            startThread(calleeThread.get());
        }
    }

    /**
     * Starts the specified thread.
     * <p>
     * This method is used to start the specified thread. It sets the thread start request to null, prints a message
     * indicating that the thread is selected to run for loading in the runtime environment, and then starts the thread.
     *
     * @param thread the thread to be started.
     */
    private void startThread(Thread thread) {
        RuntimeEnvironment.threadStartReq = null;
        System.out.println(
                "[Scheduler Thread Message] : Thread-" + getThreadId(thread) +
                    " is selected to run for loading in the runtime environment"
        );
        thread.start();
    }

    /**
     * Handles the enter monitor request of a thread.
     * <p>
     * This method is used to handle the enter monitor request of a thread. It adds the thread and the monitor into the
     * monitorRequest and checks whether there is a deadlock between the threads in using the monitors or not. If there
     * is a deadlock, the execution is set to be finished. Otherwise, it picks the next thread to run.
     */
    public void enterMonitorRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Enter monitor request handler is called");
        Optional<Thread> enterMonitorThread = Optional.ofNullable(RuntimeEnvironment.threadEnterMonitorReq);
        Optional<Object> enterMonitorObject = Optional.ofNullable(RuntimeEnvironment.objectEnterMonitorReq);
        if (enterMonitorThread.isPresent() && enterMonitorObject.isPresent()) {
            RuntimeEnvironment.monitorRequest.put(enterMonitorThread.get(), enterMonitorObject.get());
            RuntimeEnvironment.threadEnterMonitorReq = null;
            RuntimeEnvironment.objectEnterMonitorReq = null;
            if (monitorsDeadlockDetection()) {
                System.out.println(
                        "[Scheduler Thread Message] : There is a deadlock between the threads in using " +
                                "the monitors"
                );
                RuntimeEnvironment.threadWaitReq = null;
                RuntimeEnvironment.deadlockHappened = true;
                RuntimeEnvironment.executionFinished = true;
            } else {
                System.out.println(
                        "[Scheduler Thread Message] : There is no deadlock between the threads in using " +
                                "the monitors"
                );
                RuntimeEnvironment.threadWaitReq = null;
                pickNextThread();
            }
        }
    }

    /**
     * Handles the exit monitor request of a thread.
     * <p>
     * This method is used to handle the exit monitor request of a thread. It retrieves the thread and the monitor that
     * requested to exit, handles the exit monitor event, sets the thread exit monitor request and the object exit monitor
     * request to null, sets the thread wait request to null, and then picks the next thread to run.
     */
    public void exitMonitorRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Exit monitor request handler is called");
        Optional<Thread> exitMonitorThread = Optional.ofNullable(RuntimeEnvironment.threadExitMonitorReq);
        Optional<Object> exitMonitorObject = Optional.ofNullable(RuntimeEnvironment.objectExitMonitorReq);
        if (exitMonitorThread.isPresent() && exitMonitorObject.isPresent()) {
            searchStrategy.nextExitMonitorEvent(exitMonitorThread.get(), exitMonitorObject.get());
            RuntimeEnvironment.threadExitMonitorReq = null;
            RuntimeEnvironment.objectExitMonitorReq = null;
            RuntimeEnvironment.threadWaitReq = null;
            pickNextThread();
        }
    }

    /**
     * Handles the join request of a thread.
     * <p>
     * This method is used to handle the join request of a thread. It retrieves the thread that requested to join and the
     * thread that is joined, handles the join request, sets the thread join request and the thread join response to null,
     * sets the thread wait request to null, and then picks the next thread to run.
     */
    public void joinRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Join request handler is called");
        Optional<Thread> joinRequestThread = Optional.ofNullable(RuntimeEnvironment.threadJoinReq);
        Optional<Thread> joinResponseThread = Optional.ofNullable(RuntimeEnvironment.threadJoinRes);
        if (joinRequestThread.isPresent() && joinResponseThread.isPresent()) {
            RuntimeEnvironment.joinRequest.put(joinRequestThread.get(), joinResponseThread.get());
            RuntimeEnvironment.threadJoinReq = null;
            RuntimeEnvironment.threadJoinRes = null;
            RuntimeEnvironment.threadWaitReq = null;
            pickNextThread();
        }
    }

    /**
     * Handles the read request of a thread.
     * <p>
     * This method is used to handle the read request event of a thread. It retrieves the read event that requested by
     * the thread, handles the read request, sets the read event request to null, sets the thread wait request to null,
     * and then notifies the thread.
     */
    public void readRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Read request handler is called");
        Optional<ReadEvent> readRequestEvent = Optional.ofNullable(RuntimeEnvironment.readEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        if (readRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextReadEvent(readRequestEvent.get());
            RuntimeEnvironment.readEventReq = null;
            RuntimeEnvironment.threadWaitReq = null;
            notifyThread(thread.get());
        }
    }

    /**
     * Handles the write request of a thread.
     * <p>
     * This method is used to handle the write request of a thread. It retrieves the write event that requested by
     * the thread, handles the write request, sets the write event request to null, sets the thread wait request to null,
     * and then notifies the thread.
     */
    public void writeRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Write request handler is called");
        Optional<WriteEvent> writeRequestEvent = Optional.ofNullable(RuntimeEnvironment.writeEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        if (writeRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextWriteEvent(writeRequestEvent.get());
            RuntimeEnvironment.writeEventReq = null;
            RuntimeEnvironment.threadWaitReq = null;
            notifyThread(thread.get());
        }
    }

    public void finishRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Finish request handler is called");
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        if (thread.isPresent()) {
            searchStrategy.nextFinishEvent(thread.get());
            RuntimeEnvironment.threadWaitReq = null;
            RuntimeEnvironment.isFinished = false;
            pickNextThread();
        }
    }

    public void wasLastExecution() {
        if (searchStrategy.done()) {
            RuntimeEnvironment.allExecutionsFinished = true;
        }
    }

    /**
     * This method is used to detect potential deadlocks in the threads that are waiting to enter a monitor.
     * It computes the transitive closure of the (@monitorRequest \cup @monitorList) relation and checks whether the
     * relation is irreflexive or not.
     * If the relation is irreflexive, there is no deadlock and the method returns false.
     * Otherwise, there is a deadlock and the method returns true.
     *
     * @return {@code true} if there is a deadlock, {@code false} otherwise.
     */
    public boolean monitorsDeadlockDetection() {
        System.out.println("[Scheduler Thread Message] : The deadlock detection phase is started");
        Optional<Map<Thread, Thread>> threadClosure = computeTransitiveClosure();
        if (threadClosure.isPresent()) {
            return checkIrreflexivity(threadClosure.get());
        } else {
            System.out.println("[Scheduler Thread Message] : There is no need to check the deadlock");
            return false;
        }
    }

    private Optional<Map<Thread, Thread>> computeTransitiveClosure() {
        if (RuntimeEnvironment.monitorList.isEmpty()) {
            return Optional.empty();
        } else {
            System.out.println("[Scheduler Thread Message] : The deadlock detection phase is started");
            Map<Thread, Thread> threadClosure = new HashMap<>();
            // Compute the primitive closure of the (@monitorRequest \cup @monitorList) relation.
            for (Map.Entry<Thread, Object> entry : RuntimeEnvironment.monitorRequest.entrySet()) {
                for (Map.Entry<Object, Thread> entry2 : RuntimeEnvironment.monitorList.entrySet()) {
                    if (entry.getValue().equals(entry2.getKey())) {
                        threadClosure.put(entry.getKey(), entry2.getValue());
                    }
                }
            }
            // Compute the complete transitive closure of the (@monitorRequest \cup @monitorList) relation.
            boolean addedNewPairs = true;
            while (addedNewPairs){
                addedNewPairs = false;
                for (Map.Entry<Thread, Thread> entry : threadClosure.entrySet()) {
                    for (Map.Entry<Thread, Thread> entry2 : threadClosure.entrySet()) {
                        if (entry.getValue().equals(entry2.getKey()) &&
                                threadClosure.entrySet().stream().noneMatch(e ->
                                        e.getKey().equals(entry.getKey()) && e.getValue().equals(entry2.getValue()))) {
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
}