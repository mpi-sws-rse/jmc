package org.example.checker;

import org.example.runtime.RuntimeEnvironment;
import programStructure.ReadEvent;
import programStructure.WriteEvent;

import java.util.Random;

public interface SearchStrategy {

    public void nextStartEvent(Thread calleeThread, Thread callerThread);

    public void nextEnterMonitorEvent(Thread thread, Object monitor);

    public void nextExitMonitorEvent(Thread thread, Object monitor);

    public void nextJoinEvent(Thread joinReq, Thread joinRes);

    public void nextReadEvent(ReadEvent readEvent);

    public void nextWriteEvent(WriteEvent writeEvent);

    public boolean done();

    /*
     * The following method is used to select the next random thread to run.
     * When the @readyThreadList has more than one thread, the @SchedulerThread checks the @monitorRequest to see
     * whether there is a monitor request by the candidate thread or not. If there is a monitor request, the
     * @SchedulerThread checks the @monitorList in the RuntimeEnvironment to see whether the monitor is available or
     * not. If the monitor is available, the @SchedulerThread selects the candidate thread to run and removes the thread
     * and the monitor from the @monitorRequest. Otherwise, the @SchedulerThread selects another thread to run.
     */
    default Thread pickNextRandomThread() {
        /*
         * If the @readyThreadList has more than one thread, the @SchedulerThread selects a random thread to run.
         * If the @readyThreadList has only one thread, the @SchedulerThread selects the only thread to run.
         * If the @readyThreadList has no thread, the @SchedulerThread terminates.
         */
        if (RuntimeEnvironment.readyThreadList.size() > 1) {
            Random random = new Random();
            int randomIndex = random.nextInt(RuntimeEnvironment.readyThreadList.size());
            Thread randomElement = RuntimeEnvironment.readyThreadList.get(randomIndex);
            System.out.println(
                    "[Scheduler Thread Message] : " + randomElement.getName() + " is selected to to be a " +
                        "candidate to run"
            );

            // The following if-else statement is used to check whether the candidate thread has a monitor or join
            // request. If the candidate thread has a monitor request, the @SchedulerThread checks the corresponding
            // monitor in the @monitorList to see whether the monitor is available or not. If the monitor is available,
            // the @SchedulerThread selects the candidate thread to run and removes the thread and the monitor from the
            // @monitorRequest. Otherwise, the @SchedulerThread selects another thread to run.
            // If the candidate thread has a join request, the @SchedulerThread checks the corresponding join request in
            // the @createdThreadList to see whether the join request is available or not. If the join request is
            // available the @SchedulerThread selects the candidate thread to run and removes the join request from the
            // @joinRequest. Otherwise, the @SchedulerThread selects another thread to run.
            if (RuntimeEnvironment.monitorRequest.containsKey(randomElement)) {

                // Get the monitor of the selected thread
                Object monitor = RuntimeEnvironment.monitorRequest.get(randomElement);
                System.out.println("[Scheduler Thread Message] : " + RuntimeEnvironment.threadIdMap.get(randomElement.getId())
                        + " is requested to enter the monitor " + monitor);

                if (RuntimeEnvironment.monitorList.containsKey(monitor)) {
                    System.out.println("[Scheduler Thread Message] : However, the monitor " + monitor + " is not available");
                    System.out.println("[Scheduler Thread Message] : The monitor " + monitor + " is already in use by " +
                            RuntimeEnvironment.threadIdMap.get(RuntimeEnvironment.monitorList.get(monitor).getId()));
                    return pickNextRandomThread();
                } else {
                    System.out.println("[Scheduler Thread Message] : The monitor " + monitor + " is available");
                    RuntimeEnvironment.monitorRequest.remove(randomElement, monitor);
                    System.out.println("[Scheduler Thread Message] : The request of " + randomElement.getName() +
                            " to enter the monitor " + monitor + " is removed from the monitorRequest");
                    nextEnterMonitorEvent(randomElement, monitor);
                    return randomElement;
                }
            } else if (RuntimeEnvironment.joinRequest.containsKey(randomElement)) {

                // Get the join request of the selected thread
                Thread joinRes = RuntimeEnvironment.joinRequest.get(randomElement);
                System.out.println("[Scheduler Thread Message] : " + randomElement.getName() + " is requested to join "
                        + joinRes.getName());

                if (!RuntimeEnvironment.createdThreadList.contains(joinRes) &&
                        !RuntimeEnvironment.readyThreadList.contains(joinRes)) {
                    RuntimeEnvironment.joinRequest.remove(randomElement, joinRes);
                    System.out.println("[Scheduler Thread Message] : As " + joinRes.getName() + " is not in the " +
                            "createdThreadList or the readyThreadList, the request of " + randomElement.getName() +
                            " to join " + joinRes.getName() + " is removed from the joinRequest");
                    nextJoinEvent(randomElement, joinRes);
                    System.out.println("[Scheduler Thread Message] : " + randomElement.getName() + " is selected to run");
                    return randomElement;
                } else {
                    System.out.println("[Scheduler Thread Message] : "+randomElement.getName()+ " is requested to join "+joinRes.getName());
                    System.out.println("[Scheduler Thread Message] : However, "+joinRes.getName()+" is not finished yet");
                    return pickNextRandomThread();
                }
            } else {
                return randomElement;
            }
        } else if (RuntimeEnvironment.readyThreadList.size() == 1){
            System.out.println("[Scheduler Thread Message] : Only one thread is in the ready list");
            if (RuntimeEnvironment.joinRequest.containsKey(RuntimeEnvironment.readyThreadList.get(0))){
                Thread joinRes = RuntimeEnvironment.joinRequest.get(RuntimeEnvironment.readyThreadList.get(0));
                RuntimeEnvironment.joinRequest.remove(RuntimeEnvironment.readyThreadList.get(0), joinRes);
                nextJoinEvent(RuntimeEnvironment.readyThreadList.get(0), joinRes);
                System.out.println("[Scheduler Thread Message] : As " + joinRes.getName() + " is not in the createdThreadList or the readyThreadList, the request of " + RuntimeEnvironment.readyThreadList.get(0).getName() + " to join " + joinRes.getName() + " is removed from the joinRequest");
                System.out.println("[Scheduler Thread Message] : " + RuntimeEnvironment.readyThreadList.get(0).getName() + " is selected to run");
            }
            return RuntimeEnvironment.readyThreadList.get(0);
        }else{
            System.out.println("[Scheduler Thread Message] : There is no thread in the ready list");
            System.out.println("[Scheduler Thread Message] : The scheduler thread is going to terminate");
            return null;
        }
    }

}
