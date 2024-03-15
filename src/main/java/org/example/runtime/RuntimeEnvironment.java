package org.example.runtime;

import org.example.checker.CheckerConfiguration;
import executionGraph.ExecutionGraph;
import org.example.checker.StrategyType;
import programStructure.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.util.*;

/**
 * The RuntimeEnvironment class is a singleton that manages the execution state of a multithreaded program under test.
 * It tracks and controls various aspects of the program's execution, including thread creation, synchronization,
 * termination, and read/write operations on shared resources. It also handles events like thread start, join,
 * monitor enter/exit, read/write operations, and assertion failures. The class provides functionality to load
 * a CheckerConfiguration object from a file, which is used to configure the execution of the program under test.
 * It also provides methods to control the execution of the program, including setting a random seed, initializing
 * the scheduler thread, and terminating the execution. The class is designed to be used in a single-threaded
 * environment, where the SchedulerThread guarantees the sequential execution of operations.
 */
public class RuntimeEnvironment {

    /**
     * @property {@link #config} is used to store the {@link CheckerConfiguration} object which is loaded from the
     * config.obj file.
     */
    private static CheckerConfiguration config = null;

    /**
     * @property {@link #threadCount} is used to store the number of threads that are created in the program under test.
     */
    private static int threadCount = 1;

    /**
     * @property {@link #threadWaitReq} is used to store the thread that requested to wait
     */
    public static Thread threadWaitReq = null;

    /**
     * @property {@link #threadWaitReqLock} is used to store the lock object for the {@link #threadWaitReq}
     * It is used to synchronize the access to {@link #threadWaitReq}
     */
    public static final Object threadWaitReqLock = new Object();

    /**
     * @property {@link #threadStartReq} is used to store the thread that requested to start
     */
    public static Thread threadStartReq = null;

    /**
     * @property {@link #threadEnterMonitorReq} is used to store the thread that requested to enter the monitor
     */
    public static Thread threadEnterMonitorReq = null;

    /**
     * @property {@link #threadExitMonitorReq} is used to store the thread that requested to exit the monitor
     */
    public static Thread threadExitMonitorReq = null;
    /**
     * @property {@link #objectEnterMonitorReq} is used to store the object that the {@link #threadEnterMonitorReq}
     * requested to enter the monitor
     */
    public static Object objectEnterMonitorReq = null;

    /**
     * @property {@link #objectExitMonitorReq} is used to store the object that the {@link #threadExitMonitorReq}
     * requested to exit the monitor
     */
    public static Object objectExitMonitorReq = null;

    /**
     * @property {@link #threadJoinReq} is used to store the thread that requested to join to the {@link #threadJoinRes}
     */
    public static Thread threadJoinReq = null;

    /**
     * @property {@link #threadJoinRes} is used to store the thread that the {@link #threadJoinReq} requested
     * to join over it.
     */
    public static Thread threadJoinRes = null;

    /**
     * @property {@link #assertFlag} is used to inform the {@link SchedulerThread} object that an assert statement is
     * executed, and it failed. Thus, the {@link SchedulerThread} object will terminate the program execution.
     */
    public static boolean assertFlag = false;

    /**
     * @property {@link #locks} is used to store the locks for the threads. The key is the thread id and the value is
     * the lock object.
     */
    public static Map<Long, Object> locks = new HashMap<>();

    /**
     * @property {@link #createdThreadList} is used to store the threads that are created in the program under test.
     */
    public static List<Thread> createdThreadList = new ArrayList<>();

    /**
     * @property {@link #readyThreadList} is used to store the threads that are ready to run in the program under test.
     */
    public static List<Thread> readyThreadList = new ArrayList<>();

    /**
     * @property {@link #monitorList} is used to store the monitor objects which are acquired by the threads.
     */
    public static Map<Object, Thread> monitorList = new HashMap<>();

    /**
     * @property {@link #monitorRequest} is used to store the threads that are waiting to enter a monitor.
     * <p>
     * The {@link SchedulerThread} uses this map in scheduling phase in order to select the next thread to run which
     * will not cause a deadlock. When a thread is selected to run, and it is waiting to enter a monitor, the
     * {@link SchedulerThread} checks {@link #monitorList} in {@link RuntimeEnvironment} to see whether the monitor is
     * available or not. If the monitor is available, the {@link SchedulerThread} selects the thread to run and removes
     * the thread from {@link #monitorRequest}. Otherwise, the {@link SchedulerThread} selects another thread to run.
     */
    public static Map<Thread, Object> monitorRequest = new HashMap<>();

    /**
     * @property {@link #joinRequest} is used to store the threads that are waiting to join to another thread.
     * <p>
     * The {@link SchedulerThread} uses this map in scheduling phase in order to select the next thread to run which
     * will not cause a deadlock. When a thread is selected to run, and it is waiting to join to another thread, the
     * {@link SchedulerThread} checks {@link #createdThreadList} in {@link RuntimeEnvironment} to see whether the
     * thread that the selected thread wants to join is still alive or not. If the thread is finished, the
     * {@link SchedulerThread} selects the thread to run and removes it from {@link #joinRequest}. Otherwise, the
     * {@link SchedulerThread} selects another thread to run.
     */
    public static Map<Thread, Thread> joinRequest = new HashMap<>();

    /**
     * @property {@link #randomEventsRecord} is used to store the events that are executed by the threads in the
     * program under test using the {@link org.example.checker.strategy.RandomStrategy}.
     */
    public static List<Event> randomEventsRecord;

    // TODO() : Check if it is necessary to have it.
    /**
     * @property {@link #mcThreads} is used to store the threads in a proper format to be used by the model checker.
     */
    public static Map<Integer, JMCThread> mcThreads = new HashMap<>();

    /**
     * @property {@link #mcThreadSerialNumber} is used to store the number of seen events for each thread.
     * This number is used to generate the serial number for the events of the threads.
     */
    public static Map<Integer, Integer> mcThreadSerialNumber = new HashMap<>();

