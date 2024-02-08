package org.example.runtime;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeEnvironment {

    // @threadCount is used to generate the name of the threads as "Thread-"+@threadCount++
    private static int threadCount = 1;
    // @threadWaitReq is used to store the thread that requested to wait
    public static Thread threadWaitReq = null;
    // @threadWaitReqLock is used to synchronize the access to @threadWaitReq
    public static Object threadWaitReqLock = new Object();
    // @threadStartReq is used to store the thread that requested to start
    public static Thread threadStartReq = null;
    // @locks is used to store the locks for the threads. The key is the id of the thread and the value is the lock object.
    // @locks.get(@thread.getId()) is used to synchronize @thread and the SchedulerThread which are running concurrently.
    public static Map<Long, Object> locks = new HashMap<>();
    // @createdThreadList is used to store the threads that are created
    public static List<Thread> createdThreadList = new ArrayList<>();
    // @readyThreadList is used to store the threads that are ready to run
    public static List<Thread> readyThreadList = new ArrayList<>();

    // The constructor is private to prevent the instantiation of the class
    private RuntimeEnvironment(){}

    /*
     * The @init method is used to initialize the Runtime Environment. It is called by the main method of the program.
     * By using the @init method, the main thread is added to the @createdThreadList and the @readyThreadList.
     * The @locks map is also initialized with the lock object of the main thread.
     * After the initialization, the main thread's name will be "Thread-1".
     * As this method is called by the main method in a single-threaded environment, there is no need to synchronize
     * the access to the @createdThreadList, @readyThreadList, and @locks.
     */
    public static void init(Thread main){
        System.out.println("[Runtime Environment Message] : The Runtime Environment has been deployed");
        Object lock = new Object();
        locks.put(main.getId(), lock);
        createdThreadList.add(main);
        readyThreadList.add(main);
        main.setName("Thread-"+threadCount++);
        System.out.println("[Runtime Environment Message] : "+main.getName() +" added to the createdThreadList of the Runtime Environment");
        System.out.println("[Runtime Environment Message] : "+main.getName() +" added to the readyThreadList of the Runtime Environment");
        System.out.println("[Runtime Environment Message] : "+main.getName() +" has the "+main.getState()+" state");
    }

    /*
     * The @addThread method is used to add the @thread to the Runtime Environment. It is called when a new object of the
     * Thread class(or castable to Thread class) is created.
     * By using the @addThread method, the @thread is added to the @createdThreadList.
     * After the addition, the @thread's name will be "Thread-"+@threadCount++.
     * The corresponding @lock object of the @thread is also added to the @locks map.
     * As this method is called by a single-threaded environment(This is guaranteed by the SchedulerThread),
     * there is no need to synchronize the access to the @createdThreadList and @locks.
     */
    public static void addThread(Thread thread){
        if (!createdThreadList.contains(thread)) {
            thread.setName("Thread-"+threadCount++);
            Object lock = new Object();
            locks.put(thread.getId(), lock);
            createdThreadList.add(thread);
            System.out.println("[Runtime Environment Message] : "+thread.getName()+ " added to the createdThreadList of the Runtime Environment");
            System.out.println("[Runtime Environment Message] : "+thread.getName()+ " has the "+thread.getState()+" state");
        } else {
            System.out.println("[Runtime Environment Message] : "+thread.getName()+":"+ thread.getId() +" is already in the createdThreadlist of the Runtime Environment");
        }
    }

    /*
     * The @threadStart method is used to add the @thread to the @readyThreadList of the Runtime Environment.
     * It is called when the start() method of the @thread is called.
     * By using the @threadStart method, the @thread is added to the @readyThreadList.
     * As the SchedulerThread needs to know that the @thread is ready to run, the @threadStartReq is assigned to the @thread.
     * Thus, the @currentThread will request to wait to hand over the control to the SchedulerThread for deciding which thread to run.
     * As this method is called by a single-threaded environment(This is guaranteed by the SchedulerThread),
     * there is no need to synchronize the access to the @readyThreadList.
     */
    public static void threadStart(Thread currentThread, Thread thread){
        if (createdThreadList.contains(thread) && !readyThreadList.contains(thread)) {
            System.out.println("[Runtime Environment Message] : "+thread.getName() +" requested to run the start() inside the "+currentThread.getName());
            readyThreadList.add(thread);
            System.out.println("[Runtime Environment Message] : "+thread.getName()+ " added to the readyThreadList of the Runtime Environment");
            threadStartReq = thread;
            waitRequest(currentThread);
        } else {
            System.out.println("[Runtime Environment Message] : "+thread.getName()+ " is not in the createdThreadList");
        }
    }

    /*
     * The @getPermission method is used to give the permission to the @thread to run.
     * It is called by the SchedulerThread to give the permission from the @thread to run.
     * For now, the @thread is the main thread of the program.
     */
    public static void getPermission(Thread thread){
        synchronized (locks.get(thread.getId())) {
            System.out.println("[Runtime Environment Message] : "+Thread.currentThread().getName()+ " got permitted to RUN");
        }
    }

    /*
     * The @waitRequest method is used by the @thread to request to wait.
     * It is called by the @thread for various reasons such as a new thread wants to start, read or write operations are requested.
     */
    public static void waitRequest(Thread thread){
        synchronized (locks.get(thread.getId())) {
            System.out.println("[Runtime Environment Message] : "+thread.getName()+ " has requested to WAIT");
            /*
             * There could be a race condition on @threadWaitReq in the following assignment statement. While the @thread is
             * changing the value of @threadWaitReq, SchedulerThread can read the value of @threadWaitReq.
             * To avoid from this situation, we used the @threadWaitReqLock object to synchronize the access to @threadWaitReq.
             */
            synchronized (threadWaitReqLock){
                threadWaitReq = thread;
            }
            try {
                locks.get(thread.getId()).wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }

    /*
     * The @initSchedulerThread method is used to initialize the SchedulerThread. It is called by the main method of the program.
     * By using the @initSchedulerThread method, the SchedulerThread is started and the main thread is requested to wait.
     * Since the SchedulerThread is running concurrently with the main thread, the @locks.get(@main.getId()) is used by the main thread,
     * and the SchedulerThread first calls the RuntimeEnvironment.getPermission(RuntimeEnvironment.createdThreadList.get(0)) to ensure that
     * the @threadWaitReq is race-free.
     */
    public static void initSchedulerThread(Thread main, Thread st){
        synchronized (locks.get(main.getId())){
            System.out.println("[Runtime Environment Message] : "+main.getName()+ " is calling the start() of the SchedulerThread");
            st.start();
            System.out.println("[Runtime Environment Message] : "+st.getName()+ " has the "+st.getState()+" state");
            System.out.println("[Runtime Environment Message] : "+main.getName()+ " has requested to WAIT");
            threadWaitReq = main;
            try {
                locks.get(main.getId()).wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /*
     * The @finishThreadRequest method is used by the @thread to request to finish.
     * By calling the @finishThreadRequest method, the @thread is removed from the @createdThreadList and @readyThreadList.
     */
    public static void finishThreadRequest(Thread thread){
        synchronized (locks.get(thread.getId())){
            System.out.println("[Runtime Environment Message] : "+thread.getName()+":"+ thread.getId() +" has requested to FINISH");

            /*
             * There could be a race condition on @threadWaitReq in the following assignment statement. While the @thread is
             * changing the value of @threadWaitReq, SchedulerThread can read the value of @threadWaitReq.
             * To avoid from this situation, we used the @threadWaitReqLock object to synchronize the access to @threadWaitReq.
             * Since the @SchedulerThread will wait on the @threadWaitReqLock, the createdThreadList and readyThreadList will be race-free.
             */
            synchronized (threadWaitReqLock){
                createdThreadList.remove(thread);
                readyThreadList.remove(thread);
                threadWaitReq = thread;
            }
        }
    }

    /*
     * The @readOperation method is used by the @thread when its next instruction is a read operation(GETFIELD).
     * It is called by the @thread to request to read the value of the @obj.@name which is from the @owner class.
     * The @descriptor is used to specify the type of the field.
     * After this request, the @thread will request to wait to hand over the control to the SchedulerThread for deciding which thread to run.
     */
    public static void ReadOperation(Object obj, Thread thread, String owner, String name, String descriptor){
        try {
            Class<?> clazz = Class.forName(owner.replace("/", "."));
            Object instance = clazz.cast(obj);
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(instance);
            System.out.println("[Runtime Environment Message] : "+thread.getName()+ " requested to read the value of "+owner+"."+name+"("+descriptor+") = "+value);
            waitRequest(thread);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * The @writeOperation method is used by the @thread when its next instruction is a write operation(PUTFIELD).
     * It is called by the @thread to request to write the @newVal to the @obj.@name with @oldValue which is from the @owner class.
     * The @descriptor is used to specify the type of the field.
     * After this request, the @thread will request to wait to hand over the control to the SchedulerThread for deciding which thread to run.
     */
    public static void WriteOperation(Object obj, Object newVal, Thread thread, String owner, String name, String descriptor){
        try {
            Class<?> clazz = Class.forName(owner.replace("/", "."));
            Object instance = clazz.cast(obj);
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object oldValue = field.get(instance);
            System.out.println("[Runtime Environment Message] : "+thread.getName() +" requested to write the ["+ newVal +"] value to "+owner+"."+name+"("+descriptor+") with old value of ["+oldValue+"]");
            waitRequest(thread);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void enterMonitor(Object lock, Thread thread){
        System.out.println("[Runtime Environment Message] : "+thread.getName() +" requested to MONITORENTER over the "+lock.toString());
    }

    public static void exitMonitor(Object lock, Thread thread){
        System.out.println("[Runtime Environment Message] : "+thread.getName() +" requested to MONITOREXIT over the "+lock.toString());
    }

    public static void acquiredLock(Object lock, Thread thread){
        System.out.println("[Runtime Environment Message] : "+thread.getName() +" acquired the "+lock.toString()+ " lock");
    }

    public static void releasedLock(Object lock, Thread thread){
        System.out.println("[Runtime Environment Message] : "+thread.getName() +" released the "+lock.toString()+ " lock");
    }

    //public static void ReadOperation(Object value, Thread thread, String owner, String name, String descriptor){
    //    System.out.println("[Runtime Environment Message] : "+thread.getName()+":"+ thread.getId() +" requested to read the value of "+owner+"."+name+"("+descriptor+")");
    //    System.out.println("[Runtime Environment Message] : "+thread.getName()+":"+ thread.getId() +" read the value of "+owner+"."+name+"("+descriptor+") = "+value);
    //}
}
