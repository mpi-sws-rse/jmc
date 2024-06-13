package org.mpisws.runtime;

import java.util.Optional;

import org.mpisws.checker.SearchStrategy;
import org.mpisws.checker.StrategyType;
import org.mpisws.checker.strategy.RandomStrategy;
import org.mpisws.checker.strategy.ReplayStrategy;
import org.mpisws.checker.strategy.TrustStrategy;
import org.mpisws.symbolic.SymbolicOperation;
import programStructure.ReadEvent;
import programStructure.WriteEvent;

/**
 * The SchedulerThread class extends the Thread class and is responsible for managing the execution of threads in a
 * multithreaded program. It uses a SearchStrategy to determine the next thread to be executed based on a selected
 * strategy type. The SchedulerThread class handles various types of requests including start, enter monitor,
 * exit monitor, join, read, write, and finish requests. It also includes functionality for deadlock detection among
 * threads waiting to enter a monitor. The SchedulerThread class is designed to control the flow of a program's execution
 * and ensure sequential execution of operations.
 */
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
     * If the selected strategy type is {@link StrategyType#RANDOM}, the method initializes the {@link RandomStrategy}.
     * Otherwise, the method throws an {@link IllegalArgumentException}.
     *
     * @throws IllegalArgumentException if the selected strategy type is not supported.
     */
    private void createSearchStrategy() {
        StrategyType strategyType = RuntimeEnvironment.strategyType;
        if (strategyType == StrategyType.RANDOM) {
            searchStrategy = new RandomStrategy();
        } else if (strategyType == StrategyType.TRUST) {
            searchStrategy = new TrustStrategy();
        } else if (strategyType == StrategyType.REPLAY) {
            searchStrategy = new ReplayStrategy();
        } else {
            // TODO() : Fix it
            System.out.println("[Scheduler Thread Message] : Unsupported strategy type: " + strategyType);
            //throw new IllegalArgumentException("Unsupported strategy type: " + strategyType);
            System.exit(1);
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
            }
            System.out.println("[Scheduler Thread Message] : All threads are in waiting state");
            waitForThreadStateChange();
            eventHandler();
        }
        printEndMessage();
        saveExecutionState();
        wasLastExecution();
        notifyMainThread();
    }

    /**
     * Saves the execution state.
     * <p>
     * This method is used to save the execution state. It calls the {@link SearchStrategy#saveExecutionState()} method
     * to save the execution state.
     */
    private void saveExecutionState() {
        searchStrategy.saveExecutionState();
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
        // TODO() : Create FailureEvent and replace the following line with the nextFailureEvent method
        searchStrategy.nextFailureEvent(RuntimeEnvironment.threadWaitReq);
        searchStrategy.printExecutionTrace();
        searchStrategy.saveBuggyExecutionTrace();
        System.exit(0);
    }

    /**
     * Waits for the state of the only other running thread to change to the WAIT state.
     *
     * <p>This method is used to wait until the state of the only other running thread
     * changes to the WAIT state.</p>
     */
    private void waitForThreadStateChange() {
        synchronized (RuntimeEnvironment.locks.get(getThreadId(RuntimeEnvironment.threadWaitReq))) {
            System.out.println("[Scheduler Thread Message] : Scheduling phase begins");
        }
    }

    /**
     * Prints the end message.
     *
     * <p>This method is used to print the end message.</p>
     */
    private void printEndMessage() {
        System.out.println("******************************************************************************************");
        System.out.println("[*** The SchedulerThread requested to FINISH***]");
        System.out.println("******************************************************************************************");
    }

    /**
     * Notifies the main thread to continue the execution to finish the current execution iteration.
     * <p>
     * This method is used to notify the main thread to continue the execution to finish the current execution iteration.
     */
    private void notifyMainThread() {
        if (RuntimeEnvironment.deadlockHappened) {
            searchStrategy.printExecutionTrace();
            searchStrategy.saveBuggyExecutionTrace();
            System.exit(0);
        }
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
    private void waitEventHandler() {
        Thread thread = searchStrategy.pickNextThread();
        notifyThread(thread);
    }

    /**
     * Notifies the specified thread to continue its execution.
     *
     * <p>This method is used to notify the specified thread to continue its execution. If the thread is null, it does
     * nothing.
     * <br>
     * The method retrieves the thread's ID, prints a message indicating that the thread is selected to run, and then
     * notifies the thread.
     *
     * @param thread the thread to be notified.
     */
    private void notifyThread(Thread thread) {
        Optional<Thread> optionalThread = Optional.ofNullable(thread);
        if (optionalThread.isPresent()) {
            Long threadId = getThreadId(optionalThread.get());
            System.out.println("[Scheduler Thread Message] : Thread-" + threadId + " is selected to run");
            synchronized (RuntimeEnvironment.locks.get(threadId)) {
                RuntimeEnvironment.locks.get(threadId).notify();
            }
        } else {
            RuntimeEnvironment.executionFinished = true;
        }
    }

    /**
     * Retrieves the ID of the specified thread.
     *
     * <p>This method is used to retrieve the ID of the specified thread.
     *
     * @param thread the thread whose ID is to be retrieved.
     * @return the ID of the thread.
     */
    private Long getThreadId(Thread thread) {
        return RuntimeEnvironment.threadIdMap.get(thread.getId());
    }

    /**
     * Handles the event based on the type of the event.
     *
     * <p>This method is used to handle the event based on the type of the event.
     * It calls the appropriate handler method
     * based on the event type.
     */
    private void eventHandler() {
        RequestType request = determineEventType();
        switch (request) {
            case START_REQUEST:
                startEventHandler();
                break;
            case ENTER_MONITOR_REQUEST:
                enterMonitorRequestHandler();
                break;
            case EXIT_MONITOR_REQUEST:
                exitMonitorRequestHandler();
                break;
            case JOIN_REQUEST:
                joinRequestHandler();
                break;
            case READ_REQUEST:
                readRequestHandler();
                break;
            case WRITE_REQUEST:
                writeRequestHandler();
                break;
            case FINISH_REQUEST:
                finishRequestHandler();
                break;
            case SYMB_ARTH_REQUEST:
                symbolicArithmeticRequestHandler();
                break;
            default:
                RuntimeEnvironment.threadWaitReq = null;
                waitEventHandler();
                break;
        }
    }

    /**
     * Determines the type of the event.
     *
     * <p>This method is used to determine the type of the event. It checks the state of the runtime environment and returns
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
        } else if (RuntimeEnvironment.symbolicOperation != null) {
            return RequestType.SYMB_ARTH_REQUEST;
        } else {
            return RequestType.WAIT_REQUEST;
        }
    }

    /**
     * Handles the start event.
     *
     * <p>This method is used to handle the start event. It retrieves the thread that requested to start, sets the thread
     * wait request to null, prints a message indicating that the thread is selected to run for loading in the runtime
     * environment, and then starts the thread.
     */
    private void startEventHandler() {
        System.out.println("[Scheduler Thread Message] : Start event handler is called");
        Optional<Thread> calleeThread = Optional.ofNullable(RuntimeEnvironment.threadStartReq);
        Optional<Thread> callerThread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.threadWaitReq = null;
        RuntimeEnvironment.threadStartReq = null;
        if (calleeThread.isPresent() && callerThread.isPresent()) {
            searchStrategy.nextStartEvent(calleeThread.get(), callerThread.get());
            startThread(calleeThread.get());
        }
    }

    /**
     * Starts the specified thread.
     *
     * <p>This method is used to start the specified thread. It sets the thread start request to null, prints a message
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
     *
     * <p>This method is used to handle the enter monitor request of a thread. It adds the thread and the monitor into the
     * monitorRequest and checks whether there is a deadlock between the threads in using the monitors or not. If there
     * is a deadlock, the execution is set to be finished. Otherwise, it picks the next thread to run.
     */
    public void enterMonitorRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Enter monitor request handler is called");
        Optional<Thread> enterMonitorThread = Optional.ofNullable(RuntimeEnvironment.threadEnterMonitorReq);
        Optional<Object> enterMonitorObject = Optional.ofNullable(RuntimeEnvironment.objectEnterMonitorReq);
        RuntimeEnvironment.threadEnterMonitorReq = null;
        RuntimeEnvironment.objectEnterMonitorReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (enterMonitorThread.isPresent() && enterMonitorObject.isPresent()) {
            Optional<Thread> thread = Optional.ofNullable(
                    searchStrategy.nextEnterMonitorRequest(
                            enterMonitorThread.get(), enterMonitorObject.get()
                    )
            );
            if (thread.isPresent()) {
                notifyThread(thread.get());
            } else {
                searchStrategy.nextDeadlockEvent(enterMonitorThread.get());
            }
        }
    }

    /**
     * Handles the exit monitor request of a thread.
     *
     * <p>This method is used to handle the exit monitor request of a thread. It retrieves the thread and the monitor that
     * requested to exit, handles the exit monitor event, sets the thread exit monitor request and the object exit monitor
     * request to null, sets the thread wait request to null, and then picks the next thread to run.
     */
    public void exitMonitorRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Exit monitor request handler is called");
        Optional<Thread> exitMonitorThread = Optional.ofNullable(RuntimeEnvironment.threadExitMonitorReq);
        Optional<Object> exitMonitorObject = Optional.ofNullable(RuntimeEnvironment.objectExitMonitorReq);
        RuntimeEnvironment.threadExitMonitorReq = null;
        RuntimeEnvironment.objectExitMonitorReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (exitMonitorThread.isPresent() && exitMonitorObject.isPresent()) {
            searchStrategy.nextExitMonitorEvent(exitMonitorThread.get(), exitMonitorObject.get());
            waitEventHandler();
        }
    }

    /**
     * Handles the join request of a thread.
     *
     * <p>This method is used to handle the join request of a thread. It retrieves the thread that requested to join and the
     * thread that is joined, handles the join request, sets the thread join request and the thread join response to null,
     * sets the thread wait request to null, and then picks the next thread to run.
     */
    public void joinRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Join request handler is called");
        Optional<Thread> joinRequestThread = Optional.ofNullable(RuntimeEnvironment.threadJoinReq);
        Optional<Thread> joinResponseThread = Optional.ofNullable(RuntimeEnvironment.threadJoinRes);
        RuntimeEnvironment.threadJoinReq = null;
        RuntimeEnvironment.threadJoinRes = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (joinRequestThread.isPresent() && joinResponseThread.isPresent()) {
            Optional<Thread> thread = Optional.ofNullable(
                    searchStrategy.nextJoinRequest(
                            joinRequestThread.get(), joinResponseThread.get()
                    )
            );
            thread.ifPresent(this::notifyThread);
        }
    }

    /**
     * Handles the read request of a thread.
     *
     * <p>This method is used to handle the read request event of a thread. It retrieves the read event that requested by
     * the thread, handles the read request, sets the read event request to null, sets the thread wait request to null,
     * and then notifies the thread.
     */
    public void readRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Read request handler is called");
        Optional<ReadEvent> readRequestEvent = Optional.ofNullable(RuntimeEnvironment.readEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.readEventReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (readRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextReadEvent(readRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    /**
     * Handles the write request of a thread.
     *
     * <p>This method is used to handle the write request of a thread. It retrieves the write event that requested by
     * the thread, handles the write request, sets the write event request to null, sets the thread wait request to null,
     * and then notifies the thread.
     */
    public void writeRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Write request handler is called");
        Optional<WriteEvent> writeRequestEvent = Optional.ofNullable(RuntimeEnvironment.writeEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.writeEventReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (writeRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextWriteEvent(writeRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    /**
     * Handles the finish request of a thread.
     *
     * <p>This method is used to handle the finish request of a thread. It retrieves the thread that requested to finish,
     * handles the finish request, sets the thread wait request to null, and then picks the next thread to run.
     */
    public void finishRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Finish request handler is called");
        Optional<Thread> finishedThread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.threadWaitReq = null;
        RuntimeEnvironment.isFinished = false;
        if (finishedThread.isPresent()) {
            Thread thread = searchStrategy.nextFinishRequest(finishedThread.get());
            notifyThread(thread);
        }
    }

    public void symbolicArithmeticRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Symbolic arithmetic request handler is called");
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        Optional<SymbolicOperation> symbolicOperation = Optional.ofNullable(RuntimeEnvironment.symbolicOperation);
        RuntimeEnvironment.threadWaitReq = null;
        RuntimeEnvironment.symbolicOperation = null;
        if (thread.isPresent() && symbolicOperation.isPresent()) {
            searchStrategy.nextSymbolicOperationRequest(thread.get(), symbolicOperation.get());
            notifyThread(thread.get());
        }
    }

    /**
     * Checks whether the last execution is finished or not.
     *
     * <p>This method is used to check whether the last execution is finished or not. If the search strategy is done, it
     * sets the {@link RuntimeEnvironment#allExecutionsFinished} flag to true.
     */
    public void wasLastExecution() {
        if (searchStrategy.done()) {
            RuntimeEnvironment.allExecutionsFinished = true;
        }
    }
}