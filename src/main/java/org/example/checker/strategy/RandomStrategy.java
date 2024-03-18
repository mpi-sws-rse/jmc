package org.example.checker.strategy;

import org.example.checker.SearchStrategy;
import org.example.runtime.RuntimeEnvironment;
import programStructure.*;
import java.util.*;

/**
 * The RandomStrategy class implements the {@link SearchStrategy} interface and is responsible for managing the execution
 * order of events in a multithreaded program using a random strategy. It maintains a record of random events and a
 * random number generator for the random strategy. The class provides functionality to handle various types of events
 * including start, enter monitor, exit monitor, join, read, write, and finish events. The class uses the
 * {@link RuntimeEnvironment} API to create and record events. The class initializes the random number generator with
 * the seed value from the {@link RuntimeEnvironment}. It also includes functionality for printing the execution trace
 * and checking if the execution is done. The RandomStrategy class is designed to control the flow of a program's
 * execution and ensure a random execution order of operations.
 */
public class RandomStrategy implements SearchStrategy {

    /**
     * @property {@link #random} is a random number generator.
     */
    public Random random;

    /**
     * The following constructor initializes the random events record and the random number generator with the given
     * seed.
     */
    public RandomStrategy() {
        RuntimeEnvironment.randomEventsRecord = new ArrayList<>();
        random = new Random(RuntimeEnvironment.seed);
    }


    /**
     * Selects a random thread from the ready thread list based on the {@link #random} object.
     * <p>
     * This method selects a random thread from the ready thread list.
     *
     * @param readyThreadList is the list of threads that are ready to run.
     * @return the selected random thread.
     */
    @Override
    public Thread selectRandomThread(List<Thread> readyThreadList) {
        int randomIndex = random.nextInt(readyThreadList.size());
        Thread randomElement = readyThreadList.get(randomIndex);
        System.out.println(
                "[Scheduler Thread Message] : " + randomElement.getName() + " is selected to to be a " +
                        "candidate to run"
        );
        return randomElement;
    }

    /**
     * Creates a {@link StartEvent} for the corresponding starting a thread request of a thread
     * <p>
     * This method creates a {@link StartEvent} for the corresponding starting a thread request of a thread.
     * The created {@link StartEvent} is added to the {@link RuntimeEnvironment#randomEventsRecord}.
     *
     * @param calleeThread is the thread that is going to be started.
     * @param callerThread is the thread that is going to call the start method of the calleeThread.
     */
    @Override
    public void nextStartEvent(Thread calleeThread, Thread callerThread) {
        StartEvent startEvent = RuntimeEnvironment.createStartEvent(calleeThread, callerThread);
        RuntimeEnvironment.randomEventsRecord.add(startEvent);
    }

    /**
     * Creates an {@link EnterMonitorEvent} for the corresponding entering a monitor request of a thread
     * <p>
     * This method creates a {@link EnterMonitorEvent} for the corresponding entering a monitor request of a thread.
     * The created {@link EnterMonitorEvent} is added to the {@link RuntimeEnvironment#randomEventsRecord}.
     *
     * @param thread is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        EnterMonitorEvent enterMonitorEvent = RuntimeEnvironment.createEnterMonitorEvent(thread, monitor);
        RuntimeEnvironment.randomEventsRecord.add(enterMonitorEvent);
    }

    /**
     * Creates an {@link ExitMonitorEvent} for the corresponding exiting a monitor request of a thread
     * <p>
     * This method creates an {@link ExitMonitorEvent} for the corresponding exiting a monitor request of a thread.
     * The created {@link ExitMonitorEvent} is added to the {@link RuntimeEnvironment#randomEventsRecord}.
     * The method also analyzes the suspended threads for the released monitor.
     *
     * @param thread is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    @Override
    public void nextExitMonitorEvent(Thread thread, Object monitor) {
        ExitMonitorEvent exitMonitorEvent = RuntimeEnvironment.createExitMonitorEvent(thread, monitor);
        RuntimeEnvironment.randomEventsRecord.add(exitMonitorEvent);
        analyzeSuspendedThreadsForMonitor(monitor);
    }

    /**
     * Creates a {@link JoinEvent} for the corresponding joining a thread request of a thread
     * <p>
     * This method creates a {@link JoinEvent} for the corresponding joining a thread request of a thread.
     * The created {@link JoinEvent} is added to the {@link RuntimeEnvironment#randomEventsRecord}.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    @Override
    public void nextJoinEvent(Thread joinReq, Thread joinRes) {
        JoinEvent joinEvent = RuntimeEnvironment.createJoinEvent(joinReq, joinRes);
        RuntimeEnvironment.randomEventsRecord.add(joinEvent);
    }

    /**
     * Records the created {@link ReadEvent} for the corresponding reading a variable request of a thread
     * <p>
     * This method records the created {@link ReadEvent} for the corresponding reading a variable request of a thread.
     * The {@link ReadEvent} is added to the {@link RuntimeEnvironment#randomEventsRecord}.
     *
     * @param readEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        RuntimeEnvironment.randomEventsRecord.add(readEvent);
    }

    /**
     * Records the created {@link WriteEvent} for the corresponding writing a variable request of a thread
     * <p>
     * This method records the created {@link WriteEvent} for the corresponding writing a variable request of a thread.
     * The {@link WriteEvent} is added to the {@link RuntimeEnvironment#randomEventsRecord}.
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        RuntimeEnvironment.randomEventsRecord.add(writeEvent);
    }

    /**
     * Creates a {@link FinishEvent} for the corresponding finishing execution request of a thread
     * <p>
     * This method creates a {@link FinishEvent} for the corresponding finishing execution request of a thread.
     * The created {@link FinishEvent} is added to the {@link RuntimeEnvironment#randomEventsRecord}.
     * The method also analyzes the suspended threads for joining the finished thread.
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextFinishEvent(Thread thread) {
        FinishEvent finishEvent = RuntimeEnvironment.createFinishEvent(thread);
        RuntimeEnvironment.randomEventsRecord.add(finishEvent);
        analyzeSuspendedThreadsForJoin(thread);
    }

    /**
     * Prints the current execution trace.
     */
    @Override
    public void printExecutionTrace() {
        System.out.println("[Search Strategy Message] : Execution trace:");
        for (Event event : RuntimeEnvironment.randomEventsRecord) {
            int index = RuntimeEnvironment.randomEventsRecord.indexOf(event) + 1;
            System.out.println("[Search Strategy Message] : " + index + "." + event);
        }
    }

    /**
     * Indicates whether the execution is done or not.
     * <p>
     * This method indicates whether the execution is done or not.
     *
     * @return true if the execution is done, otherwise false.
     */
    @Override
    public boolean done() {
        return (RuntimeEnvironment.numOfExecutions == RuntimeEnvironment.maxNumOfExecutions);
    }
}