package org.mpisws.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.checker.SearchStrategy;
import org.mpisws.checker.StrategyType;
import org.mpisws.checker.strategy.MustStrategy;
import org.mpisws.checker.strategy.OptTrustStrategy;
import org.mpisws.checker.strategy.RandomStrategy;
import org.mpisws.checker.strategy.ReplayStrategy;
import org.mpisws.checker.strategy.TrustStrategy;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCStarterThread;

import programStructure.AssumeBlockedEvent;
import programStructure.ConAssumeEvent;
import programStructure.MainStartEvent;
import programStructure.ReadEvent;
import programStructure.ReadExEvent;
import programStructure.ReceiveEvent;
import programStructure.SendEvent;
import programStructure.WriteEvent;
import programStructure.WriteExEvent;

import java.util.Optional;
import java.util.concurrent.FutureTask;

/**
 * The SchedulerThread class extends the Thread class and is responsible for managing the execution
 * of threads in a multithreaded program. It uses a {@link SearchStrategy} to determine the next
 * thread to be executed based on a selected strategy type. The SchedulerThread class handles
 * various types of requests including start, enter monitor, exit monitor, synchronized methods,
 * join, read, write, finish, thread park, thread unpark, and symbolic arithmetic requests. It also
 * includes functionality for deadlock detection among threads waiting to enter a monitor. The
 * SchedulerThread class is designed to control the flow of a program's execution and ensure
 * sequential execution of operations.
 */
