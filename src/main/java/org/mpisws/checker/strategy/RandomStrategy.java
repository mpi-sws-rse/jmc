package org.mpisws.checker.strategy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.RuntimeEnvironment;
import programStructure.*;


/**
 * The RandomStrategy class implements the {@link SearchStrategy} interface and is responsible for managing
 * the execution order of events in a multithreaded program using a random strategy.
 * It maintains a record of random events and a random number generator for the random strategy.
 * The class provides functionality to handle various types of events
 * including start, enter monitor, exit monitor, join, read, write, and finish events. The class uses the
 * {@link RuntimeEnvironment} API to create and record events. The class initializes the random number generator with
 * the seed value from the {@link RuntimeEnvironment}. It also includes functionality for printing the execution trace
 * and checking if the execution is done. The RandomStrategy class is designed to control the flow of a program's
 * execution and ensure a random execution order of operations.
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
     * The following constructor initializes the random events record and the random number generator with the given
     * seed. It also initializes the path to the buggy trace object.
     */
    public RandomStrategy() {
        buggyTracePath = RuntimeEnvironment.buggyTracePath;
        if (!Files.exists(Paths.get(buggyTracePath))) {
            System.out.println("[Random Strategy Message] : Directory " + buggyTracePath + " does not exist ");
            System.exit(0);
        }
        buggyTraceFile = RuntimeEnvironment.buggyTraceFile;
        random = new Random(RuntimeEnvironment.seed);
    }

    /**
     * Selects a random thread from the ready thread list based on the {@link #random} object.
     * <p>
     * This method selects a random thread from the ready thread list.
     * </p>
     *
     * @param readyThreadList is the list of threads that are ready to run.
     * @return the selected random thread.
     */
    @Override
    public Thread selectRandomThread(List<Thread> readyThreadList) {
        int randomIndex = random.nextInt(readyThreadList.size());
        Thread randomElement = readyThreadList.get(randomIndex);
        System.out.println(
                "[Random Strategy Message] : " + randomElement.getName() + " is selected to to be a " +
                        "candidate to run"
        );
        return randomElement;
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
        return pickNextRandomThread();
    }

    /**
     * Handles the next enter monitor request of a given thread.
     * <p>
     * This method records the monitor request in the {@link RuntimeEnvironment#monitorRequest} map. It also checks
     * for a deadlock between the threads in using the monitors. If a deadlock is detected, the method sets the
     * {@link RuntimeEnvironment#deadlockHappened} flag to true and the {@link RuntimeEnvironment#executionFinished}
     * flag to true. Otherwise, it sets the {@link RuntimeEnvironment#deadlockHappened} flag to false. The method also
     * selects the next random thread to run.
     * </p>
     *
     * @param thread  is the thread that is requested to enter the monitor.
     * @param monitor is the monitor that is requested to be entered by the thread.
     * @return the next random thread to run.
     */
    @Override
    public Thread nextEnterMonitorRequest(Thread thread, Object monitor) {
        MonitorRequestEvent monitorRequestEvent = RuntimeEnvironment.createMonitorRequestEvent(thread, monitor);
        RuntimeEnvironment.eventsRecord.add(monitorRequestEvent);
        RuntimeEnvironment.monitorRequest.put(thread, monitor);
        if (monitorsDeadlockDetection()) {
            System.out.println(
                    "[Random Strategy Message] : There is a deadlock between the threads in using " +
                            "the monitors"
            );
            RuntimeEnvironment.deadlockHappened = true;
            RuntimeEnvironment.executionFinished = true;
            return null;
        } else {
            System.out.println(
                    "[Random Strategy Message] : There is no deadlock between the threads in using " +
                            "the monitors"
            );
            return pickNextThread();
        }
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
    }

    /**
     * Creates a {@link FinishEvent} for the corresponding finishing execution request of a thread
     * <p>
     * This method creates a {@link FinishEvent} for the corresponding finishing execution request of a thread.
     * The created {@link FinishEvent} is added to the {@link RuntimeEnvironment#eventsRecord}.
     * The method also analyzes the suspended threads for joining the finished thread.
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
        return pickNextRandomThread();
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
        return pickNextRandomThread();
    }

    /**
     * Saves the current execution state.
     */
    @Override
    public void saveExecutionState() {
        printExecutionTrace();
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