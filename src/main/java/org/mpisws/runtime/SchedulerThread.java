package org.mpisws.runtime;

import java.util.Optional;

import org.mpisws.checker.SearchStrategy;
import org.mpisws.checker.StrategyType;
import org.mpisws.checker.strategy.RandomStrategy;
import org.mpisws.checker.strategy.ReplayStrategy;
import org.mpisws.checker.strategy.TrustStrategy;
import org.mpisws.symbolic.SymbolicOperation;
import programStructure.ReadEvent;
import programStructure.ReceiveEvent;
import programStructure.SendEvent;
import programStructure.WriteEvent;

/**
 * The SchedulerThread class extends the Thread class and is responsible for managing the execution of threads in a
 * multithreaded program. It uses a {@link SearchStrategy} to determine the next thread to be executed based on a selected
 * strategy type. The SchedulerThread class handles various types of requests including start, enter monitor,
 * exit monitor, synchronized methods, join, read, write, finish, thread park, thread unpark, and symbolic arithmetic
 * requests. It also includes functionality for deadlock detection among threads waiting to enter a monitor.
 * The SchedulerThread class is designed to control the flow of a program's execution and ensure sequential execution
 * of operations.
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
     * This method is used to select the next thread to be executed. It calls the {@link SearchStrategy#pickNextThread()}
     * method to select the next thread to be executed. Then, it notifies the selected thread to continue its execution.
     * <br>
     * Finding the next thread to be executed is based on the selected strategy.
     * </p>
     */
    private void waitEventHandler() {
        Thread thread = searchStrategy.pickNextThread();
        notifyThread(thread);
    }

    /**
     * Notifies the specified thread to continue its execution.
     * <p>
     * This method is used to notify the specified thread to continue its execution. If the thread is null, it indicates
     * that the execution is finished. In this case, the method sets the {@link RuntimeEnvironment#executionFinished}
     * flag to true. Otherwise, The method retrieves the thread's ID, prints a message indicating that the thread is
     * permitted to run, and then notifies the thread.
     * </p>
     *
     * @param thread the thread to be notified.
     */
    private void notifyThread(Thread thread) {
        Optional<Thread> optionalThread = Optional.ofNullable(thread);
        if (optionalThread.isPresent()) {
            Long threadId = getThreadId(optionalThread.get());
            System.out.println("[Scheduler Thread Message] : Thread-" + threadId + " is permitted to run");
            synchronized (RuntimeEnvironment.locks.get(threadId)) {
                RuntimeEnvironment.locks.get(threadId).notify();
            }
        } else {
            RuntimeEnvironment.executionFinished = true;
        }
    }

    /**
     * Retrieves the ID of the specified thread.
     * <br>
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
     * </p>
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
            // TODO() : Add send, recv handlres
            case SEND_REQUEST:
                sendRequestHandler();
                break;
            case RECV_REQUEST:
                receiveRequestHandler();
                break;
            case FINISH_REQUEST:
                finishRequestHandler();
                break;
            case SYMB_ARTH_REQUEST:
                symbolicArithmeticRequestHandler();
                break;
            case PARK_REQUEST:
                parkRequestHandler();
                break;
            case UNPARK_REQUEST:
                unparkRequestHandler();
                break;
            default:
                RuntimeEnvironment.threadWaitReq = null;
                waitEventHandler();
                break;
        }
    }

    /**
     * Determines the type of the event.
     * <p>
     * This method is used to determine the type of the event. It checks the state of the runtime environment and returns
     * the type of the event that should be handled next.
     * </p>
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
        } else if (RuntimeEnvironment.sendEventReq != null) {
            return RequestType.SEND_REQUEST;
        } else if (RuntimeEnvironment.receiveEventReq != null) {
            return RequestType.RECV_REQUEST;
        } else if (RuntimeEnvironment.isFinished) {
            return RequestType.FINISH_REQUEST;
        } else if (RuntimeEnvironment.symbolicOperation != null) {
            return RequestType.SYMB_ARTH_REQUEST;
        } else if (RuntimeEnvironment.unparkerThread != null) {
            return RequestType.UNPARK_REQUEST;
        } else if (RuntimeEnvironment.threadToPark != null) {
            return RequestType.PARK_REQUEST;
        } else {
            return RequestType.WAIT_REQUEST;
        }
    }

    /**
     * Handles the start event.
     * <p>
     * This method is used to handle the start event. It retrieves the thread that requested to start, sets the thread
     * wait request to null, if both the callee thread and the caller thread are present, it calls the next start event
     * method of the search strategy, and then starts the callee thread.
     * </p>
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
     * <p>
     * This method is used to start the specified thread. It prints a message indicating that the thread is permitted to
     * run for loading in the runtime environment, and then starts the thread.
     * </p>
     *
     * @param thread the thread to be started.
     */
    private void startThread(Thread thread) {
        System.out.println(
                "[Scheduler Thread Message] : Thread-" + getThreadId(thread) +
                        " is permitted to run for loading in the runtime environment"
        );
        thread.start();
    }

    /**
     * Handles the enter monitor request of a thread.
     * <p>
     * This method is used to handle the enter monitor request of a thread. It adds the thread and the monitor into the
     * monitorRequest and calls the next enter monitor request method of the search strategy. If the next thread to run is
     * null, it calls the next deadlock event method of the search strategy. Otherwise, it notifies the thread to run.
     * </p>
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
     * <p>
     * This method is used to handle the exit monitor request of a thread. It retrieves the thread and the monitor that
     * requested to exit, handles the exit monitor event by calling the next exit monitor event method of the search
     * strategy, sets the thread exit monitor request and the object exit monitor request to null, sets the thread wait
     * request to null, and finally calls the wait event handler, which selects the next thread to run.
     * </p>
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
     * <p>
     * This method is used to handle the join request of a thread. It retrieves the thread that requested to join and the
     * thread that is joined, sets the thread join request and the thread join response to null,
     * sets the thread wait request to null, and finally handles the join request by calling the next join request method
     * of the search strategy, and then notifies the thread to run.
     * </p>
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
     * <p>
     * This method is used to handle the read request event of a thread. It retrieves the read event that requested by
     * the thread, handles the read request, sets the read event request to null, sets the thread wait request to null,
     * and then notifies the thread.
     * </p>
     */
    public void readRequestHandler() {
        System.out.println("[Scheduler Thread Message] : read request handler is called");
        Optional<ReadEvent> readRequestEvent = Optional.ofNullable(RuntimeEnvironment.readEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.readEventReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (readRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextReadEvent(readRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    public void receiveRequestHandler() {
        System.out.println("[Scheduler Thread Message] : receive request handler is called");
        Optional<ReceiveEvent> receiveRequestEvent = Optional.ofNullable(RuntimeEnvironment.receiveEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.receiveEventReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (receiveRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextReceiveEvent(receiveRequestEvent.get());
            notifyThread(thread.get());
        }

    }

    /**
     * Handles the write request of a thread.
     * <p>
     * This method is used to handle the write request of a thread. It retrieves the write event that requested by
     * the thread, handles the write request, sets the write event request to null, sets the thread wait request to null,
     * and then notifies the thread.
     * </p>
     */
    public void writeRequestHandler() {
        System.out.println("[Scheduler Thread Message] : write request handler is called");
        Optional<WriteEvent> writeRequestEvent = Optional.ofNullable(RuntimeEnvironment.writeEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.writeEventReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (writeRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextWriteEvent(writeRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    public void sendRequestHandler() {
        System.out.println("[Scheduler Thread Message] : send request handler is called");
        Optional<SendEvent> sendRequestEvent = Optional.ofNullable(RuntimeEnvironment.sendEventReq);
        Optional<Thread> thread = Optional.ofNullable(RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.sendEventReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (sendRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextSendEvent(sendRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    /**
     * Handles the finish request of a thread.
     * <p>
     * This method is used to handle the finish request of a thread. It retrieves the thread that requested to finish,
     * sets the thread wait request to null, handles the finish request by calling the next finish request method of the
     * search strategy, and then notifies the returned thread to run.
     * </p>
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

    /**
     * Handles the unpark request of a thread.
     * <p>
     * This method is used to handle the unpark request of a thread. It retrieves the unparker thread and the unparkee
     * thread, sets the unparker thread and the unparkee thread to null, sets the thread wait request to null, and then
     * calls the next unpark request method of the search strategy. Finally, it calls the wait event handler to select
     * the next thread to run.
     * </p>
     */
    public void unparkRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Unpark request handler is called");
        Optional<Thread> unparkerThread = Optional.ofNullable(RuntimeEnvironment.unparkerThread);
        Optional<Thread> unparkeeThread = Optional.ofNullable(RuntimeEnvironment.unparkeeThread);
        RuntimeEnvironment.unparkerThread = null;
        RuntimeEnvironment.unparkeeThread = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (unparkerThread.isPresent() && unparkeeThread.isPresent()) {
            searchStrategy.nextUnparkRequest(unparkerThread.get(), unparkeeThread.get());
            waitEventHandler();
        }
    }

    /**
     * Handles the park request of a thread.
     * <p>
     * This method is used to handle the park request of a thread. It retrieves the thread that requested to park, sets
     * the thread to park to null, sets the thread wait request to null, and then calls the next park request method of
     * the search strategy. Finally, it calls the wait event handler to select the next thread to run.
     * </p>
     */
    public void parkRequestHandler() {
        System.out.println("[Scheduler Thread Message] : Park request handler is called");
        Optional<Thread> threadToPark = Optional.ofNullable(RuntimeEnvironment.threadToPark);
        RuntimeEnvironment.threadToPark = null;
        RuntimeEnvironment.threadWaitReq = null;
        if (threadToPark.isPresent()) {
            searchStrategy.nextParkRequest(threadToPark.get());
            waitEventHandler();
        }
    }

    /**
     * Handles the symbolic arithmetic request of a thread.
     * <p>
     * This method is used to handle the symbolic arithmetic request of a thread. It retrieves the thread that requested
     * the symbolic arithmetic operation, sets the thread wait request to null, and then calls the next symbolic operation
     * request method of the search strategy. Finally, it notifies the thread to run.
     * </p>
     */
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