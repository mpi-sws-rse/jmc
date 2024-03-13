package org.example.runtime;
import org.example.checker.SearchStrategy;
import org.example.checker.StrategyType;
import org.example.checker.strategy.RandomStrategy;

import java.util.*;

public class SchedulerThread extends Thread{

    private SearchStrategy searchStrategy;



    public SchedulerThread() {
        createSearchStrategy();
    }


    private void createSearchStrategy() {
        if (RuntimeEnvironment.strategyType.equals(StrategyType.RANDOMSTRAREGY) ) {
            searchStrategy = new RandomStrategy();
        } else {
            assert (false) : "Unknown strategy type";
        }
    }

    /*
     * The following method is used to find the potential deadlock in the threads that are waiting to enter a monitor.
     * This method is implemented based on the following concept:
     * First, the algorithm computes the transitive closure of the (@monitorRequest \cup @monitorList) relation which is Asymmetric (Antisymmetric and Irreflexive).
     * Then, the algorithm checks whether the (@monitorRequest \cup @monitorList)^+ relation is irreflexive or not.
     * If the relation is irreflexive, there is no deadlock and the algorithm terminates.
     * Otherwise, there is a deadlock and the algorithm returns the threads that are in deadlock.
     */

    public boolean monitorsDeadlockDetection() {

        boolean isDeadlock = false;

        if (RuntimeEnvironment.monitorList.isEmpty()){
            System.out.println("[Scheduler Thread Message] : There is no need to check the deadlock");
            return isDeadlock;
        } else {
            System.out.println("[Scheduler Thread Message] : The deadlock detection phase is started");

            // The following map is used to store thread pairs that are in the transitive closure of the (@monitorRequest \cup @monitorList) relation.
            // To simplify the procedure, the algorithm ignores the monitor pairs.
            Map<Thread, Thread> threadClosure = new HashMap<>();


            // The following part computes the primitive closure of the (@monitorRequest \cup @monitorList) relation.
            for (Map.Entry<Thread, Object> entry : RuntimeEnvironment.monitorRequest.entrySet()){
                for (Map.Entry<Object, Thread> entry2 : RuntimeEnvironment.monitorList.entrySet()){
                    if (entry.getValue().equals(entry2.getKey())){
                        threadClosure.put(entry.getKey(), entry2.getValue());
                    }
                }
            }

            // The following part computes the complete transitive closure of the (@monitorRequest \cup @monitorList) relation.
            boolean addedNewPairs = true;
            while (addedNewPairs){
                addedNewPairs = false;
                for (Map.Entry<Thread, Thread> entry : threadClosure.entrySet()){
                    for (Map.Entry<Thread, Thread> entry2 : threadClosure.entrySet()){
                        if (entry.getValue().equals(entry2.getKey()) &&
                                !threadClosure.entrySet().stream().anyMatch(e -> e.getKey().equals(entry.getKey()) && e.getValue().equals(entry2.getValue()))){
                            threadClosure.put(entry.getKey(), entry2.getValue());
                            addedNewPairs = true;
                        }
                    }
                }
            }

            // The following part checks whether the (@monitorRequest \cup @monitorList)^+ relation is irreflexive or not.
            for (Map.Entry<Thread, Thread> entry : threadClosure.entrySet()){
                if (entry.getKey().equals(entry.getValue())){
                    isDeadlock = true;
                    break;
                }
            }

            return isDeadlock;
        }
    }

