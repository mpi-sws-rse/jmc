package org.example.checker;

import org.example.runtime.RuntimeEnvironment;
import programStructure.ReadEvent;
import programStructure.WriteEvent;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public interface SearchStrategy {

    /**
     * This method represents the required strategy for the next start event.
     *
     * @param calleeThread is the thread that is going to be started.
     * @param callerThread is the thread that is going to call the start method of the calleeThread.
     */
    void nextStartEvent(Thread calleeThread, Thread callerThread);

    /**
     * This method represents the required strategy for the next enter monitor event.
     *
     * @param thread is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    void nextEnterMonitorEvent(Thread thread, Object monitor);

    /**
     * This method represents the required strategy for the next exit monitor event.
     *
     * @param thread is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    void nextExitMonitorEvent(Thread thread, Object monitor);

    /**
     * This method represents the required strategy for the next join event.
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    void nextJoinEvent(Thread joinReq, Thread joinRes);

    /**
     * This method represents the required strategy for the next read event.
     *
     * @param readEvent is the read event that is going to be executed.
     */
    void nextReadEvent(ReadEvent readEvent);

    /**
     * This method represents the required strategy for the next write event.
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    void nextWriteEvent(WriteEvent writeEvent);

    /**
     * This method represents the required strategy for the next finish event.
     *
     * @param thread is the thread that is going to be finished.
     */
    void nextFinishEvent(Thread thread);

    /**
     * This method prints the current execution trace.
     */
    void printExecutionTrace();

    /**
     * This method indicates whether the execution is done or not.
     *
     * @return true if the execution is done, otherwise false.
     */
    boolean done();

    /**
     * Selects the next random thread to run.
     * <p>
     * This method is used to select the next random thread to run. It checks the monitor request and join request of the
     * candidate thread and handles them appropriately.
     *
     * @return the next random thread to run.
     */
    default Thread pickNextRandomThread() {
        Optional<List<Thread>> readyThreadList = Optional.ofNullable(RuntimeEnvironment.readyThreadList);
        if (readyThreadList.isPresent()) {
            if (readyThreadList.get().size() > 1) {
                Thread randomElement = selectRandomThread(readyThreadList.get());
                return handleChosenThreadRequest(randomElement);
            } else if (readyThreadList.get().size() == 1) {
                System.out.println("[Scheduler Thread Message] : Only one thread is in the ready list");
                return handleChosenThreadRequest(readyThreadList.get().get(0));
            } else {
                System.out.println("[Scheduler Thread Message] : There is no thread in the ready list");
                System.out.println("[Scheduler Thread Message] : The scheduler thread is going to terminate");
                return null;
            }
        } else {
            System.out.println("[Scheduler Thread Message] : There is no thread in the ready list");
            System.out.println("[Scheduler Thread Message] : The scheduler thread is going to terminate");
            return null;
        }
    }

    /**
     * Selects a random thread from the {@link RuntimeEnvironment#readyThreadList} list.
     *
     * @param readyThreadList the ready thread list.
     * @return the selected random thread.
     */
    default Thread selectRandomThread(List<Thread> readyThreadList) {
        Random random = new Random();
        int randomIndex = random.nextInt(readyThreadList.size());
        Thread randomElement = readyThreadList.get(randomIndex);
        System.out.println(
                "[Scheduler Thread Message] : " + randomElement.getName() + " is selected to to be a " +
                        "candidate to run"
        );
        return randomElement;
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
        } else {
            return thread;
        }
    }

    /**
     * Handles the monitor request of the candidate thread.
     *
     * @param thread the candidate thread.
     * @return the candidate thread if it can run, otherwise selects another random thread.
     */
    default Thread handleMonitorRequest(Thread thread) {
        // Get the monitor of the selected thread
        Object monitor = RuntimeEnvironment.monitorRequest.get(thread);
        System.out.println(
                "[Scheduler Thread Message] : " + RuntimeEnvironment.threadIdMap.get(thread.getId())
                        + " is requested to enter the monitor " + monitor
        );
        if (RuntimeEnvironment.monitorList.containsKey(monitor)) {
            System.out.println("[Scheduler Thread Message] : However, the monitor " + monitor + " is not available");
            System.out.println(
                    "[Scheduler Thread Message] : The monitor " + monitor + " is already in use by " +
                            RuntimeEnvironment.threadIdMap.get(RuntimeEnvironment.monitorList.get(monitor).getId())
            );
            return pickNextRandomThread();
        } else {
            System.out.println("[Scheduler Thread Message] : The monitor " + monitor + " is available");
            RuntimeEnvironment.monitorRequest.remove(thread, monitor);
            System.out.println(
                    "[Scheduler Thread Message] : The request of " + thread.getName() +
                        " to enter the monitor " + monitor + " is removed from the monitorRequest"
            );
            nextEnterMonitorEvent(thread, monitor);
            return thread;
        }
    }

    /**
     * Handles the join request of the candidate thread.
     *
     * @param thread the candidate thread.
     * @return the candidate thread if it can run, otherwise selects another random thread.
     */
    default Thread handleJoinRequest(Thread thread) {
        // Get the join request of the selected thread
        Thread joinRes = RuntimeEnvironment.joinRequest.get(thread);
        System.out.println(
                "[Scheduler Thread Message] : " + thread.getName() + " is requested to join "
                    + joinRes.getName()
        );
        if (!RuntimeEnvironment.createdThreadList.contains(joinRes) &&
                !RuntimeEnvironment.readyThreadList.contains(joinRes)) {
            RuntimeEnvironment.joinRequest.remove(thread, joinRes);
            System.out.println(
                    "[Scheduler Thread Message] : As " + joinRes.getName() + " is not in the " +
                        "createdThreadList or the readyThreadList, the request of " + thread.getName() +
                        " to join " + joinRes.getName() + " is removed from the joinRequest"
            );
            nextJoinEvent(thread, joinRes);
            return thread;
        } else {
            System.out.println(
                    "[Scheduler Thread Message] : " + thread.getName() +  " is requested to join " +
                        joinRes.getName()
            );
            System.out.println("[Scheduler Thread Message] : However, " + joinRes.getName() + " is not finished yet");
            return pickNextRandomThread();
        }
    }
}