public class SchedulerThread extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(SchedulerThread.class);

    /**
     * The @property {@link SearchStrategy} is used to select the next thread to be executed based
     * on the selected strategy.
     */
    private SearchStrategy searchStrategy;

    /**
     * The following constructor initializes the {@link SearchStrategy} based on the selected
     * strategy type.
     */
    public SchedulerThread() {
        createSearchStrategy();
    }

    /**
     * Initializes the {@link SearchStrategy} based on the selected strategy type.
     *
     * <p>The following method is used to initialize the {@link SearchStrategy} based on the
     * selected strategy type. If the selected strategy type is {@link StrategyType#RANDOM}, the
     * method initializes the {@link RandomStrategy}. Otherwise, the method throws an {@link
     * IllegalArgumentException}.
     *
     * @throws IllegalArgumentException if the selected strategy type is not supported.
     */
    private void createSearchStrategy() {
        StrategyType strategyType = JmcRuntime.strategyType;
        if (strategyType == StrategyType.RANDOM) {
            searchStrategy = new RandomStrategy();
        } else if (strategyType == StrategyType.TRUST) {
            searchStrategy = new TrustStrategy();
        } else if (strategyType == StrategyType.REPLAY) {
            searchStrategy = new ReplayStrategy();
        } else if (strategyType == StrategyType.MUST) {
            searchStrategy = new MustStrategy();
        } else if (strategyType == StrategyType.OPT_TRUST) {
            searchStrategy = new OptTrustStrategy();
        } else {
            // TODO() : Fix it
            LOGGER.error("Unsupported strategy type: {}", strategyType);
            // throw new IllegalArgumentException("Unsupported strategy type: " + strategyType);
            System.exit(1);
        }
    }

    /**
     * Executes the scheduler thread.
     *
     * <p>This method is used to execute the scheduler thread. It waits until the other thread
     * (which is the main thread) waits, prints a message indicating that the flow of the program is
     * controlled by the SchedulerThread, and then handles the events until the execution is
     * finished.
     */
    @Override
    public void run() {
        waitForMainThread();
        printStartMessage();
        while (!JmcRuntime.executionFinished) {
            waitForOtherThread();
            if (checkAssertFlag()) {
                handleAssertFail();
            }
            LOGGER.debug("All threads are in waiting state");
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
     *
     * <p>This method is used to save the execution state. It calls the {@link
     * SearchStrategy#saveExecutionState()} method to save the execution state.
     */
    private void saveExecutionState() {
        searchStrategy.saveExecutionState();
    }

    /**
     * Waits for the main thread.
     *
     * <p>This method is used to wait until the other thread (which is the main thread) waits.
     */
    private void waitForMainThread() {
        JmcRuntime.getPermission(JmcRuntime.threadManager.getThread(1L));
    }

    /**
     * Prints the start message.
     *
     * <p>This method is used to print the start message.
     */
    private void printStartMessage() {
        LOGGER.debug(
                "[*** From this point on, the flow of the program is "
                        + "controlled by the SchedulerThread ***]");
    }

    /**
     * Waits for the other thread.
     *
     * <p>This method is used to wait until the only other running thread requests to wait.
     */
    private void waitForOtherThread() {
        while (true) {
            synchronized (JmcRuntime.threadWaitReqLock) {
                if (JmcRuntime.threadWaitReq != null) {
                    break;
                }
                Thread.yield();
            }
        }
    }

    /**
     * Checks the assert flag.
     *
     * <p>This method is used to check whether the wait request is due to an assert violation or
     * not.
     *
     * @return {@code true} if the wait request is due to an assert violation, {@code false}
     *     otherwise.
     */
    private boolean checkAssertFlag() {
        return JmcRuntime.assertFlag;
    }

    /**
     * Handles the assert fail.
     *
     * <p>This method is used to handle the assert fail. It sets the execution to be finished,
     * prints a message indicating that the SchedulerThread requested to finish, and then prints the
     * executed events.
     */
    private void handleAssertFail() {
        if (!JmcRuntime.isExecutionBlocked) {
            JmcRuntime.executionFinished = true;
            // TODO() : Create FailureEvent and replace the following line with the nextFailureEvent
            // method
            searchStrategy.nextFailureEvent(JmcRuntime.threadWaitReq);
            searchStrategy.printExecutionTrace();
            LOGGER.error("[*** Assertion Fail ***]");
            LOGGER.error(
                    "[*** Number of execution iteration : {} ***]",
                    JmcRuntime.numOfExecutions);
            LOGGER.debug("[*** The SchedulerThread requested to FINISH***]");
            JmcRuntime.printFinalMessage();
            searchStrategy.saveBuggyExecutionTrace();
            System.exit(0);
        }
    }

    private void printResourceUsage() {
        LOGGER.debug("[*** Resource Usage ***]");
        LOGGER.debug(
                "[*** Number of execution iteration : {} ***]", JmcRuntime.numOfExecutions);
        LOGGER.debug(
                "[*** Number of threads created : {} ***]",
                JmcRuntime.threadManager.findThreadsWithStatus(ThreadManager.ThreadState.CREATED)
                        .size());
        LOGGER.debug(
                "[*** Memory used : {} MB ***]",
                JmcRuntime.currentMemoryUsageInMegaBytes());
        long timeInNano = JmcRuntime.elapsedTimeInNanoSeconds();
        LOGGER.debug("[*** Time taken to execute the program : {} ns", timeInNano);
        double timeInSeconds = (double) timeInNano / 1_000_000_000;
        double timeInMinutes = timeInSeconds / 60;
        timeInSeconds = timeInSeconds % 60;
        LOGGER.debug(
                "[*** Time taken to execute the program : {} min: {} sec",
                timeInMinutes,
                timeInSeconds);
        //        System.out.println("[*** Number of threads started : " +
        // RuntimeEnvironment.startedThreadList.size() + " ***]");
        //        System.out.println("[*** Number of threads finished : " +
        // RuntimeEnvironment.finishedThreadList.size() + " ***]");
        //        System.out.println("[*** Number of threads waiting : " +
        // RuntimeEnvironment.waitingThreadList.size() + " ***]");
        //        System.out.println("[*** Number of threads in monitor : " +
        // RuntimeEnvironment.monitorThreadList.size() + " ***]");
        //        System.out.println("[*** Number of threads in deadlock : " +
        // RuntimeEnvironment.deadlockThreadList.size() + " ***]");
    }

    /**
     * Waits for the state of the only other running thread to change to the WAIT state.
     *
     * <p>This method is used to wait until the state of the only other running thread changes to
     * the WAIT state.
     */
    private void waitForThreadStateChange() {
        synchronized (JmcRuntime.locks.get(getThreadId(JmcRuntime.threadWaitReq))) {
            LOGGER.debug("Scheduling phase begins");
        }
    }

    /**
     * Prints the end message.
     *
     * <p>This method is used to print the end message.
     */
    private void printEndMessage() {
        // LOGGER.debug("The last execution trace is :");
        // searchStrategy.printExecutionTrace();
        LOGGER.debug("[*** The SchedulerThread requested to FINISH***]");
        // printResourceUsage();
    }

    /**
     * Notifies the main thread to continue the execution to finish the current execution iteration.
     *
     * <p>This method is used to notify the main thread to continue the execution to finish the
     * current execution iteration.
     */
    private void notifyMainThread() {
        if (JmcRuntime.deadlockHappened) {
            searchStrategy.printExecutionTrace();
            printResourceUsage();
            searchStrategy.saveBuggyExecutionTrace();
            System.exit(0);
        }
        synchronized (JmcRuntime.locks.get((long) 1)) {
            JmcRuntime.locks.get((long) 1).notify();
        }
    }

    /**
     * Selects the next thread to be executed.
     *
     * <p>This method is used to select the next thread to be executed. It calls the {@link
     * SearchStrategy#pickNextThread()} method to select the next thread to be executed. Then, it
     * notifies the selected thread to continue its execution. <br>
     * Finding the next thread to be executed is based on the selected strategy.
     */
    private void waitEventHandler() {
        Thread thread = searchStrategy.pickNextThread();
        notifyThread(thread);
    }

    /**
     * Notifies the specified thread to continue its execution.
     *
     * <p>This method is used to notify the specified thread to continue its execution. If the
     * thread is null, it indicates that the execution is finished. In this case, the method sets
     * the {@link JmcRuntime#executionFinished} flag to true. Otherwise, The method
     * retrieves the thread's ID, prints a message indicating that the thread is permitted to run,
     * and then notifies the thread.
     *
     * @param thread the thread to be notified.
     */
    private void notifyThread(Thread thread) {
        Optional<Thread> optionalThread = Optional.ofNullable(thread);
        if (optionalThread.isPresent()) {
            Long threadId = getThreadId(optionalThread.get());
            LOGGER.debug("Thread-{} is permitted to run", threadId);
            synchronized (JmcRuntime.locks.get(threadId)) {
                JmcRuntime.locks.get(threadId).notify();
            }
        } else {
            JmcRuntime.executionFinished = true;
        }
    }

    /**
     * Retrieves the ID of the specified thread. <br>
     * This method is used to retrieve the ID of the specified thread.
     *
     * @param thread the thread whose ID is to be retrieved.
     * @return the ID of the thread.
     */
    private Long getThreadId(Thread thread) {
        return JmcRuntime.threadManager.getRevId(thread.getId());
    }

    /**
     * Handles the event based on the type of the event.
     *
     * <p>This method is used to handle the event based on the type of the event. It calls the
     * appropriate handler method based on the event type.
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
            case SEND_REQUEST:
                sendRequestHandler();
                break;
            case RECV_REQUEST:
                receiveRequestHandler();
                break;
            case RECV_BLOCKING_REQUEST:
                blockingReceiveRequestHandler();
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
            case MAIN_START_REQUEST:
                mainStartEventHandler();
                break;
            case GET_FUTURE_REQUEST:
                getFutureRequestHandler();
                break;
            case TAKE_WORK_QUEUE:
                takeFromWorkQueueHandler();
                break;
            case CON_ASSUME_REQUEST:
                conAssumeRequestHandler();
                break;
            case ASSUME_BLOCKED_REQUEST:
                assumeBlockedRequestHandler();
                break;
            case SYM_ASSUME_REQUEST:
                symAssumeRequestHandler();
                break;
            case CAS_REQUEST:
                casRequestHandler();
                break;
            default:
                JmcRuntime.threadWaitReq = null;
                waitEventHandler();
                break;
        }
    }

    /**
     * Determines the type of the event.
     *
     * <p>This method is used to determine the type of the event. It checks the state of the runtime
     * environment and returns the type of the event that should be handled next.
     *
     * @return the type of the event that should be handled next.
     */
    private RequestType determineEventType() {
        if (JmcRuntime.threadStartReq != null) {
            return RequestType.START_REQUEST;
        } else if (JmcRuntime.threadEnterMonitorReq != null) {
            return RequestType.ENTER_MONITOR_REQUEST;
        } else if (JmcRuntime.threadExitMonitorReq != null) {
            return RequestType.EXIT_MONITOR_REQUEST;
        } else if (JmcRuntime.threadJoinReq != null) {
            return RequestType.JOIN_REQUEST;
        } else if (JmcRuntime.writeEventReq != null) {
            return RequestType.WRITE_REQUEST;
        } else if (JmcRuntime.readEventReq != null) {
            return RequestType.READ_REQUEST;
        } else if (JmcRuntime.sendEventReq != null) {
            return RequestType.SEND_REQUEST;
        } else if (JmcRuntime.receiveEventReq != null) {
            return RequestType.RECV_REQUEST;
        } else if (JmcRuntime.blockingReceiveEventReq != null) {
            return RequestType.RECV_BLOCKING_REQUEST;
        } else if (JmcRuntime.isFinished) {
            return RequestType.FINISH_REQUEST;
        } else if (JmcRuntime.symbolicOperation != null) {
            return RequestType.SYMB_ARTH_REQUEST;
        } else if (JmcRuntime.unparkerThread != null) {
            return RequestType.UNPARK_REQUEST;
        } else if (JmcRuntime.threadToPark != null) {
            return RequestType.PARK_REQUEST;
        } else if (JmcRuntime.mainStartEventReq != null) {
            return RequestType.MAIN_START_REQUEST;
        } else if (JmcRuntime.getFutureReq != null) {
            return RequestType.GET_FUTURE_REQUEST;
        } else if (JmcRuntime.takeFromBlockingQueueReq != null) {
            return RequestType.TAKE_WORK_QUEUE;
        } else if (JmcRuntime.conAssumeEventReq != null) {
            return RequestType.CON_ASSUME_REQUEST;
        } else if (JmcRuntime.assumeBlockedEventReq != null) {
            return RequestType.ASSUME_BLOCKED_REQUEST;
        } else if (JmcRuntime.symAssumeEventReq != null) {
            return RequestType.SYM_ASSUME_REQUEST;
        } else if (JmcRuntime.exclusiveReadEventReq != null
                && JmcRuntime.exclusiveWriteEventReq != null) {
            return RequestType.CAS_REQUEST;
        } else {
            return RequestType.WAIT_REQUEST;
        }
    }

    private void casRequestHandler() {
        LOGGER.debug("CAS request handler is called");
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        Optional<ReadExEvent> exclusiveReadEvent =
                Optional.ofNullable(JmcRuntime.exclusiveReadEventReq);
        Optional<WriteExEvent> exclusiveWriteEvent =
                Optional.ofNullable(JmcRuntime.exclusiveWriteEventReq);
        JmcRuntime.exclusiveReadEventReq = null;
        JmcRuntime.exclusiveWriteEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (exclusiveReadEvent.isPresent()
                && exclusiveWriteEvent.isPresent()
                && thread.isPresent()) {
            Thread nextThread =
                    searchStrategy.nextCasRequest(
                            thread.get(), exclusiveReadEvent.get(), exclusiveWriteEvent.get());
            notifyThread(nextThread);
        }
    }

    private void symAssumeRequestHandler() {
        LOGGER.debug("Symbolic assume request handler is called");
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        Optional<SymbolicOperation> symAssumeEvent =
                Optional.ofNullable(JmcRuntime.symAssumeEventReq);
        JmcRuntime.symAssumeEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (symAssumeEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextSymAssumeRequest(thread.get(), symAssumeEvent.get());
            notifyThread(thread.get());
        }
    }

    private void conAssumeRequestHandler() {
        LOGGER.debug("Concrete assume request handler is called");
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        Optional<ConAssumeEvent> conAssumeEvent =
                Optional.ofNullable(JmcRuntime.conAssumeEventReq);
        JmcRuntime.conAssumeEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (conAssumeEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextConAssumeRequest(conAssumeEvent.get());
            waitEventHandler();
        }
    }

    private void assumeBlockedRequestHandler() {
        LOGGER.debug("Assume blocked request handler is called");
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        Optional<AssumeBlockedEvent> assumeBlockedEvent =
                Optional.ofNullable(JmcRuntime.assumeBlockedEventReq);
        JmcRuntime.assumeBlockedEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (assumeBlockedEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextAssumeBlockedRequest(assumeBlockedEvent.get());
            waitEventHandler();
        }
    }

    private void mainStartEventHandler() {
        LOGGER.debug("Main Start event handler is called");
        Optional<MainStartEvent> mainStartEvent =
                Optional.ofNullable(JmcRuntime.mainStartEventReq);
        Optional<Thread> callerThread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.mainStartEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (mainStartEvent.isPresent() && callerThread.isPresent()) {
            searchStrategy.nextMainStartEvent(mainStartEvent.get());
            waitEventHandler();
        }
    }

    private void takeFromWorkQueueHandler() {
        // TODO()
    }

    /**
     * Handles the start event.
     *
     * <p>This method is used to handle the start event. It retrieves the thread that requested to
     * start, sets the thread wait request to null, if both the callee thread and the caller thread
     * are present, it calls the next start event method of the search strategy, and then starts the
     * callee thread.
     */
    private void startEventHandler() {
        LOGGER.debug("Start event handler is called");
        Optional<Thread> calleeThread = Optional.ofNullable(JmcRuntime.threadStartReq);
        Optional<Thread> callerThread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.threadWaitReq = null;
        JmcRuntime.threadStartReq = null;
        if (calleeThread.isPresent() && callerThread.isPresent()) {
            searchStrategy.nextStartEvent(calleeThread.get(), callerThread.get());
            startThread(calleeThread.get());
        }
    }

    /**
     * Starts the specified thread.
     *
     * <p>This method is used to start the specified thread. It prints a message indicating that the
     * thread is permitted to run for loading in the runtime environment, and then starts the
     * thread.
     *
     * @param thread the thread to be started.
     */
    private void startThread(Thread thread) {
        LOGGER.debug(
                "Thread-{} is permitted to run for loading in the runtime environment",
                getThreadId(thread));
        if (thread instanceof JMCStarterThread jmcStarterThread) {
            jmcStarterThread.startByScheduler();
        } else {
            thread.start();
        }
    }

    /**
     * Handles the enter monitor request of a thread.
     *
     * <p>This method is used to handle the enter monitor request of a thread. It adds the thread
     * and the monitor into the monitorRequest and calls the next enter monitor request method of
     * the search strategy. If the next thread to run is null, it calls the next deadlock event
     * method of the search strategy. Otherwise, it notifies the thread to run.
     */
    public void enterMonitorRequestHandler() {
        LOGGER.debug("Enter monitor request handler is called");
        Optional<Thread> enterMonitorThread =
                Optional.ofNullable(JmcRuntime.threadEnterMonitorReq);
        Optional<Object> enterMonitorObject =
                Optional.ofNullable(JmcRuntime.objectEnterMonitorReq);
        JmcRuntime.threadEnterMonitorReq = null;
        JmcRuntime.objectEnterMonitorReq = null;
        JmcRuntime.threadWaitReq = null;
        if (enterMonitorThread.isPresent() && enterMonitorObject.isPresent()) {
            Optional<Thread> thread =
                    Optional.ofNullable(
                            searchStrategy.nextEnterMonitorRequest(
                                    enterMonitorThread.get(), enterMonitorObject.get()));
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
     * <p>This method is used to handle the exit monitor request of a thread. It retrieves the
     * thread and the monitor that requested to exit, handles the exit monitor event by calling the
     * next exit monitor event method of the search strategy, sets the thread exit monitor request
     * and the object exit monitor request to null, sets the thread wait request to null, and
     * finally calls the wait event handler, which selects the next thread to run.
     */
    public void exitMonitorRequestHandler() {
        LOGGER.debug("Exit monitor request handler is called");
        Optional<Thread> exitMonitorThread =
                Optional.ofNullable(JmcRuntime.threadExitMonitorReq);
        Optional<Object> exitMonitorObject =
                Optional.ofNullable(JmcRuntime.objectExitMonitorReq);
        JmcRuntime.threadExitMonitorReq = null;
        JmcRuntime.objectExitMonitorReq = null;
        JmcRuntime.threadWaitReq = null;
        if (exitMonitorThread.isPresent() && exitMonitorObject.isPresent()) {
            searchStrategy.nextExitMonitorEvent(exitMonitorThread.get(), exitMonitorObject.get());
            waitEventHandler();
        }
    }

    /**
     * Handles the join request of a thread.
     *
     * <p>This method is used to handle the join request of a thread. It retrieves the thread that
     * requested to join and the thread that is joined, sets the thread join request and the thread
     * join response to null, sets the thread wait request to null, and finally handles the join
     * request by calling the next join request method of the search strategy, and then notifies the
     * thread to run.
     */
    public void joinRequestHandler() {
        LOGGER.debug("Join request handler is called");
        Optional<Thread> joinRequestThread = Optional.ofNullable(JmcRuntime.threadJoinReq);
        Optional<Thread> joinResponseThread = Optional.ofNullable(JmcRuntime.threadJoinRes);
        JmcRuntime.threadJoinReq = null;
        JmcRuntime.threadJoinRes = null;
        JmcRuntime.threadWaitReq = null;
        if (joinRequestThread.isPresent() && joinResponseThread.isPresent()) {
            Optional<Thread> thread =
                    Optional.ofNullable(
                            searchStrategy.nextJoinRequest(
                                    joinRequestThread.get(), joinResponseThread.get()));
            thread.ifPresent(this::notifyThread);
        }
    }

    /**
     * Handles the read request of a thread.
     *
     * <p>This method is used to handle the read request event of a thread. It retrieves the read
     * event that requested by the thread, handles the read request, sets the read event request to
     * null, sets the thread wait request to null, and then notifies the thread.
     */
    public void readRequestHandler() {
        LOGGER.debug("Read request handler is called");
        Optional<ReadEvent> readRequestEvent = Optional.ofNullable(JmcRuntime.readEventReq);
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.readEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (readRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextReadEvent(readRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    /** Handle a Non-Blocking receive request. */
    public void receiveRequestHandler() {
        LOGGER.debug("Receive request handler is called");
        Optional<ReceiveEvent> receiveRequestEvent =
                Optional.ofNullable(JmcRuntime.receiveEventReq);
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.receiveEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (receiveRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextReceiveEvent(receiveRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    /** Handle a Blocking receive request. */
    public void blockingReceiveRequestHandler() {
        LOGGER.debug("Blocking receive request handler is called");
        Optional<ReceiveEvent> blockingReceiveEventReq =
                Optional.ofNullable(JmcRuntime.blockingReceiveEventReq);
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.blockingReceiveEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (blockingReceiveEventReq.isPresent() && thread.isPresent()) {
            if (searchStrategy.nextBlockingReceiveRequest(blockingReceiveEventReq.get())) {
                notifyThread(thread.get());
            } else {
                waitEventHandler();
            }
        }
    }

    /**
     * Handles the write request of a thread.
     *
     * <p>This method is used to handle the write request of a thread. It retrieves the write event
     * that requested by the thread, handles the write request, sets the write event request to
     * null, sets the thread wait request to null, and then notifies the thread.
     */
    public void writeRequestHandler() {
        LOGGER.debug("Write request handler is called");
        Optional<WriteEvent> writeRequestEvent =
                Optional.ofNullable(JmcRuntime.writeEventReq);
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.writeEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (writeRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextWriteEvent(writeRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    /** Handle a request to send a message. */
    public void sendRequestHandler() {
        LOGGER.debug("Send request handler is called");
        Optional<SendEvent> sendRequestEvent = Optional.ofNullable(JmcRuntime.sendEventReq);
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.sendEventReq = null;
        JmcRuntime.threadWaitReq = null;
        if (sendRequestEvent.isPresent() && thread.isPresent()) {
            searchStrategy.nextSendEvent(sendRequestEvent.get());
            notifyThread(thread.get());
        }
    }

    /**
     * Handles the finish request of a thread.
     *
     * <p>This method is used to handle the finish request of a thread. It retrieves the thread that
     * requested to finish, sets the thread wait request to null, handles the finish request by
     * calling the next finish request method of the search strategy, and then notifies the returned
     * thread to run.
     */
    public void finishRequestHandler() {
        LOGGER.debug("Finish request handler is called");
        Optional<Thread> finishedThread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        JmcRuntime.threadWaitReq = null;
        JmcRuntime.isFinished = false;
        if (finishedThread.isPresent()) {
            Thread thread = searchStrategy.nextFinishRequest(finishedThread.get());
            notifyThread(thread);
        }
    }

    /**
     * Handles the unpark request of a thread.
     *
     * <p>This method is used to handle the unpark request of a thread. It retrieves the unparker
     * thread and the unparkee thread, sets the unparker thread and the unparkee thread to null,
     * sets the thread wait request to null, and then calls the next unpark request method of the
     * search strategy. Finally, it calls the wait event handler to select the next thread to run.
     */
    public void unparkRequestHandler() {
        LOGGER.debug("Unpark request handler is called");
        Optional<Thread> unparkerThread = Optional.ofNullable(JmcRuntime.unparkerThread);
        Optional<Thread> unparkeeThread = Optional.ofNullable(JmcRuntime.unparkeeThread);
        JmcRuntime.unparkerThread = null;
        JmcRuntime.unparkeeThread = null;
        JmcRuntime.threadWaitReq = null;
        if (unparkerThread.isPresent() && unparkeeThread.isPresent()) {
            searchStrategy.nextUnparkRequest(unparkerThread.get(), unparkeeThread.get());
            waitEventHandler();
        }
    }

    /**
     * Handles the park request of a thread.
     *
     * <p>This method is used to handle the park request of a thread. It retrieves the thread that
     * requested to park, sets the thread to park to null, sets the thread wait request to null, and
     * then calls the next park request method of the search strategy. Finally, it calls the wait
     * event handler to select the next thread to run.
     */
    public void parkRequestHandler() {
        LOGGER.debug("Park request handler is called");
        Optional<Thread> threadToPark = Optional.ofNullable(JmcRuntime.threadToPark);
        JmcRuntime.threadToPark = null;
        JmcRuntime.threadWaitReq = null;
        if (threadToPark.isPresent()) {
            searchStrategy.nextParkRequest(threadToPark.get());
            waitEventHandler();
        }
    }

    /**
     * Handles the symbolic arithmetic request of a thread.
     *
     * <p>This method is used to handle the symbolic arithmetic request of a thread. It retrieves
     * the thread that requested the symbolic arithmetic operation, sets the thread wait request to
     * null, and then calls the next symbolic operation request method of the search strategy.
     * Finally, it notifies the thread to run.
     */
    public void symbolicArithmeticRequestHandler() {
        LOGGER.debug("Symbolic arithmetic request handler is called");
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        Optional<SymbolicOperation> symbolicOperation =
                Optional.ofNullable(JmcRuntime.symbolicOperation);
        JmcRuntime.threadWaitReq = null;
        JmcRuntime.symbolicOperation = null;
        if (thread.isPresent() && symbolicOperation.isPresent()) {
            searchStrategy.nextSymbolicOperationRequest(thread.get(), symbolicOperation.get());
            notifyThread(thread.get());
        }
    }

    public void getFutureRequestHandler() {
        LOGGER.debug("Get future request handler is called");
        Optional<Thread> thread = Optional.ofNullable(JmcRuntime.threadWaitReq);
        Optional<FutureTask> getFutureRequest =
                Optional.ofNullable(JmcRuntime.getFutureReq);
        JmcRuntime.threadWaitReq = null;
        JmcRuntime.getFutureReq = null;
        if (thread.isPresent() && getFutureRequest.isPresent()) {
            notifyThread(searchStrategy.nextGetFutureRequest(thread.get(), getFutureRequest.get()));
        }
    }

    /**
     * Checks whether the last execution is finished or not.
     *
     * <p>This method is used to check whether the last execution is finished or not. If the search
     * strategy is done, it sets the {@link JmcRuntime#allExecutionsFinished} flag to true.
     */
    public void wasLastExecution() {
        if (searchStrategy.done()) {
            JmcRuntime.allExecutionsFinished = true;
        }
    }
}