    public void run(){
        /*
         * The following expression is used for the synchronization of the @SchedulerThread and the main thread.
         * This is necessary for the @SchedulerThread to wait until the other thread (which is the main thread) waits.
         */
        RuntimeEnvironment.getPermission(RuntimeEnvironment.createdThreadList.get(0));

        System.out.println("**********************************************************************************************");
        System.out.println("[***** From this point on, the flow of the program is controlled by the SchedulerThread *****]");
        System.out.println("**********************************************************************************************");

        while (RuntimeEnvironment.executionFinished == false) {

            /*
             * The following while loop is used by the @SchedulerThread to wait until the only other running thread requests to wait.
             * There could be a race condition on @threadWaitReq in the following while loop. While the SchedulerThread is
             * reading the value of @threadWaitReq, the only other running thread can change the value of @threadWaitReq.
             * To avoid from this situation, we used the @threadWaitReqLock object to synchronize the access to @threadWaitReq.
             */
            while (true) {
                synchronized (RuntimeEnvironment.threadWaitReqLock) {
                    if (RuntimeEnvironment.threadWaitReq != null) {
                        break;
                    }
                    Thread.yield();
                }
            }

            /*
             * The following if statement is used to check whether the wait request is due to an assert violation or not.
             * If it is due to an assert fail, the @SchedulerThread terminates the program execution.
             */
            if (RuntimeEnvironment.assertFlag) {
                RuntimeEnvironment.executionFinished = true;
                System.out.println("**********************************************************************************************");
                System.out.println("[*** Assertion Fail ***]");
                System.out.println("[*** Number of execution iteration : " + RuntimeEnvironment.numOfExecutions + " ***]");
                System.out.println("[*** The SchedulerThread requested to FINISH***]");
                System.out.println("**********************************************************************************************");
                System.out.println("The executed events are : ");
                for (Object event : RuntimeEnvironment.randomEventsRecord){
                    System.out.println(event);
                }
                System.exit(0);
            }

            System.out.println("[Scheduler Thread Message] : All threads are in waiting state");

            /*
             * The following while loop is used by the @SchedulerThread to wait until the state of the only other running thread
             * changes to the WAIT state.
             */
            synchronized (RuntimeEnvironment.locks.get(RuntimeEnvironment.threadIdMap.get(RuntimeEnvironment.threadWaitReq.getId()))) {
                System.out.println("[Scheduler Thread Message] : Scheduling phase begins");
            }
            eventHandler();
        }
        System.out.println("**********************************************************************************************");
        System.out.println("[*** The SchedulerThread requested to FINISH***]");
        System.out.println("**********************************************************************************************");
        // The following expression is used to notify the main thread to continue the execution to finish the current execution iteration.
        synchronized (RuntimeEnvironment.locks.get((long) 1)){
            RuntimeEnvironment.locks.get((long) 1).notify();
        }
    }

    private void pickNextThread(){
        Thread nextThread = searchStrategy.pickNextRandomThread();
        if (nextThread != null){
            notifyThread(nextThread);
        } else {
            RuntimeEnvironment.executionFinished = true;
        }
    }

    private void notifyThread(Thread thread) {
        System.out.println("[Scheduler Thread Message] : Thread-" + RuntimeEnvironment.threadIdMap.get(thread.getId()) + " is selected to run");
        synchronized (RuntimeEnvironment.locks.get(RuntimeEnvironment.threadIdMap.get(thread.getId()))){
            RuntimeEnvironment.locks.get(RuntimeEnvironment.threadIdMap.get(thread.getId())).notify();
        }
    }

    private void eventHandler() {
        /*
         * The following if-else statement is used to select the next thread to run.
         * If there is a new added thread into the @readyThreadList, the @SchedulerThread selects the new thread to run.
         * This is necessary for registering the new thread in the runtime environment. Since the @SchedulerThread use
         * the notify method to run the selected thread, the new added thread needs to be started and waited on its
         * @lock object.
         * If there is a monitor request, the @SchedulerThread adds the thread and the monitor into the @monitorRequest
         * and selects a random thread to run. Moreover, it checks whether there is a deadlock between the threads in
         * using the monitors or not. If there is a deadlock, the @SchedulerThread terminates. Otherwise, the
         * @SchedulerThread selects a random thread to run.
         * If there is no new added thread and no monitor request, the @SchedulerThread selects a random thread to run.
         */
        if (RuntimeEnvironment.threadStartReq != null) {
            startEventHandler();
        } else if (RuntimeEnvironment.threadEnterMonitorReq != null) {
            enterMonitorRequestHandler();
        } else if (RuntimeEnvironment.threadExitMonitorReq != null) {
            exitMonitorRequestHandler();
        } else if (RuntimeEnvironment.threadJoinReq != null) {
            joinRequestHandler();
        } else if (RuntimeEnvironment.writeEventReq != null) {
            writeRequestHandler();
        } else if (RuntimeEnvironment.readEventReq != null) {
            readRequestHandler();
        } else {
            RuntimeEnvironment.threadWaitReq = null;
            pickNextThread();
        }
    }