    // TODO() : Check if it is necessary to have it.
    /**
     * @property {@link #mcGraphs} is used to store the execution graphs which are generated by the model checker.
     */
    public static List<ExecutionGraph> mcGraphs = new ArrayList<>();

    // TODO() : Check if it is necessary to have it.
    /**
     * @property {@link #tempMCGraphs} is a temp for {@link #mcGraphs}
     */
    public static List<ExecutionGraph> tempMCGraphs = new ArrayList<>();

    /**
     * @property {@link #writeEventReq} is used to store the {@link WriteEvent} that a thread will execute.
     */
    public static WriteEvent writeEventReq;

    /**
     * @property {@link #readEventReq} is used to store the {@link ReadEvent} that a thread will execute.
     */
    public static ReadEvent readEventReq;

    /**
     * @property {@link #numOfGraphs} is used to store the number of the execution graphs that are generated by the
     * model checker at the end of each iteration.
     */
    public static int numOfGraphs = 0;

    /**
     * @property {@link #threadIdMap} is used to store the mapping between each thread id generated by JVM and the
     * thread id generated by the {@link RuntimeEnvironment}.
     */
    public static Map<Long, Long> threadIdMap = new HashMap<>();

    /**
     * @property {@link #isFinished} is used to indicate that the program execution is finished.
     */
    public static boolean isFinished = false;

    /**
     * @property {@link #strategyType} is used to store the strategy type that is used by the {@link #RuntimeEnvironment}
     * and {@link SchedulerThread}. The supported strategy types are: {@link StrategyType#RANDOMSTRAREGY} and
     * {@link StrategyType#TRUSTSTRATEGY}.
     */
    public static StrategyType strategyType;

    /**
     * @property {@link #maxNumOfExecutions} is used to store the maximum number of the executions that the
     * {@link SchedulerThread} will explore.
     */
    public static int maxNumOfExecutions = 100;

    /**
     * @property {@link #numOfExecutions} is used to count the number of the executions that the program has been tested.
     */
    public static int numOfExecutions = 0;

    /**
     * @property {@link #executionFinished} is used to indicate that the execution is finished.
     */
    public static boolean executionFinished = false;

    /**
     * @property {@link #deadlockHappened} is used to indicate that a deadlock happened.
     */
    public static boolean deadlockHappened = false;

    /**
     * @property {@link #allExecutionsFinished} is used to indicate that all executions are finished.
     */
    public static boolean allExecutionsFinished = false;

    /**
     * @property {@link #seed} is used to store the random seed that is used by the a search strategy object.
     */
    public static long seed = 0;

    /**
     * @property {@link #suspendedThreads} is used to store the threads that are suspended in the program under test.
     */
    public static List<Thread> suspendedThreads = new ArrayList<>();

    /**
     * The constructor is private to prevent the instantiation of the class
     */
    private RuntimeEnvironment() {
    }

    public static void setRandomSeed(long seed) {
        // rng.setSeed(seed);
    }

    /**
     * Initializes the {@link RuntimeEnvironment}.
     * <p>
     * This method is invoked by the main method of the program under test. As it is called in a single-threaded environment,
     * there is no need to synchronize the access to the {@link #createdThreadList}, {@link #readyThreadList}, and {@link #locks}.
     *
     * @param thread The main thread of the program under test.
     */
    public static void init(Thread thread) {
        System.out.println("[Runtime Environment Message] : The RuntimeEnvironment has been deployed");
        numOfExecutions++;
        System.out.println("[Runtime Environment Message] : The number of executions is " + numOfExecutions);
        loadConfig();
        System.out.println("[Runtime Environment Message] : The CheckerConfiguration has been loaded");
        threadIdMap.put(thread.getId(), (long) threadCount);
        thread.setName("Thread-" + threadCount++);
        System.out.println(
                "[Runtime Environment Message] : " + threadIdMap.get(thread.getId()) +
                        " added to the createdThreadList of the Runtime Environment"
        );
        Object lock = new Object();
        locks.put(threadIdMap.get(thread.getId()), lock);
        createdThreadList.add(thread);
        readyThreadList.add(thread);
        JMCThread trd = new JMCThread(threadIdMap.get(thread.getId()).intValue(), new ArrayList<>());
        mcThreads.put(threadIdMap.get(thread.getId()).intValue(), trd);
        mcThreadSerialNumber.put(threadIdMap.get(thread.getId()).intValue(), 0);
        System.out.println("[Runtime Environment Message] : " + thread.getName() + " added to the createdThreadList of the" +
                " Runtime Environment");
        System.out.println("[Runtime Environment Message] : " + thread.getName() + " added to the readyThreadList of the " +
                "Runtime Environment");
        System.out.println("[Runtime Environment Message] : " + thread.getName() + " has the " + thread.getState() + " state");
    }

    /**
     * Loads the {@link CheckerConfiguration} object from the config.obj file and assigns it to {@link #config}.
     * <p>
     * This method is invoked by the {@link #init(Thread)} method.
     *
     * @throws FileNotFoundException  if the config.obj file is not found.
     * @throws IOException            if an I/O error occurs while reading the config.obj file.
     * @throws ClassNotFoundException if the {@link CheckerConfiguration} class is not found.
     */
    public static void loadConfig() {
        try {
            FileInputStream fileIn = new FileInputStream("src/main/resources/config/config.obj");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            config = (CheckerConfiguration) in.readObject();
            assert (config != null) : "The CheckerConfiguration is null";
            System.out.println(
                    "[Runtime Environment Message] : The verbose mode is " + config.verbose +
                            " , the random seed is " + config.seed +
                            " , the maximum events per execution is " + config.maxEventsPerExecution +
                            " , and the maximum iteration is : " + config.maxIterations
            );
            in.close();
            fileIn.close();
            readConfig();
        } catch (FileNotFoundException f) {
            f.printStackTrace();
            assert (false) : "FileNotFoundException in loadConfig method";
        } catch (IOException i) {
            i.printStackTrace();
            assert (false) : "IOException in loadConfig method";
        } catch (ClassNotFoundException c) {
            c.printStackTrace();
            assert (false) : "Class not found in loadConfig method";
        }
    }

