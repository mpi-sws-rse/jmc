package org.example.runtime;
import java.util.*;

public class SchedulerThread extends Thread{

    // The @monitorRequest is used to store the threads that are waiting to enter a monitor.
    // The SchedulerThread uses this map in scheduling phase in order to select the next thread to run which will not cause a deadlock.
    // When a thread is selected to run, and it is waiting to enter a monitor, the SchedulerThread checks @monitorList in RuntimeEnvironment to see whether the monitor is available or not.
    // If the monitor is available, the SchedulerThread selects the thread to run and removes the thread from @monitorRequest. Otherwise, the SchedulerThread selects another thread to run.
    private Map<Thread, Object> monitorRequest = new HashMap<>();

    // The @isFinished variable is used to indicate whether the @SchedulerThread is finished or not.
    private boolean isFinished = false;

    @Override
    public void run() {
        /*
         * The following expression is used for the synchronization of the @SchedulerThread and the main thread.
         */
        RuntimeEnvironment.getPermission(RuntimeEnvironment.createdThreadList.get(0));

        System.out.println("**********************************************************************************************");
        System.out.println("[*** From this point on, the flow of the program is controlled by the SchedulerThread ***]");
        System.out.println("**********************************************************************************************");

        while (isFinished == false){

            /*
             * The following while loop is used by the @SchedulerThread to wait until the only other running thread requests to wait.
             * There could be a race condition on @threadWaitReq in the following while loop. While the SchedulerThread is
             * reading the value of @threadWaitReq, the only other running thread can change the value of @threadWaitReq.
             * To avoid from this situation, we used the @threadWaitReqLock object to synchronize the access to @threadWaitReq.
             */
            while (true){
                synchronized (RuntimeEnvironment.threadWaitReqLock){
                    if (RuntimeEnvironment.threadWaitReq != null){
                        break;
                    }
                    Thread.yield();
                }
            }

            System.out.println("[Scheduler Thread Message] : All threads are in waiting state");

            /*
             * The following while loop is used by the @SchedulerThread to wait until the state of the only other running thread
             * changes to the WAIT state.
             */
            synchronized (RuntimeEnvironment.locks.get(RuntimeEnvironment.threadWaitReq.getId())){
                System.out.println("[Scheduler Thread Message] : Scheduling phase begins");
            }

            /*
             * The following if-else statement is used to select the next thread to run.
             * If there is a new added thread into the @readyThreadList, the @SchedulerThread selects the new thread to run.
             * This is necessary for registering the new thread in the runtime environment. Since the @SchedulerThread use the notify method
             * to run the selected thread, the new added thread needs to be started and waited on its @lock object.
             * If there is a monitor request, the @SchedulerThread adds the thread and the monitor into the @monitorRequest and selects a random thread to run.
             * If there is no new added thread and no monitor request, the @SchedulerThread selects a random thread to run.
             */
            if (RuntimeEnvironment.threadStartReq != null){
                RuntimeEnvironment.threadWaitReq = null;
                Thread newThread = RuntimeEnvironment.threadStartReq;
                RuntimeEnvironment.threadStartReq = null;
                System.out.println("[Scheduler Thread Message] : "+newThread.getName()+" is selected to run for loading in the runtime environment");
                newThread.start();
            } else if (RuntimeEnvironment.threadEnterMonitorReq != null) {
                monitorRequest.put(RuntimeEnvironment.threadEnterMonitorReq, RuntimeEnvironment.objectEnterMonitorReq);
                RuntimeEnvironment.threadEnterMonitorReq = null;
                RuntimeEnvironment.objectEnterMonitorReq = null;
                pickNextRandomThread();
            } else{
                pickNextRandomThread();
            }
        }
        System.out.println("**********************************************************************************************");
        System.out.println("[*** The SchedulerThread requested to FINISH***]");
        System.out.println("**********************************************************************************************");
    }

    /*
     * The following method is used to select the next random thread to run.
     * If the @readyThreadList has more than one thread, the @SchedulerThread selects a random thread to run.
     * If the @readyThreadList has only one thread, the @SchedulerThread selects the only thread to run.
     * If the @readyThreadList has no thread, the @SchedulerThread terminates.
     * Now, let's explain the scheduling phase in detail.
     * When the @readyThreadList has more than one thread, the @SchedulerThread checks the @monitorRequest to see whether there is a monitor request by the candidate thread or not.
     * If there is a monitor request, the @SchedulerThread checks the @monitorList in the RuntimeEnvironment to see whether the monitor is available or not.
     * If the monitor is available, the @SchedulerThread selects the candidate thread to run and removes the thread and the monitor from the @monitorRequest.
     * Otherwise, the @SchedulerThread selects another thread to run.
     */
    private void pickNextRandomThread(){
        if (RuntimeEnvironment.readyThreadList.size() > 1){
            Random random = new Random();
            int randomIndex = random.nextInt(RuntimeEnvironment.readyThreadList.size()); // Generate a random index
            Thread randomElement = RuntimeEnvironment.readyThreadList.get(randomIndex); // Get the element at the random index
            System.out.println("[Scheduler Thread Message] : "+randomElement.getName()+ " is selected to to be a candidate to run");
            if (monitorRequest.containsKey(randomElement)){
                Object monitor = monitorRequest.get(randomElement); // Get the monitor of the selected thread
                System.out.println("[Scheduler Thread Message] : "+randomElement.getName()+ " is requested to enter the monitor "+monitor);
                if (RuntimeEnvironment.monitorList.containsKey(monitor)){
                    System.out.println("[Scheduler Thread Message] : However, the monitor "+monitor+" is not available");
                    System.out.println("[Scheduler Thread Message] : The monitor "+monitor+" is already in use by "+RuntimeEnvironment.monitorList.get(monitor).getName());
                    System.out.println("[Scheduler Thread Message] : The scheduler thread is going to select another thread to run");
                    pickNextRandomThread();
                } else {
                    System.out.println("[Scheduler Thread Message] : The monitor "+monitor+" is available");
                    monitorRequest.remove(randomElement, monitor);
                    System.out.println("[Scheduler Thread Message] : The request of "+randomElement.getName()+" to enter the monitor "+monitor+" is removed from the monitorRequest");
                    System.out.println("[Scheduler Thread Message] : "+randomElement.getName()+ " is selected to run");
                    synchronized (RuntimeEnvironment.locks.get(randomElement.getId())){
                        RuntimeEnvironment.locks.get(randomElement.getId()).notify();
                    }
                }
            } else {
                System.out.println("[Scheduler Thread Message] : "+randomElement.getName()+ " is selected to run");
                synchronized (RuntimeEnvironment.locks.get(randomElement.getId())){
                    RuntimeEnvironment.locks.get(randomElement.getId()).notify();
                }
            }
        } else if (RuntimeEnvironment.readyThreadList.size() == 1){
            System.out.println("[Scheduler Thread Message] : Only one thread is in the ready list");
            synchronized (RuntimeEnvironment.locks.get(RuntimeEnvironment.readyThreadList.get(0).getId())){
                System.out.println("[Scheduler Thread Message] : "+RuntimeEnvironment.readyThreadList.get(0).getName()+ " is selected to run");
                RuntimeEnvironment.locks.get(RuntimeEnvironment.readyThreadList.get(0).getId()).notify();
            }
        }else{
            System.out.println("[Scheduler Thread Message] : There is no thread in the ready list");
            System.out.println("[Scheduler Thread Message] : The scheduler thread is going to terminate");
            isFinished = true;
        }
        RuntimeEnvironment.threadWaitReq = null;
    }
}