    private void startEventHandler() {
        searchStrategy.nextStartEvent(RuntimeEnvironment.threadStartReq, RuntimeEnvironment.threadWaitReq);
        RuntimeEnvironment.threadWaitReq = null;
        Thread thread = RuntimeEnvironment.threadStartReq;
        RuntimeEnvironment.threadStartReq = null;
        System.out.println("[Scheduler Thread Message] : Thread-" + RuntimeEnvironment.threadIdMap.get(thread.getId()) +
                " is selected to run for loading in the runtime environment");
        thread.start();
    }

    /*
     * The following method is used by the @SchedulerThread to handle the monitor request of a thread.
     * It adds the thread and the monitor into the @monitorRequest and checks whether there is a deadlock between the
     * threads in using the monitors or not. If there is a deadlock, the @SchedulerThread terminates. Otherwise, the
     * @SchedulerThread selects a random thread to run.
     */
    public void enterMonitorRequestHandler() {
        RuntimeEnvironment.monitorRequest.put(RuntimeEnvironment.threadEnterMonitorReq, RuntimeEnvironment.objectEnterMonitorReq);
        RuntimeEnvironment.threadEnterMonitorReq = null;
        RuntimeEnvironment.objectEnterMonitorReq = null;
        if (monitorsDeadlockDetection()) {
            System.out.println("[Scheduler Thread Message] : There is a deadlock between the threads in using the monitors");
            RuntimeEnvironment.executionFinished = true;
        } else {
            System.out.println("[Scheduler Thread Message] : There is no deadlock between the threads in using the monitors");
            RuntimeEnvironment.threadWaitReq = null;
            pickNextThread();
        }
    }

    public void exitMonitorRequestHandler() {
        searchStrategy.nextExitMonitorEvent(RuntimeEnvironment.threadExitMonitorReq, RuntimeEnvironment.objectExitMonitorReq);
        RuntimeEnvironment.threadExitMonitorReq = null;
        RuntimeEnvironment.objectExitMonitorReq = null;
        RuntimeEnvironment.threadWaitReq = null;
        pickNextThread();
    }

    public void joinRequestHandler() {
        RuntimeEnvironment.joinRequest.put(RuntimeEnvironment.threadJoinReq, RuntimeEnvironment.threadJoinRes);
        RuntimeEnvironment.threadJoinReq = null;
        RuntimeEnvironment.threadJoinRes = null;
        RuntimeEnvironment.threadWaitReq = null;
        pickNextThread();
    }

    public void readRequestHandler() {
        searchStrategy.nextReadEvent(RuntimeEnvironment.readEventReq);
        RuntimeEnvironment.readEventReq = null;
        Thread thread = RuntimeEnvironment.threadWaitReq;
        RuntimeEnvironment.threadWaitReq = null;
        notifyThread(thread);
    }

    public void writeRequestHandler() {
        searchStrategy.nextWriteEvent(RuntimeEnvironment.writeEventReq);
        RuntimeEnvironment.writeEventReq = null;
        Thread thread = RuntimeEnvironment.threadWaitReq;
        RuntimeEnvironment.threadWaitReq = null;
        notifyThread(thread);
    }
}