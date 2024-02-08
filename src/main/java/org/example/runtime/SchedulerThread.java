package org.example.runtime;
import java.util.Random;

public class SchedulerThread extends Thread{

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
             * If there is no new added thread into the @readyThreadList, the @SchedulerThread selects a random thread to run.
             * If there is only one thread in the @readyThreadList, the @SchedulerThread selects the only thread to run.
             * If there is no thread in the @readyThreadList, the @SchedulerThread terminates.
             */
            if (RuntimeEnvironment.threadStartReq != null){
                RuntimeEnvironment.threadWaitReq = null;
                Thread newThread = RuntimeEnvironment.threadStartReq;
                RuntimeEnvironment.threadStartReq = null;
                System.out.println("[Scheduler Thread Message] : "+newThread.getName()+" is selected to run for loading in the runtime environment");
                newThread.start();
            }else{
                if (RuntimeEnvironment.readyThreadList.size() > 1){
                    Random random = new Random();
                    int randomIndex = random.nextInt(RuntimeEnvironment.readyThreadList.size()); // Generate a random index
                    Thread randomElement = RuntimeEnvironment.readyThreadList.get(randomIndex); // Get the element at the random index
                    synchronized (RuntimeEnvironment.locks.get(randomElement.getId())){
                        System.out.println("[Scheduler Thread Message] : "+randomElement.getName()+ " is selected to run");
                        RuntimeEnvironment.locks.get(randomElement.getId()).notify();
                    }
                } else if (RuntimeEnvironment.readyThreadList.size() == 1){
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
        System.out.println("**********************************************************************************************");
        System.out.println("[*** The SchedulerThread requested to FINISH***]");
        System.out.println("**********************************************************************************************");
    }
}