    private static void readConfig() {
        maxNumOfExecutions = config.maxIterations;
        strategyType = config.strategyType;
        seed = config.seed;
    }

    /**
     * Adds a new thread to the {@link RuntimeEnvironment}.
     * <p>
     * This method is invoked when a new instance of the Thread class (or a class castable to Thread) is created.
     * The new thread is added to the {@link #createdThreadList}.
     * <p>
     * The method assigns a unique name to the thread in the format "Thread-"+threadCount and increments the {@link #threadCount}.
     * It also creates a corresponding lock object for the thread and adds it to the {@link #locks} map.
     * <p>
     * The method also creates a new {@link JMCThread} object for the thread and adds it to the {@link #mcThreads} map.
     * It also initializes the serial number of the thread to 0 and adds it to the {@link #mcThreadSerialNumber} map.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is no need
     * to synchronize access to the {@link #createdThreadList} and {@link #locks}.
     * <p>
     * If the thread is already in the {@link #createdThreadList}, a message is printed to the console and no further
     * action is taken.
     *
     * @param thread The thread to be added to the {@link #RuntimeEnvironment}.
     *               <p>
     */
    public static void addThread(Thread thread) {
        threadIdMap.put(thread.getId(), (long) threadCount);
        if (!createdThreadList.contains(thread)) {
            thread.setName("Thread-" + threadCount++);
            Object lock = new Object();
            locks.put(threadIdMap.get(thread.getId()), lock);
            createdThreadList.add(thread);
            JMCThread trd = new JMCThread(threadIdMap.get(thread.getId()).intValue(), new ArrayList<>());
            mcThreads.put(threadIdMap.get(thread.getId()).intValue(), trd);
            mcThreadSerialNumber.put(threadIdMap.get(thread.getId()).intValue(), 0);
            System.out.println(
                    "[Runtime Environment Message] : " + thread.getName() + " added to the createdThreadList " +
                            "of the Runtime Environment"
            );
            System.out.println(
                    "[Runtime Environment Message] : " + thread.getName() + " has the " + thread.getState() + " state"
            );
        } else {
            System.out.println(
                    "[Runtime Environment Message] : " + thread.getName() +
                            " is already in the createdThreadList of the RuntimeEnvironment"
            );
        }
    }

    /**
     * Adds a new thread to the {@link #readyThreadList} of the {@link RuntimeEnvironment}.
     * <p>
     * This method is invoked when a thread in the program under test calls the start() method. The thread is then added
     * to the {@link #readyThreadList}.
     * <p>
     * The method assigns the thread to the {@link #threadStartReq} to inform the {@link SchedulerThread} that the thread
     * is ready to run. It then calls the {@link #waitRequest(Thread)} method to make the current thread wait and hand
     * over control to the {@link SchedulerThread} for deciding which thread to run next.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #readyThreadList}.
     * <p>
     * If the thread is not in the {@link #createdThreadList} or is already in the {@link #readyThreadList}, a message
     * is printed to the console and no further action is taken.
     *
     * @param thread        The thread to be added to the {@link #readyThreadList}.
     * @param currentThread The current thread that is running.
     */
    public static void threadStart(Thread thread, Thread currentThread) {
        if (createdThreadList.contains(thread) && !readyThreadList.contains(thread)) {
            System.out.println(
                    "[Runtime Environment Message] : " + thread.getName() + " requested to run the start()" +
                            " inside the " + currentThread.getName()
            );
            readyThreadList.add(thread);
            System.out.println(
                    "[Runtime Environment Message] : " + thread.getName() + " added to the readyThreadList " +
                            "of the Runtime Environment"
            );
            threadStartReq = thread;
            waitRequest(currentThread);
        } else {
            System.out.println("[Runtime Environment Message] : " + thread.getName() + " is not in the createdThreadList");
        }
    }

    /**
     * Handles a join request from one thread to another.
     * <p>
     * This method is invoked when a thread in the program under test calls the join() method on another thread. The
     * calling thread is then set as the {@link #threadJoinReq} and the thread it wishes to join is set as the
     * {@link #threadJoinRes}.
     * <p>
     * The method then calls the {@link #waitRequest(Thread)} method to make the calling thread wait and hand over
     * control to the {@link SchedulerThread} for deciding which thread to run next.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadJoinReq} and {@link #threadJoinRes}.
     *
     * @param threadReq The thread that requested to join over the threadRes.
     * @param threadRes The thread that the threadReq requested to join over it.
     */
    public static void threadJoin(Thread threadReq, Thread threadRes) {
        System.out.println(
                "[Runtime Environment Message] : " + threadReq.getName() + " requested to join over the " +
                        threadRes.getName()
        );
        threadJoinReq = threadReq;
        threadJoinRes = threadRes;
        waitRequest(threadReq);
    }

    // TODO() : Check if it is necessary to have it.

    /**
     * Requests permission for a thread to run.
     * <p>
     * This method is invoked by the {@link SchedulerThread} to request permission from a thread to execute. It ensures
     * that access to {@link #threadWaitReq} is synchronized and race-free.
     * <p>
     * The method uses the lock associated with the thread (retrieved from the {@link #locks} map) to synchronize the
     * access. This ensures that the thread can safely run without any race conditions.
     *
     * @param thread The thread for which permission to run is requested.
     */
    public static void getPermission(Thread thread) {
        synchronized (locks.get(threadIdMap.get(thread.getId()))) {
            System.out.println("[Runtime Environment Message] : " + Thread.currentThread().getName() + " got permitted to RUN");
        }
    }

    /**
     * Handles a wait request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test needs to wait. The waiting thread could be due to
     * various reasons such as a new thread wanting to start, read or write operations being requested, monitor enter
     * operation being requested, join operation being requested, or an assert statement failing.
     * <p>
     * The method assigns the waiting thread to the {@link #threadWaitReq} to inform the {@link SchedulerThread} that
     * the thread is waiting. It then makes the current thread wait by calling the wait() method on the lock associated
     * with the thread (retrieved from the {@link #locks} map). This hands over control to the {@link SchedulerThread}
     * for deciding which thread to run next.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadWaitReq}.
     * <p>
     *
     * @param thread The thread that requested to wait.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    public static void waitRequest(Thread thread) {
        synchronized (locks.get(threadIdMap.get(thread.getId()))) {
            System.out.println("[Runtime Environment Message] : " + thread.getName() + " has requested to WAIT");
            /*
              There could be a race condition on {@link #threadWaitReq} in the following assignment statement.
              While the thread is changing the value of {@link #threadWaitReq}, {@link SchedulerThread} can read the
              value of {@link #threadWaitReq}. To avoid from this situation, we used the {@link #threadWaitReqLock}
              object to synchronize the access to {@link #threadWaitReq}.
             */
            synchronized (threadWaitReqLock) {
                threadWaitReq = thread;
            }
            try {
                locks.get(threadIdMap.get(thread.getId())).wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                assert (false) : "InterruptedException in waitRequest method";
            }

        }
    }

    /**
     * Initializes the {@link SchedulerThread} and makes the main thread wait.
     * <p>
     * This method is invoked by the main method of the program under test. It starts the {@link SchedulerThread} and
     * then makes the main thread wait. This allows the {@link SchedulerThread} to take control of the execution order
     * of the threads in the program.
     * <p>
     * The method first starts the {@link SchedulerThread} and then assigns the main thread to the {@link #threadWaitReq}
     * to inform the {@link SchedulerThread} that the main thread is waiting. It then makes the main thread wait by
     * calling the wait() method on the lock associated with the main thread (retrieved from the {@link #locks} map).
     * This hands over control to the {@link SchedulerThread} for deciding which thread to run next.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadWaitReq}.
     *
     * @param main The main thread of the program under test.
     * @param st   The {@link SchedulerThread} of the program under test.
     * @throws InterruptedException if the main thread is interrupted while waiting.
     */
    public static void initSchedulerThread(Thread main, Thread st) {
        synchronized (locks.get(threadIdMap.get(main.getId()))) {
            System.out.println(
                    "[Runtime Environment Message] : Thread-" + threadIdMap.get(main.getId()) + " is calling " +
                            "the start() of the SchedulerThread"
            );
            st.start();
            System.out.println("[Runtime Environment Message] : " + st.getName() + " has the " + st.getState() + " state");
            System.out.println("[Runtime Environment Message] : " + main.getName() + " has requested to WAIT");
            threadWaitReq = main;
            try {
                locks.get(threadIdMap.get(main.getId())).wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                assert (false) : "InterruptedException in initSchedulerThread method";
            }
        }
    }

    /**
     * Handles a finish request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test has completed its execution and needs to be
     * removed from the active threads list. The thread is then removed from both the {@link #createdThreadList} and
     * {@link #readyThreadList}.
     * <p>
     * The method checks if the thread is the main thread. If it is, it sets the {@link #isFinished} flag to true, makes
     * the main thread wait by calling the {@link #waitRequest(Thread)} method, and then calls the
     * {@link #terminateExecution()} method to end the program execution.
     * <p>
     * If the thread is not the main thread, it sets the {@link #isFinished} flag to true and assigns the thread to
     * the {@link #threadWaitReq} to inform the {@link SchedulerThread} that the thread has finished its execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #createdThreadList}, {@link #readyThreadList}, and {@link #threadWaitReq}.
     *
     * @param thread The thread that requested to finish.
     */
    public static void finishThreadRequest(Thread thread) {
        synchronized (locks.get(threadIdMap.get(thread.getId()))) {
            System.out.println("[Runtime Environment Message] : " + thread.getName() + " has requested to FINISH");
            createdThreadList.remove(thread);
            readyThreadList.remove(thread);
            isFinished = true;
            if (threadIdMap.get(thread.getId()) == 1) {
                waitRequest(thread);
                terminateExecution();
            } else {
                /*
                  There could be a race condition on {@link #threadWaitReq} in the following assignment statement. While
                  the thread is changing the value of {@link #threadWaitReq}, {@link SchedulerThread} can read the value
                  of {@link #threadWaitReq}. To avoid from this situation, we used the {@link #threadWaitReqLock} object
                  to synchronize the access to {@link #threadWaitReq}. Since the {@link SchedulerThread} will wait on the
                  {@link #threadWaitReqLock}, the {@link #createdThreadList} and {@link #readyThreadList} will be race-free.
                 */
                synchronized (threadWaitReqLock) {
                    threadWaitReq = thread;
                }
            }
        }
    }

    //TODO() : Change the way of termination ( using the System.exit(0) method is not good)

    /**
     * Terminates the execution of the program under test and resets the {@link RuntimeEnvironment}.
     * <p>
     * This method is invoked when the main thread of the program under test has completed its execution. It first
     * resets the {@link RuntimeEnvironment} by calling the {@link #resetRuntimeEnvironment()} method. Then, based on the
     * strategy type and the states of the {@link RuntimeEnvironment}, it decides whether to terminate the life-cycle
     * process of testing or continue the testing with the next iteration.
     * <p>
     * If the {@link #mcGraphs} list is empty and the {@link #strategyType} is {@link StrategyType#TRUSTSTRATEGY}, the
     * method terminates the program execution.
     * If the {@link #numOfExecutions} is less than the {@link #maxNumOfExecutions} and the {@link #strategyType}
     * is {@link StrategyType#RANDOMSTRAREGY}, the method terminates the program execution.
     * If the {@link #numOfExecutions} is equal to the {@link #maxNumOfExecutions} and the {@link #strategyType}
     * is {@link StrategyType#RANDOMSTRAREGY}, the method terminates the program execution.
     * <p>
     * The termination of the program execution is done by calling the System.exit(0) method.
     */
    private static void terminateExecution() {
        if (deadlockHappened) {
            System.out.println("[Runtime Environment Message] : The deadlock happened");
            System.exit(0);
        } else if (allExecutionsFinished) {
            System.out.println("[Runtime Environment Message] : The " + numOfExecutions + " execution is finished");
            System.out.println("[Runtime Environment Message] : The maximum number of the executions is reached");
            System.exit(0);
        } else {
            System.out.println("[Runtime Environment Message] : The " + numOfExecutions + " execution is finished");
            resetRuntimeEnvironment();
        }
    }

    /**
     * Handles a read operation request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a read operation ({@code GETFIELD}).
     * The thread is requesting to read the value of a field from an object.
     * <p>
     * The method first creates a {@link Location} based on the provided object, owner class, field name, and descriptor.
     * It then checks if the {@link Location} is of a primitive type. If it is, a {@link ReadEvent} is created for the thread and the
     * thread are made to wait by calling the {@link #waitRequest(Thread)} method. This hands over control to the
     * {@link SchedulerThread} for deciding which thread to run next.
     * <p>
     * If the {@link Location} is not of a primitive type, a message is printed to the console indicating that the Model Checker
     * will not consider this operation as it does not involve a primitive type.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #writeEventReq}.
     *
     * @param obj        The object that the thread wants to read the value of the field from it.
     * @param thread     The thread that wants to read the value of the field.
     * @param owner      The class that the field is from it.
     * @param name       The name of the field.
     * @param descriptor The type of the field.
     * @throws NullPointerException if the {@link Location} is null.
     */
    public static void readOperation(Object obj, Thread thread, String owner, String name, String descriptor) {
        Location location = createLocation(obj, owner, name, descriptor);
        System.out.println(
                "[Runtime Environment Message] : " + threadIdMap.get(thread.getId()) + " requested to " +
                        "read the value of " + owner + "." + name + "(" + descriptor + ") = " + Objects.requireNonNull(location).getValue()
        );
        if (location.isPrimitive()) {
            readEventReq = createReadEvent(thread, location);
            waitRequest(thread);
        } else {
            System.out.println(
                    "[Runtime Environment Message] : Since the value is not a primitive type, the Model" +
                            "Checker will not care about it"
            );
        }
    }

    /**
     * Handles a write operation request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a write operation ({@code PUTFIELD}).
     * The thread is requesting to write a new value to a field of an object.
     * <p>
     * The method first creates a {@link Location} based on the provided object, owner class, field name, and descriptor.
     * It then checks if the {@link Location} is of a primitive type. If it is, a {@link WriteEvent} is created for the thread
     * and the thread are made to wait by calling the {@link #waitRequest(Thread)} method. This hands over control to the
     * {@link SchedulerThread} for deciding which thread to run next.
     * <p>
     * If the {@link Location} is not of a primitive type, a message is printed to the console indicating that the Model Checker
     * will not consider this operation as it does not involve a primitive type.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is no need
     * to synchronize access to the {@link #writeEventReq}.
     *
     * @param obj        The object that the thread wants to write the new value to its field.
     * @param newVal     The new value that the thread wants to write to the field.
     * @param thread     The thread that wants to write the new value to the field.
     * @param owner      The class that the field belongs to.
     * @param name       The name of the field.
     * @param descriptor The type of the field.
     * @throws NullPointerException if the {@link Location} is null.
     */
    public static void writeOperation(Object obj, Object newVal, Thread thread, String owner, String name,
                                      String descriptor) {
        Location location = createLocation(obj, owner, name, descriptor);
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "write the [" + newVal + "] value to " + owner + "." + name + "(" + descriptor + ") with old value " +
                        "of [" + Objects.requireNonNull(location).getValue() + "]"
        );
        if (location.isPrimitive()) {
            writeEventReq = createWriteEvent(thread, location, newVal);
            waitRequest(thread);
        } else {
            System.out.println(
                    "[Runtime Environment Message] : Since the value is not a primitive type, the Model " +
                            "Checker will not care about it"
            );
        }
    }

    /**
     * Handles a monitor entry request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a {@code MONITORENTER} operation.
     * The thread is requesting to enter the monitor of a lock.
     * <p>
     * The method assigns the requesting thread to the {@link #threadEnterMonitorReq} and the lock to the
     * {@link #objectEnterMonitorReq} to inform the {@link SchedulerThread} that the thread has requested to enter the
     * monitor. It then makes the requesting thread wait by calling the {@link #waitRequest(Thread)} method. This hands
     * over control to the {@link SchedulerThread} for deciding which thread to run next.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadEnterMonitorReq} and {@link #objectEnterMonitorReq}.
     *
     * @param lock   The lock that the thread wishes to enter.
     * @param thread The thread that wishes to enter the lock.
     */
    public static void enterMonitor(Object lock, Thread thread) {
        System.out.println(
                "[Runtime Environment Message] : " + thread.getName() + " requested to MONITORENTER over " +
                        "the " + lock.toString()
        );
        threadEnterMonitorReq = thread;
        objectEnterMonitorReq = lock;
        waitRequest(thread);
    }

    // TODO() :  It is called by the @thread to request to exit the monitor of the @lock.
    // TODO() : After this request, the @thread will request to wait to hand over the control to the SchedulerThread
    //  for deciding which thread to run.

    /**
     * Handles a monitor exit request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a {@code MONITOREXIT} operation.
     * The thread is requesting to exit the monitor of a lock.
     * <p>
     * The method logs the request of the thread to exit the monitor of the lock. However, it does not perform any
     * action or change any state in the {@link RuntimeEnvironment}. The actual exit operation is handled by the JVM.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to any shared resources.
     *
     * @param lock   The lock that the thread wishes to exit.
     * @param thread The thread that wishes to exit the lock.
     */
    public static void exitMonitor(Object lock, Thread thread) {
        System.out.println(
                "[Runtime Environment Message] : " + thread.getName() + " requested to MONITOREXIT over " +
                        "the " + lock.toString()
        );
    }

    /**
     * Handles a lock acquisition event from a thread.
     * <p>
     * This method is invoked when a thread in the program under test has successfully acquired a lock. The thread and
     * the lock it acquired are then recorded in the {@link #monitorList}.
     * <p>
     * The method logs the lock acquisition event and adds the lock and thread to the {@link #monitorList}. This allows
     * the {@link RuntimeEnvironment} to keep track of which threads hold which locks, which is essential for handling
     * synchronization and preventing deadlocks.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #monitorList}.
     *
     * @param lock   The lock that the thread has acquired.
     * @param thread The thread that has acquired the lock.
     */
    public static void acquiredLock(Object lock, Thread thread) {
        System.out.println(
                "[Runtime Environment Message] : " + thread.getName() + " acquired the " + lock.toString() + " lock"
        );
        monitorList.put(lock, thread);
        System.out.println(
                "[Runtime Environment Message] : (" + lock + ", " + thread.getName() + ") added to the " +
                        "monitorList of the RuntimeEnvironment"
        );
    }

    /**
     * Handles a lock release event from a thread.
     * <p>
     * This method is invoked when a thread in the program under test has successfully released a lock. The thread and
     * the lock it released are then removed from the {@link #monitorList}.
     * <p>
     * The method logs the lock release event and removes the lock and thread from the {@link #monitorList}. This allows
     * the {@link RuntimeEnvironment} to keep track of which threads hold which locks, which is essential for handling
     * synchronization and preventing deadlocks.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #monitorList}.
     *
     * @param lock   The lock that the thread has released.
     * @param thread The thread that has released the lock.
     */
    public static void releasedLock(Object lock, Thread thread) {
        System.out.println(
                "[Runtime Environment Message] : " + thread.getName() + " released the " + lock.toString() +
                        " lock"
        );
        monitorList.remove(lock, thread);
        threadExitMonitorReq = thread;
        objectExitMonitorReq = lock;
        System.out.println(
                "[Runtime Environment Message] : (" + lock + ", " + thread.getName() + ") removed from the " +
                        "monitorList of the RuntimeEnvironment"
        );
        waitRequest(thread);
    }

    /**
     * Handles an assertion failure from a thread.
     * <p>
     * This method is invoked when a thread in the program under test encounters a failed assert statement. The thread
     * uses this method to notify the {@link RuntimeEnvironment} about the assertion failure.
     * <p>
     * The method sets the {@link #assertFlag} to true, indicating that an assertion has failed. It then logs the
     * provided failure message to the console. Finally, it makes the current thread wait by calling the
     * {@link #waitRequest(Thread)} method. This hands over control to the {@link SchedulerThread} for deciding which
     * thread to run next.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #assertFlag}.
     *
     * @param message The failure message associated with the assert statement.
     */
    public static void assertOperation(String message) {
        System.out.println("[Runtime Environment Message] : " + message);
        assertFlag = true;
        waitRequest(Thread.currentThread());
    }

    /**
     * Creates a {@link JoinEvent} for a thread that has requested to join another thread.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test has requested to join
     * another thread and the latter has finished its execution. The method creates a {@link JoinEvent} for the requesting
     * thread over the finished thread.
     * <p>
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)} method.
     * It then creates a new {@link JoinEvent} with the {@code EventType.JOIN}, the IDs of the requesting and finished
     * threads, and the generated serial number.
     * <p>
     * The created {@link JoinEvent} is then added to the instructions of the requesting thread in the {@link #mcThreads}
     * map. This allows the {@link SchedulerThread} to keep track of the join events in the program execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #mcThreads} map.
     *
     * @param threadReq The thread that has requested to join over the threadRes.
     * @param threadRes The thread that the threadReq has requested to join over it.
     * @return The created {@link JoinEvent} for the threadReq over the threadRes.
     */
    public static JoinEvent createJoinEvent(Thread threadReq, Thread threadRes) {
        int serialNumber = getNextSerialNumber(threadReq);
        JoinEvent joinEvent = new JoinEvent(EventType.JOIN, threadIdMap.get(threadReq.getId()).intValue(), serialNumber,
                threadIdMap.get(threadRes.getId()).intValue());
        mcThreads.get(threadIdMap.get(threadReq.getId()).intValue()).getInstructions().add(joinEvent);
        return joinEvent;
    }

    /**
     * Creates a {@link StartEvent} for a thread that has been started by another thread.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread (the callerThread) in the program under test
     * has started another thread (the calleeThread). The method creates a {@link StartEvent} for the calleeThread.
     * <p>
     * The method creates a new {@link StartEvent} with the {@code EventType.START}, the IDs of the calleeThread and
     * callerThread. The serial number for the event is set to 0 as it is the first event for the calleeThread.
     * <p>
     * The created {@link StartEvent} is then added to the instructions of the calleeThread in the {@link #mcThreads} map.
     * This allows the {@link SchedulerThread} to keep track of the start events in the program execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #mcThreads} map.
     *
     * @param calleeThread The thread for which the {@link StartEvent} is being created.
     * @param callerThread The thread that started the calleeThread.
     * @return The created {@link StartEvent} for the calleeThread.
     */
    public static StartEvent createStartEvent(Thread calleeThread, Thread callerThread) {
        StartEvent startEvent = new StartEvent(EventType.START, threadIdMap.get(calleeThread.getId()).intValue(), 0,
                threadIdMap.get(callerThread.getId()).intValue());
        mcThreads.get(threadIdMap.get(calleeThread.getId()).intValue()).getInstructions().add(startEvent);
        return startEvent;
    }

    /**
     * Creates a {@link FinishEvent} for a thread that has completed its execution.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test has finished its execution.
     * The method creates a {@link FinishEvent} for the finished thread.
     * <p>
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link FinishEvent} with the {@code EventType.FINISH}, the ID of the finished thread, and
     * the generated serial number.
     * <p>
     * The created {@link FinishEvent} is then added to the instructions of the finished thread in the {@link #mcThreads} map.
     * This allows the {@link SchedulerThread} to keep track of the finish events in the program execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is no
     * need to synchronize access to the {@link #mcThreads} map.
     *
     * @param thread The thread for which the {@link FinishEvent} is being created.
     * @return The created {@link FinishEvent} for the finished thread.
     */
    public static FinishEvent createFinishEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        FinishEvent finishEvent = new FinishEvent(EventType.FINISH, threadIdMap.get(thread.getId()).intValue(), serialNumber);
        mcThreads.get(threadIdMap.get(thread.getId()).intValue()).getInstructions().add(finishEvent);
        return finishEvent;
    }

    /**
     * Creates a {@link ReadEvent} for a thread that is about to read a value from a {@link Location}.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to execute a
     * read operation. The method creates a {@link ReadEvent} for the thread and the {@link Location} it wants to read from.
     * <p>
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link ReadEvent} with the {@code EventType.READ}, the ID of the thread, the generated
     * serial number, the current value of the {@link Location}, and the {@link Location} itself.
     * <p>
     * The created {@link ReadEvent} is then added to the instructions of the thread in the {@link #mcThreads} map. This allows
     * the {@link SchedulerThread} to keep track of the read events in the program execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is no
     * need to synchronize access to the {@link #mcThreads} map.
     *
     * @param thread   The thread for which the {@link ReadEvent} is being created.
     * @param location The {@link Location} that the thread wants to read from.
     * @return The created {@link ReadEvent} for the thread.
     * @throws NullPointerException if the location is null.
     */
    public static ReadEvent createReadEvent(Thread thread, Location location) {
        int serialNumber = getNextSerialNumber(thread);
        ReadEvent readEvent = new ReadEvent(threadIdMap.get(thread.getId()).intValue(), EventType.READ, serialNumber,
                Objects.requireNonNull(location.getValue()), null, location);
        mcThreads.get(threadIdMap.get(thread.getId()).intValue()).getInstructions().add(readEvent);
        return readEvent;
    }

    /**
     * Creates a {@link WriteEvent} for a thread that is about to write a value to a {@link Location}.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to execute a
     * write operation. The method creates a {@link WriteEvent} for the thread, the {@link Location} it wants to write to, and the new
     * value it wants to write.
     * <p>
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link WriteEvent} with the {@code EventType.WRITE}, the ID of the thread, the generated
     * serial number, the new value, and the {@link Location} itself.
     * <p>
     * The created {@link WriteEvent} is then added to the instructions of the thread in the {@link #mcThreads} map. This allows
     * the {@link SchedulerThread} to keep track of the write events in the program execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is no need
     * to synchronize access to the {@link #mcThreads} map.
     *
     * @param thread   The thread for which the {@link WriteEvent} is being created.
     * @param location The {@link Location} that the thread wants to write to.
     * @param newVal   The new value that the thread wants to write to the {@link Location}.
     * @return The created {@link WriteEvent} for the thread.
     */
    public static WriteEvent createWriteEvent(Thread thread, Location location, Object newVal) {
        int serialNumber = getNextSerialNumber(thread);
        WriteEvent writeEvent = new WriteEvent(threadIdMap.get(thread.getId()).intValue(), EventType.WRITE, serialNumber,
                newVal, location);
        mcThreads.get(threadIdMap.get(thread.getId()).intValue()).getInstructions().add(writeEvent);
        return writeEvent;
    }

    /**
     * Creates an {@link EnterMonitorEvent} for a thread that is about to enter a monitor.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a {@code MONITORENTER} operation. The method creates an {@link EnterMonitorEvent} for the thread and the
     * lock it wants to enter.
     * <p>
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link EnterMonitorEvent} with the {@code EventType.ENTER_MONITOR}, the ID of the
     * thread, the generated serial number, and the monitor it wants to enter.
     * <p>
     * The created {@link EnterMonitorEvent} is then added to the instructions of the thread in the {@link #mcThreads}
     * map. This allows the {@link SchedulerThread} to keep track of the enter monitor events in the program execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #mcThreads} map.
     *
     * @param thread The thread for which the {@link EnterMonitorEvent} is being created.
     * @param lock   The lock that the thread wants to enter.
     * @return The created {@link EnterMonitorEvent} for the thread.
     */
    public static EnterMonitorEvent createEnterMonitorEvent(Thread thread, Object lock) {
        int serialNumber = getNextSerialNumber(thread);
        EnterMonitorEvent enterMonitorEvent = new EnterMonitorEvent(threadIdMap.get(thread.getId()).intValue(),
                EventType.ENTER_MONITOR, serialNumber, createMonitor(lock));
        mcThreads.get(threadIdMap.get(thread.getId()).intValue()).getInstructions().add(enterMonitorEvent);
        return enterMonitorEvent;
    }

    /**
     * Creates an {@link ExitMonitorEvent} for a thread that is about to exit a monitor.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a {@code MONITOREXIT} operation. The method creates an {@link ExitMonitorEvent} for the thread and the
     * lock it wants to exit.
     * <p>
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link ExitMonitorEvent} with the {@code EventType.EXIT_MONITOR}, the ID of the
     * thread, the generated serial number, and the monitor it wants to exit.
     * <p>
     * The created {@link ExitMonitorEvent} is then added to the instructions of the thread in the {@link #mcThreads}
     * map. This allows the {@link SchedulerThread} to keep track of the exit monitor events in the program execution.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #mcThreads} map.
     *
     * @param thread The thread for which the {@link ExitMonitorEvent} is being created.
     * @param lock   The lock that the thread wants to exit.
     * @return The created {@link ExitMonitorEvent} for the thread.
     */
    public static ExitMonitorEvent createExitMonitorEvent(Thread thread, Object lock) {
        int serialNumber = getNextSerialNumber(thread);
        ExitMonitorEvent exitMonitorEvent = new ExitMonitorEvent(threadIdMap.get(thread.getId()).intValue(),
                EventType.EXIT_MONITOR, serialNumber, createMonitor(lock));
        mcThreads.get(threadIdMap.get(thread.getId()).intValue()).getInstructions().add(exitMonitorEvent);
        return exitMonitorEvent;
    }

    /**
     * Creates a {@link Monitor} object for a lock that a thread is about to enter or exit.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a {@code MONITORENTER} or {@code MONITOREXIT} operation. The method creates a {@link Monitor} object for
     * the lock that the thread wants to enter or exit.
     * <p>
     * The method first retrieves the Class object of the lock and then creates a new {@link Monitor} with the Class
     * object and the lock.
     * <p>
     * The created {@link Monitor} object is then used to create an {@link EnterMonitorEvent} or {@link ExitMonitorEvent}
     * for the thread.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the lock.
     *
     * @param lock The lock for which the {@link Monitor} object is being created.
     * @return The created {@link Monitor} object for the lock.
     */
    private static Monitor createMonitor(Object lock) {
        Class<?> clazz = lock.getClass();
        return new Monitor(clazz, lock);
    }

    /**
     * Creates a {@link Location} object for a thread that is about to perform a read or write operation on a field.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to execute a
     * read or write operation. The method creates a {@link Location} object for the field that the thread wants to access.
     * <p>
     * The method first retrieves the Class object of the owner class and the Field object of the field. It then
     * retrieves the current value of the field from the provided object. Using these, it creates a new {@link Location}
     * object with the Class object, the object instance, the Field object, the current value, and the descriptor of
     * the field.
     * <p>
     * The created {@link Location} object can then be used to create a {@link ReadEvent} or {@link WriteEvent} for the thread.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is no
     * need to synchronize access to the field.
     *
     * @param obj        The object that the thread wants to read from or write to.
     * @param owner      The fully qualified name of the class that the field belongs to.
     * @param name       The name of the field.
     * @param descriptor The type descriptor of the field.
     * @return The created {@link Location} object for the field.
     * @throws ClassNotFoundException if the owner class is not found.
     * @throws NoSuchFieldException   if the field is not found in the owner class.
     * @throws IllegalAccessException if the field is not accessible.
     */
    private static Location createLocation(Object obj, String owner, String name, String descriptor) {
        try {
            Class<?> clazz = Class.forName(owner.replace("/", "."));
            Object instance = clazz.cast(obj);
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(instance);
            return new Location(clazz, instance, field, value, descriptor);
        } catch (ClassNotFoundException c) {
            c.printStackTrace();
            assert (false) : "ClassNotFoundException in createLocation method";
        } catch (NoSuchFieldException n) {
            n.printStackTrace();
            assert (false) : "NoSuchFieldException in createLocation method";
        } catch (IllegalAccessException i) {
            i.printStackTrace();
            assert (false) : "IllegalAccessException in createLocation method";
        }
        return null;
    }

    /**
     * Retrieves the next serial number for the events of a specific thread and increments it by one.
     * <p>
     * This method is invoked when a new event is being created for a thread in the program under test. The method
     * retrieves the current serial number for the thread's events from the {@link #mcThreadSerialNumber} map,
     * increments it by one, and then updates the map with the new serial number.
     * <p>
     * The serial number is used to order the events of a thread in the sequence they were created. This allows the
     * {@link SchedulerThread} to replay the events in the same order during the model checking process.
     * <p>
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is no need
     * to synchronize access to the {@link #mcThreadSerialNumber} map.
     *
     * @param thread The thread for which the next serial number is being retrieved and incremented.
     * @return The next serial number for the events of the thread.
     */
    public static int getNextSerialNumber(Thread thread) {
        int serialNumber = mcThreadSerialNumber.get(threadIdMap.get(thread.getId()).intValue()) + 1;
        mcThreadSerialNumber.put(threadIdMap.get(thread.getId()).intValue(), serialNumber);
        return serialNumber;
    }

    /**
     * Resets the {@link RuntimeEnvironment} to its initial state.
     * <p>
     * This method is invoked by the terminateExecution method after the execution of the program under test is
     * finished. It resets all the fields of the {@link RuntimeEnvironment} to their initial values, effectively preparing
     * the {@link RuntimeEnvironment} for the next execution of the program.
     */
    public static void resetRuntimeEnvironment() {
        threadCount = 1;
        threadWaitReq = null;
        threadStartReq = null;
        threadEnterMonitorReq = null;
        objectEnterMonitorReq = null;
        threadJoinReq = null;
        threadJoinRes = null;
        assertFlag = false;
        locks = new HashMap<>();
        createdThreadList = new ArrayList<>();
        readyThreadList = new ArrayList<>();
        monitorList = new HashMap<>();
        monitorRequest = new HashMap<>();
        joinRequest = new HashMap<>();
        mcThreads = new HashMap<>();
        mcThreadSerialNumber = new HashMap<>();
        mcGraphs.addAll(tempMCGraphs);
        tempMCGraphs = new ArrayList<>();
        writeEventReq = null;
        threadIdMap = new HashMap<>();
        executionFinished = false;
        randomEventsRecord = null;
        suspendedThreads = new ArrayList<>();
    }
}