package org.mpisws.runtime;

import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mpisws.checker.CheckerConfiguration;
import executionGraph.ExecutionGraph;
import org.mpisws.checker.StrategyType;
import org.mpisws.manager.Finished;
import org.mpisws.manager.FinishedType;
import org.mpisws.manager.HaltExecutionException;
import org.mpisws.solver.SMTSolverTypes;
import org.mpisws.solver.SymbolicSolver;
import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.StaticMethodMonitor;
import org.mpisws.util.concurrent.InstanceMethodMonitor;
import programStructure.*;
import org.mpisws.symbolic.SymbolicOperation;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;

/**
 * The RuntimeEnvironment class is a singleton that manages the execution state of a multithreaded program under test.
 * It tracks and controls various aspects of the program's execution states, including thread creation, synchronization,
 * termination, and read/write operations on shared resources. It also handles events like thread start, join,
 * monitor enter/exit, read/write operations, assertion failures, and symbolic operations. The class provides functionality
 * to load a {@link CheckerConfiguration} object from a file, which is used to configure the execution of the program
 * under test. It also provides methods to control the execution of the program, including setting a random seed,
 * initializing the scheduler thread, and terminating the execution. The class is designed to be used in a single-threaded
 * environment, where the {@link SchedulerThread} guarantees the sequential execution of operations.
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

    public static Map<Long, ReceiveEvent> blockedRecvThreadMap = new HashMap<>();

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
     * @property {@link #eventsRecord} is used to store the events that are executed by the threads in the
     * program under test using the {@link org.mpisws.checker.strategy.RandomStrategy}.
     */
    public static List<Event> eventsRecord = new ArrayList<>();

    /**
     * @property {@link #mcThreadSerialNumber} is used to store the number of seen events for each thread.
     * This number is used to generate the serial number for the events of the threads.
     */
    public static Map<Integer, Integer> mcThreadSerialNumber = new HashMap<>();

    /**
     * @property {@link #mcGraphs} is used to store the execution graphs which are generated by the model checker.
     */
    public static List<ExecutionGraph> mcGraphs;

    /**
     * @property {@link #writeEventReq} is used to store the {@link WriteEvent} that a thread will execute.
     */
    public static WriteEvent writeEventReq;

    /**
     * @property {@link #readEventReq} is used to store the {@link ReadEvent} that a thread will execute.
     */
    public static ReadEvent readEventReq;

    public static SendEvent sendEventReq;

    public static ReceiveEvent receiveEventReq;

    public static ReceiveEvent blockingReceiveEventReq;

    public static ReceiveEvent blokingReceiveOperationEventReq;

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
     * @property {@link #threadObjectMap} is used to store the mapping between the thread id generated by the
     * {@link RuntimeEnvironment} and the thread object.
     */
    public static Map<Long, Thread> threadObjectMap = new HashMap<>();

    /**
     * @property {@link #isFinished} is used to indicate that a thread has finished its execution.
     */
    public static boolean isFinished = false;

    /**
     * @property {@link #strategyType} is used to store the strategy type that is used by the {@link #RuntimeEnvironment}
     * and {@link SchedulerThread}. The supported strategy types are: {@link StrategyType#RANDOM} and
     * {@link StrategyType#TRUST}.
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
     * @property {@link #executionFinished} is used to indicate that the current execution iteration is finished.
     */
    public static boolean executionFinished = false;

    /**
     * @property {@link #deadlockHappened} is used to indicate that a deadlock happened.
     */
    public static boolean deadlockHappened = false;

    /**
     * @property {@link #allExecutionsFinished} is used to indicate that all execution iterations are finished.
     */
    public static boolean allExecutionsFinished = false;

    /**
     * @property {@link #seed} is used to store the random seed that is used by the
     * {@link org.mpisws.checker.strategy.RandomStrategy} object.
     */
    public static long seed = 0;

    /**
     * @property {@link #suspendedThreads} is used to store the threads that are suspended in the program under test.
     */
    public static List<Thread> suspendedThreads = new ArrayList<>();

    /**
     * @property {@link #parkedThreadList} is used to store the threads that are parked in the program under test.
     */
    public static List<Thread> parkedThreadList = new ArrayList<>();

    /**
     * @property {@link #buggyTracePath} is used to store the path to the buggy trace object.
     */
    public static String buggyTracePath;

    /**
     * @property {@link #buggyTraceFile} is used to store the name of the buggy trace file.
     */
    public static String buggyTraceFile;

    /**
     * @property {@link #executionGraphsPath} is used to store the path to the visualized execution graphs.
     */
    public static String executionGraphsPath;

    /**
     * @property {@link #suspendPriority} is used to store suspender and suspendee threads in the program under test,
     * based on the monitor that the suspendee thread is waiting to enter and the monitor that the suspender thread has
     * already entered. (Currently, this feature is only used by trust strategy to sustain the consistency of the
     * scheduling with respect to the suspend Events)
     */
    public static Map<Object, Set<Pair<Long, Long>>> suspendPriority = new HashMap<>();

    /**
     * @property {@link #threadParkingPermit} is used to store the parking permit for the threads in the program under
     * test. The key is the thread id and the value is the parking permit. When a thread is going to be parked or
     * unparked, this field is used to check whether the thread has the parking permit or not.
     */
    public static Map<Long, Boolean> threadParkingPermit = new HashMap<>();

    /**
     * @property {@link #threadToPark} is used to store the thread that is going to be parked.
     */
    public static Thread threadToPark = null;

    /**
     * @property {@link #unparkerThread} is used to store the thread that is going to unpark {@link #unparkeeThread}.
     */
    public static Thread unparkerThread = null;

    /**
     * @property {@link #unparkeeThread} is used to store the thread that is going to be unparked.
     */
    public static Thread unparkeeThread = null;

    /**
     * @property {@link #symbolicOperation} is used to store the symbolic operation that is executed by the threads
     * in the program under test.
     */
    public static SymbolicOperation symbolicOperation;

    /**
     * @property {@link #solverResult} is used to store the result of the symbolic operation that is executed by the
     * threads in the program under test. This result is returned by the {@link SymbolicSolver} object.
     */
    public static Boolean solverResult = null;

    /**
     * @property {@link #threadSymbolicOperation} is used to store the symbolic operations that are executed by the
     * threads in the program under test. The key is the thread id and the value is the list of the symbolic operations.
     * (Currently, this field is not used by {@link SchedulerThread}).
     */
    public static Map<Long, List<SymbolicOperation>> threadSymbolicOperation = new HashMap<>();

    /**
     * @property {@link #pathSymbolicOperations} is used to store the symbolic operations that are executed in the
     * path of the program under test, independently of the threads.
     */
    public static List<SymbolicOperation> pathSymbolicOperations = new ArrayList<>();

    /**
     * @property {@link #solver} is used to store the {@link SymbolicSolver} object that is used to solve the symbolic
     * operations by {@link SchedulerThread}.
     */
    public static SymbolicSolver solver;

    /**
     * @property {@link #solverType} is used to store the solver type that IS used by the {@link #solver}
     */
    public static SMTSolverTypes solverType;

    /**
     * @property {@link #staticMethodMonitorList} is used to store the static method monitors that are executed by the
     * threads in the program under test.
     */
    public static List<StaticMethodMonitor> staticMethodMonitorList = new ArrayList<>();

    /**
     * @property {@link #instanceMethodMonitorList} is used to store the instance-based method monitors that are
     * executed by the threads in the program under test.
     */
    public static List<InstanceMethodMonitor> instanceMethodMonitorList = new ArrayList<>();


    /**
     * The constructor is private to prevent the instantiation of the class
     */
    private RuntimeEnvironment() {
    }

    /**
     * Initializes the {@link RuntimeEnvironment}.
     * <p>
     * This method is invoked by the main method of the program under test. As it is called in a single-threaded
     * environment, there is no need to synchronize the access to the {@link #createdThreadList},
     * {@link #readyThreadList}, and {@link #locks}.
     * </p>
     *
     * @param thread The main thread of the program under test.
     */
    public static void init(Thread thread) {
        System.out.println("[Runtime Environment Message] : The RuntimeEnvironment has been deployed");
        numOfExecutions++;
        System.out.println("[Runtime Environment Message] : The number of executions is " + numOfExecutions);
        loadConfig();
        if (solverType == null) {
            solver = new SymbolicSolver();
        } else {
            solver = new SymbolicSolver(solverType);
        }
        System.out.println("[Runtime Environment Message] : The CheckerConfiguration has been loaded");
        threadIdMap.put(thread.getId(), (long) threadCount);
        threadObjectMap.put(threadIdMap.get(thread.getId()), thread);
        thread.setName("Thread-" + threadCount++);
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) +
                        " added to the createdThreadList of the Runtime Environment"
        );
        Object lock = new Object();
        locks.put(threadIdMap.get(thread.getId()), lock);
        createdThreadList.add(thread);
        readyThreadList.add(thread);
        mcThreadSerialNumber.put(threadIdMap.get(thread.getId()).intValue(), 0);
        threadParkingPermit.put(threadIdMap.get(thread.getId()), false);
        System.out.println(
                "[Runtime Environment Message] : " + thread.getName() + " added to the createdThreadList of" +
                        " the Runtime Environment"
        );
        System.out.println(
                "[Runtime Environment Message] : " + thread.getName() + " added to the readyThreadList of" +
                        " the Runtime Environment"
        );
        System.out.println(
                "[Runtime Environment Message] : " + thread.getName() + " has the " + thread.getState() +
                        " state"
        );
        printState();
    }

    public static void printState() {
        System.out.println("[Runtime Environment Message] : The state of the threads in the createdThreadList");
        for (Thread thread : createdThreadList) {
            System.out.println("[Runtime Environment Message] : " + thread.getName() + " has the " + thread.getState() +
                    " state");
        }
        System.out.println("[Runtime Environment Message] : The state of the threads in the readyThreadList");
        for (Thread thread : readyThreadList) {
            System.out.println("[Runtime Environment Message] : " + thread.getName() + " has the " + thread.getState() +
                    " state");
        }
    }

    /**
     * Loads the {@link CheckerConfiguration} object from the config.obj file and assigns it to {@link #config}.
     * <p>
     * This method is invoked by the {@link #init(Thread)} method.
     * </p>
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

    /**
     * Reads the {@link CheckerConfiguration} object and assigns the values to the corresponding fields.
     * <p>
     * This method is invoked by the {@link #loadConfig()} method.
     * </p>
     */
    private static void readConfig() {
        maxNumOfExecutions = config.maxIterations;
        strategyType = config.strategyType;
        seed = config.seed;
        buggyTracePath = config.buggyTracePath;
        executionGraphsPath = config.executionGraphsPath;
        buggyTraceFile = config.buggyTraceFile;
        solverType = config.solverType;
    }

    /**
     * Adds a new thread to the {@link RuntimeEnvironment}.
     * <p>
     * This method is invoked when a new instance of the Thread class (or a class castable to Thread) is created.
     * The new thread is added to the {@link #createdThreadList}.
     * <p>
     * The method assigns a unique name to the thread in the format "Thread-"+threadCount and increments the
     * {@link #threadCount}. It also creates a corresponding lock object for the thread and adds it to the
     * {@link #locks} map. As this method is invoked in a single-threaded environment (guaranteed by the
     * {@link SchedulerThread}), there is no need to synchronize access to the {@link #createdThreadList} and
     * {@link #locks}.
     * If the thread is already in the {@link #createdThreadList}, a message is printed to the console and no further
     * action is taken.
     * </p>
     *
     * @param thread The thread to be added to the {@link #RuntimeEnvironment}.
     */
    public static void addThread(Thread thread) {
        if (!createdThreadList.contains(thread)) {
            threadIdMap.put(thread.getId(), (long) threadCount);
            threadObjectMap.put(threadIdMap.get(thread.getId()), thread);
            thread.setName("Thread-" + threadCount++);
            Object lock = new Object();
            locks.put(threadIdMap.get(thread.getId()), lock);
            createdThreadList.add(thread);
            mcThreadSerialNumber.put(threadIdMap.get(thread.getId()).intValue(), 0);
            threadParkingPermit.put(threadIdMap.get(thread.getId()), false);
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
     * The method assigns the thread to the {@link #threadStartReq} to inform the {@link SchedulerThread} that the thread
     * is ready to run. It then calls the {@link #waitRequest(Thread)} method to make the current thread wait and hand
     * over control to the {@link SchedulerThread} for deciding which thread to run next.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #readyThreadList}.
     * If the thread is not in the {@link #createdThreadList} or is already in the {@link #readyThreadList}, a message
     * is printed to the console and no further action is taken.
     * </p>
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
            System.out.println("[Runtime Environment Message] : thread-" + threadIdMap.get(thread.getId()) + " is not " +
                    "in the createdThreadList");
        }
    }

    /**
     * Handles a join request from one thread to another.
     * <p>
     * This method is invoked when a thread in the program under test calls the join() method on another thread. The
     * calling thread is then set as the {@link #threadJoinReq} and the thread it wishes to join is set as the
     * {@link #threadJoinRes}.
     * The method then calls the {@link #waitRequest(Thread)} method to make the calling thread wait and hand over
     * control to the {@link SchedulerThread} for deciding which thread to run next.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadJoinReq} and {@link #threadJoinRes}.
     * </p>
     *
     * @param threadReq The thread that requested to join over the threadRes.
     * @param threadRes The thread that the threadReq requested to join over it.
     */
    public static void threadJoin(Thread threadRes, Thread threadReq) {
        System.out.println(
                "[Runtime Environment Message] : " + threadReq.getName() + " requested to join over the " +
                        threadRes.getName()
        );
        threadJoinReq = threadReq;
        threadJoinRes = threadRes;
        waitRequest(threadReq);
    }

    /**
     * Requests permission for a thread to run.
     * <p>
     * This method is invoked by the {@link SchedulerThread} to request permission from a thread to execute. It ensures
     * that access to {@link #threadWaitReq} is synchronized and race-free.
     * The method uses the lock associated with the thread (retrieved from the {@link #locks} map) to synchronize the
     * access. This ensures that the thread can safely run without any race conditions.
     * </p>
     *
     * @param thread The thread for which permission to run is requested.
     */
    public static void getPermission(Thread thread) {
        synchronized (locks.get(threadIdMap.get(thread.getId()))) {
            System.out.println("[Runtime Environment Message] : " + Thread.currentThread().getName() + " got " +
                    "permitted to RUN");
        }
    }

    /**
     * Handles a wait request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test needs to wait. The waiting thread could be due to
     * various reasons such as a new thread wanting to start, read or write operations being requested, monitor enter
     * operation being requested, join operation being requested, or an assert statement failing.
     * The method assigns the waiting thread to the {@link #threadWaitReq} to inform the {@link SchedulerThread} that
     * the thread is waiting. It then makes the current thread wait by calling the wait() method on the lock associated
     * with the thread (retrieved from the {@link #locks} map). This hands over control to the {@link SchedulerThread}
     * for deciding which thread to run next.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadWaitReq}.
     * </p>
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
     * The method first starts the {@link SchedulerThread} and then assigns the main thread to the {@link #threadWaitReq}
     * to inform the {@link SchedulerThread} that the main thread is waiting. It then makes the main thread wait by
     * calling the wait() method on the lock associated with the main thread (retrieved from the {@link #locks} map).
     * This hands over control to the {@link SchedulerThread} for deciding which thread to run next.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadWaitReq}.
     * </p>
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
     * The method checks if the thread is the main thread. If it is, it sets the {@link #isFinished} flag to true, makes
     * the main thread wait by calling the {@link #waitRequest(Thread)} method, and then calls the
     * {@link #terminateExecution()} method to end the program execution.
     * If the thread is not the main thread, it sets the {@link #isFinished} flag to true and assigns the thread to
     * the {@link #threadWaitReq} to inform the {@link SchedulerThread} that the thread has finished its execution.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #createdThreadList}, {@link #readyThreadList}, and
     * {@link #threadWaitReq}.
     * </p>
     *
     * @param thread The thread that requested to finish.
     */
    public static void finishThreadRequest(Thread thread) throws HaltExecutionException {
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

    /**
     * Terminates the execution of the program under test and resets the {@link RuntimeEnvironment}.
     * <p>
     * This method is invoked when the main thread of the program under test has completed its execution. It first
     * resets the {@link RuntimeEnvironment} by calling the {@link #resetRuntimeEnvironment()} method. Then, based on
     * the strategy type and the states of the {@link RuntimeEnvironment}, it decides whether to terminate the
     * life-cycle process of testing or continue the testing with the next iteration.
     * If the {@link #mcGraphs} list is empty and the {@link #strategyType} is {@link StrategyType#TRUST}, the
     * method terminates the program execution.
     * If the {@link #numOfExecutions} is less than the {@link #maxNumOfExecutions} and the {@link #strategyType}
     * is {@link StrategyType#RANDOM}, the method terminates the program execution.
     * If the {@link #numOfExecutions} is equal to the {@link #maxNumOfExecutions} and the {@link #strategyType}
     * is {@link StrategyType#RANDOM}, the method terminates the program execution.
     * The termination of the program execution is done by throwing a {@link HaltExecutionException} which is caught
     * by the {@link org.mpisws.manager.ByteCodeManager} class.
     * </p>
     */
    private static void terminateExecution() throws HaltExecutionException {
        if (deadlockHappened) {
            System.out.println("[Runtime Environment Message] : The deadlock happened");
            createFinishObject(FinishedType.DEADLOCK);
            throw new HaltExecutionException();
        } else if (allExecutionsFinished) {
            System.out.println("[Runtime Environment Message] : The " + numOfExecutions + " execution is finished");
            System.out.println("[Runtime Environment Message] : The maximum number of the executions is reached");
            createFinishObject(FinishedType.SUCCESS);
            throw new HaltExecutionException();
        } else {
            System.out.println("[Runtime Environment Message] : The " + numOfExecutions + " execution is finished");
            resetRuntimeEnvironment();
        }
    }

    /**
     * Handles a read operation request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a read operation
     * ({@code GETFIELD}). The thread is requesting to read the value of a field from an object.
     * The method first creates a {@link Location} based on the provided object, owner class, field name, and descriptor.
     * It then checks if the {@link Location} is of a primitive type. If it is, a {@link ReadEvent} is created for the
     * thread and the thread are made to wait by calling the {@link #waitRequest(Thread)} method. This hands over
     * control to the {@link SchedulerThread} for deciding which thread to run next.
     * If the {@link Location} is not of a primitive type, a message is printed to the console indicating that the
     * Model Checker will not consider this operation as it does not involve a primitive type.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #writeEventReq}.
     * </p>
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
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "read the value of " + owner + "." + name + "(" + descriptor + ") = " +
                        Objects.requireNonNull(location).getValue()
        );
        System.out.println("[Debug] : obj is " + obj);
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
     * This method is invoked when a thread in the program under test is about to execute a write operation
     * ({@code PUTFIELD}). The thread is requesting to write a new value to a field of an object.
     * The method first creates a {@link Location} based on the provided object, owner class, field name, and descriptor.
     * It then checks if the {@link Location} is of a primitive type. If it is, a {@link WriteEvent} is created for the
     * thread and the thread are made to wait by calling the {@link #waitRequest(Thread)} method. This hands over
     * control to the {@link SchedulerThread} for deciding which thread to run next.
     * If the {@link Location} is not of a primitive type, a message is printed to the console indicating that the
     * Model Checker will not consider this operation as it does not involve a primitive type.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #writeEventReq}.
     * </p>
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
                        "write the [" + newVal + "] value to " + owner + "." + name + "(" + descriptor + ") " +
                        "with old value of [" + Objects.requireNonNull(location).getValue() + "]"
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

    public static void receiveOperation(Thread thread) {
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "receive a message"
        );
        receiveEventReq = createReceiveEvent(thread);
        waitRequest(thread);
    }

    public static ReceiveEvent blockingReceiveRequestOperation(Thread thread) {
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "receive a blocking message"
        );
        ReceiveEvent receiveEvent = createReceiveBlockEvent(thread);
        blockingReceiveEventReq = receiveEvent;
        waitRequest(thread);
        return receiveEvent;
    }

    public static void blockingReceiveOperation(ReceiveEvent receiveEvent) {
        System.out.println(
                "[Runtime Environment Message] : Thread-" + receiveEvent.getTid() + " requested to " +
                        "receive a message"
        );
        JMCThread jmcThread = (JMCThread) findThreadObject(receiveEvent.getTid());
        if (jmcThread.getNextMessageIndex() < 0) {
            receiveEventReq = receiveEvent;
            waitRequest(jmcThread);
        } else {
            System.out.println(
                    "[Runtime Environment Message] : Thread-" + receiveEvent.getTid() + " has already " +
                            "received the message"
            );
            eventsRecord.add(receiveEvent);
        }
    }

    public static ReceiveEvent blockingReceiveRequestOperation(Thread thread, BiFunction<Long, Long, Boolean> function) {
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "receive a blocking message"
        );
        ReceiveEvent receiveEvent = createReceiveTaggedBlockEvent(thread, function);
        blockingReceiveEventReq = receiveEvent;
        waitRequest(thread);
        return receiveEvent;
    }

    public static void receiveTaggedOperation(Thread thread, BiFunction<Long, Long, Boolean> function) {
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "receive a message"
        );
        receiveEventReq = createReceiveTaggedUnblockEvent(thread, function);
        waitRequest(thread);
    }

    public static Message sendSimpleMessageOperation(Thread thread, long receiverId, Object value) {
        Message message = createSimpleMessage(receiverId, value);
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "send an untagged message to Thread-" + threadIdMap.get(receiverId)
        );
        sendEventReq = createSimpleSendEvent(thread, message, receiverId);
        waitRequest(thread);
        return message;
    }

    public static Message sendTaggedMessageOperation(Thread thread, long receiverId, long tag, Object value) {
        Message message = createTaggedMessage(receiverId, Thread.currentThread().getId(), value, tag);
        System.out.println(
                "[Runtime Environment Message] : Thread-" + threadIdMap.get(thread.getId()) + " requested to " +
                        "send a tagged message to Thread-" + threadIdMap.get(receiverId)
        );
        sendEventReq = createTaggedSendEvent(thread, message, receiverId, tag);
        waitRequest(thread);
        return message;
    }

    /**
     * Handles a park operation request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a park operation. The thread
     * is requesting to park itself. The method assigns the thread to the {@link #threadToPark} to inform the
     * {@link SchedulerThread} that the thread is going to park. It then makes the thread wait by calling the
     * {@link #waitRequest(Thread)} method. This hands over control to the {@link SchedulerThread} for deciding which
     * thread to run next.
     * </p>
     *
     * @param thread The thread that requested to park.
     */
    public static void parkOperation(Thread thread) {
        System.out.println("[Runtime Environment Message] : " + thread.getName() + " requested to PARK");
        threadToPark = thread;
        waitRequest(thread);
    }

    /**
     * Handles an unpark operation request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute an unpark operation. The thread
     * is requesting to unpark another thread. The method assigns the caller thread to the {@link #unparkerThread} and
     * the callee thread to the {@link #unparkeeThread} to inform the {@link SchedulerThread} that the caller thread is
     * going to unpark the callee thread. It then makes the caller thread wait by calling the {@link #waitRequest(Thread)}
     * method. This hands over control to the {@link SchedulerThread} for deciding which thread to run next.
     * </p>
     *
     * @param callerThread the thread that requested to unpark another thread.
     * @param calleeThread the thread that is going to be unparked.
     */
    public static void unparkOperation(Thread callerThread, Thread calleeThread) {
        System.out.println("[Runtime Environment Message] : " + callerThread.getName() + " requested to UNPARK " +
                calleeThread.getName());
        unparkerThread = callerThread;
        unparkeeThread = calleeThread;
        waitRequest(callerThread);
    }

    /**
     * Handles a symbolic operation request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a symbolic operation. The
     * thread is requesting to execute a symbolic arithmetic operation. The method assigns the thread to the
     * {@link #symbolicOperation} to inform the {@link SchedulerThread} that the thread is going to execute a symbolic
     * arithmetic operation. It then makes the thread wait by calling the {@link #waitRequest(Thread)} method. This hands
     * over control to the {@link SchedulerThread} for deciding which thread to run next.
     * </p>
     *
     * @param thread            The thread that requested to execute a symbolic arithmetic operation.
     * @param symbolicOperation The symbolic arithmetic operation that the thread is going to execute.
     * @return The result of the symbolic arithmetic operation.
     */
    public static boolean symbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
        System.out.println("[Runtime Environment Message] : " + thread.getName() + " requested to execute a symbolic " +
                "arithmetic operation");
        RuntimeEnvironment.symbolicOperation = symbolicOperation;
        waitRequest(thread);
        return solverResult;
    }

    /**
     * Handles a monitor entry request from a thread.
     * <p>
     * This method is invoked when a thread in the program under test is about to execute a {@code MONITORENTER}
     * operation. The thread is requesting to enter the monitor of a lock.
     * The method assigns the requesting thread to the {@link #threadEnterMonitorReq} and the lock to the
     * {@link #objectEnterMonitorReq} to inform the {@link SchedulerThread} that the thread has requested to enter the
     * monitor. It then makes the requesting thread wait by calling the {@link #waitRequest(Thread)} method. This hands
     * over control to the {@link SchedulerThread} for deciding which thread to run next.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #threadEnterMonitorReq} and {@link #objectEnterMonitorReq}.
     * </p>
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
     * This method is invoked when a thread in the program under test is about to execute a {@code MONITOREXIT}
     * operation. The thread is requesting to exit the monitor of a lock.
     * The method logs the request of the thread to exit the monitor of the lock. However, it does not perform any
     * action or change any state in the {@link RuntimeEnvironment}. The actual exit operation is handled by the JVM.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to any shared resources.
     * </p>
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
     * The method logs the lock acquisition event and adds the lock and thread to the {@link #monitorList}. This allows
     * the {@link RuntimeEnvironment} to keep track of which threads hold which locks, which is essential for handling
     * synchronization and preventing deadlocks.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #monitorList}.
     * </p>
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
                "[Runtime Environment Message] : The monitor (" + lock + ", " + thread.getName() + ") added to the " +
                        "monitorList of the RuntimeEnvironment"
        );
    }

    /**
     * Handles a lock release event from a thread.
     * <p>
     * This method is invoked when a thread in the program under test has successfully released a lock. The thread and
     * the lock it released are then removed from the {@link #monitorList}.
     * The method logs the lock release event and removes the lock and thread from the {@link #monitorList}. This allows
     * the {@link RuntimeEnvironment} to keep track of which threads hold which locks, which is essential for handling
     * synchronization and preventing deadlocks.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #monitorList}.
     * </p>
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
                "[Runtime Environment Message] : Monitor (" + lock + ", " + thread.getName() + ") removed from the " +
                        "monitorList of the RuntimeEnvironment"
        );
        waitRequest(thread);
    }

    /**
     * Handles an assertion failure from a thread.
     * <p>
     * This method is invoked when a thread in the program under test encounters a failed assert statement. The thread
     * uses this method to notify the {@link RuntimeEnvironment} about the assertion failure.
     * The method sets the {@link #assertFlag} to true, indicating that an assertion has failed. It then logs the
     * provided failure message to the console. Finally, it makes the current thread wait by calling the
     * {@link #waitRequest(Thread)} method. This hands over control to the {@link SchedulerThread} for deciding which
     * thread to run next.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #assertFlag}.
     * </p>
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
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test has requested to
     * join another thread and the latter has finished its execution. The method creates a {@link JoinEvent} for the
     * requesting thread over the finished thread.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link JoinEvent} with the {@code EventType.JOIN}, the IDs of the requesting and
     * finished threads, and the generated serial number.
     * </p>
     *
     * @param threadReq The thread that has requested to join over the threadRes.
     * @param threadRes The thread that the threadReq has requested to join over it.
     * @return The created {@link JoinEvent} for the threadReq over the threadRes.
     */
    public static JoinEvent createJoinEvent(Thread threadReq, Thread threadRes) {
        int serialNumber = getNextSerialNumber(threadReq);
        return new JoinEvent(EventType.JOIN, threadIdMap.get(threadReq.getId()).intValue(), serialNumber,
                threadIdMap.get(threadRes.getId()).intValue());
    }

    /**
     * Creates a {@link ParkEvent} for a thread that is about to park.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test is about to park.
     * The method creates a {@link ParkEvent} for the thread.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link ParkEvent} with the {@code EventType.PARK} and the ID of the thread.
     * </p>
     *
     * @param thread The thread for which the {@link ParkEvent} is being created.
     * @return The created {@link ParkEvent} for the thread.
     */
    public static ParkEvent createParkEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        return new ParkEvent(EventType.PARK, threadIdMap.get(thread.getId()).intValue(), serialNumber);
    }

    /**
     * Creates a {@link UnparkEvent} for a thread that is about to unpark another thread.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test is about to be
     * unparked by another thread. The method creates an {@link UnparkEvent} for the unparkeeThread over the unparkerThread.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link UnparkEvent} with the {@code EventType.UNPARK}, the IDs of the unparkerThread
     * and unparkeeThread, and the generated serial number.
     * </p>
     *
     * @param unparkerThread The thread that is about to unpark the unparkeeThread.
     * @param unparkeeThread The thread that is about to be unparked by the unparkerThread.
     * @return The created {@link UnparkEvent} for the unparkerThread over the unparkeeThread.
     */
    public static UnparkEvent createUnparkEvent(Thread unparkerThread, Thread unparkeeThread) {
        int serialNumber = getNextSerialNumber(unparkerThread);
        return new UnparkEvent(EventType.UNPARK, threadIdMap.get(unparkeeThread.getId()).intValue(), serialNumber,
                threadIdMap.get(unparkerThread.getId()).intValue());
    }

    public static UnparkEvent createUnparkEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        return new UnparkEvent(EventType.UNPARK, threadIdMap.get(thread.getId()).intValue(), serialNumber);
    }

    /**
     * Creates a {@link UnparkingEvent} for a thread that has unparked another thread.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test has unparked another
     * thread. The method creates an {@link UnparkingEvent} for the unparkerThread.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link UnparkingEvent} with the {@code EventType.UNPARKING} and the ID of the
     * unparkerThread.
     * </p>
     *
     * @param unparkerThread The thread that has unparked another unparkeeThread.
     * @param unparkeeThread The thread that has been unparked by the unparkerThread.
     * @return The created {@link UnparkingEvent} for the unparkerThread.
     */
    public static UnparkingEvent createUnparkingEvent(Thread unparkerThread, Thread unparkeeThread) {
        int serialNumber = getNextSerialNumber(unparkerThread);
        return new UnparkingEvent(EventType.UNPARKING, threadIdMap.get(unparkerThread.getId()).intValue(), serialNumber,
                threadIdMap.get(unparkeeThread.getId()).intValue());
    }

    /**
     * Creates a {@link StartEvent} for a thread that has been started by another thread.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread (the callerThread) in the program under test
     * has started another thread (the calleeThread). The method creates a {@link StartEvent} for the calleeThread.
     * The method creates a new {@link StartEvent} with the {@code EventType.START}, the IDs of the calleeThread and
     * callerThread. The serial number for the event is set to 0 as it is the first event for the calleeThread.
     * </p>
     *
     * @param calleeThread The thread for which the {@link StartEvent} is being created.
     * @param callerThread The thread that started the calleeThread.
     * @return The created {@link StartEvent} for the calleeThread.
     */
    public static StartEvent createStartEvent(Thread calleeThread, Thread callerThread) {
        return new StartEvent(EventType.START, threadIdMap.get(calleeThread.getId()).intValue(),
                0, threadIdMap.get(callerThread.getId()).intValue());
    }

    /**
     * Creates a {@link FinishEvent} for a thread that has completed its execution.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test has finished its
     * execution. The method creates a {@link FinishEvent} for the finished thread.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link FinishEvent} with the {@code EventType.FINISH}, the ID of the finished
     * thread, and the generated serial number.
     * </p>
     *
     * @param thread The thread for which the {@link FinishEvent} is being created.
     * @return The created {@link FinishEvent} for the finished thread.
     */
    public static FinishEvent createFinishEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        return new FinishEvent(EventType.FINISH, threadIdMap.get(thread.getId()).intValue(),
                serialNumber);
    }

    /**
     * Creates a {@link ReadEvent} for a thread that is about to read a value from a {@link Location}.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a read operation. The method creates a {@link ReadEvent} for the thread and the {@link Location} it wants
     * to read from. The method first generates a serial number for the event by calling the
     * {@link #getNextSerialNumber(Thread)} method. It then creates a new {@link ReadEvent} with the
     * {@code EventType.READ}, the ID of the thread, the generated serial number, the current value of the
     * {@link Location}, and the {@link Location} itself.
     * </p>
     *
     * @param thread   The thread for which the {@link ReadEvent} is being created.
     * @param location The {@link Location} that the thread wants to read from.
     * @return The created {@link ReadEvent} for the thread.
     * @throws NullPointerException if the location is null.
     */
    public static ReadEvent createReadEvent(Thread thread, Location location) {
        int serialNumber = getNextSerialNumber(thread);
        return new ReadEvent(threadIdMap.get(thread.getId()).intValue(), EventType.READ, serialNumber,
                Objects.requireNonNull(location.getValue()), null, location);
    }

    /**
     * Creates a {@link WriteEvent} for a thread that is about to write a value to a {@link Location}.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a write operation. The method creates a {@link WriteEvent} for the thread, the {@link Location} it wants
     * to write to, and the new value it wants to write.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link WriteEvent} with the {@code EventType.WRITE}, the ID of the thread, the
     * generated serial number, the new value, and the {@link Location} itself.
     * </p>
     *
     * @param thread   The thread for which the {@link WriteEvent} is being created.
     * @param location The {@link Location} that the thread wants to write to.
     * @param newVal   The new value that the thread wants to write to the {@link Location}.
     * @return The created {@link WriteEvent} for the thread.
     */
    public static WriteEvent createWriteEvent(Thread thread, Location location, Object newVal) {
        int serialNumber = getNextSerialNumber(thread);
        return new WriteEvent(threadIdMap.get(thread.getId()).intValue(), EventType.WRITE, serialNumber,
                newVal, location);
    }

    public static SendEvent createSimpleSendEvent(Thread thread, Message message, long receiverId) {
        int serialNumber = getNextSerialNumber(thread);
        return new SendEvent(threadIdMap.get(thread.getId()).intValue(), EventType.SEND, serialNumber,
                message, threadIdMap.get(receiverId), null);
    }

    public static SendEvent createTaggedSendEvent(Thread thread, Message message, long receiverId, long tag) {
        int serialNumber = getNextSerialNumber(thread);
        return new SendEvent(threadIdMap.get(thread.getId()).intValue(), EventType.SEND, serialNumber,
                message, threadIdMap.get(receiverId), tag);
    }

    public static ReceiveEvent createReceiveEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        return new ReceiveEvent(threadIdMap.get(thread.getId()).intValue(), EventType.RECEIVE, serialNumber, null, null, 0, null, false, null);
    }

    public static ReceiveEvent createReceiveBlockEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        return new ReceiveEvent(threadIdMap.get(thread.getId()).intValue(), EventType.RECEIVE, serialNumber, null, null, 0, null, true, null);
    }

    public static ReceiveEvent createReceiveTaggedBlockEvent(Thread thread, BiFunction<Long, Long, Boolean> function) {
        int serialNumber = getNextSerialNumber(thread);
        return new ReceiveEvent(threadIdMap.get(thread.getId()).intValue(), EventType.RECEIVE, serialNumber, null, null, 0, null, true, function);
    }

    public static ReceiveEvent createReceiveTaggedUnblockEvent(Thread thread, BiFunction<Long, Long, Boolean> function) {
        int serialNumber = getNextSerialNumber(thread);
        return new ReceiveEvent(threadIdMap.get(thread.getId()).intValue(), EventType.RECEIVE, serialNumber, null, null, 0, null, false, function);
    }

    public static BlockingRecvReq createBlockingRecvReq(Thread thread, ReceiveEvent receiveEvent) {
        int serialNumber = getNextSerialNumber(thread);
        return new BlockingRecvReq(threadIdMap.get(thread.getId()).intValue(), EventType.BLOCK_RECV_REQ, serialNumber, receiveEvent);
    }

    public static BlockedRecvEvent createBlockedRecvEvent(Thread thread, ReceiveEvent receiveEvent) {
        int serialNumber = getNextSerialNumber(thread);
        return new BlockedRecvEvent(threadIdMap.get(thread.getId()).intValue(), EventType.BLOCKED_RECV, serialNumber, receiveEvent);
    }

    public static UnblockedRecvEvent createUnblockedRecvEvent(Thread thread, ReceiveEvent receiveEvent) {
        int serialNumber = getNextSerialNumber(thread);
        return new UnblockedRecvEvent(threadIdMap.get(thread.getId()).intValue(), EventType.UNBLOCKED_RECV, serialNumber, receiveEvent);
    }

    /**
     * Creates an {@link EnterMonitorEvent} for a thread that is about to enter a monitor.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a {@code MONITORENTER} operation. The method creates an {@link EnterMonitorEvent} for the thread and the
     * lock it wants to enter.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link EnterMonitorEvent} with the {@code EventType.ENTER_MONITOR}, the ID of the
     * thread, the generated serial number, and the monitor it wants to enter.
     * </p>
     *
     * @param thread The thread for which the {@link EnterMonitorEvent} is being created.
     * @param lock   The lock that the thread wants to enter.
     * @return The created {@link EnterMonitorEvent} for the thread.
     */
    public static EnterMonitorEvent createEnterMonitorEvent(Thread thread, Object lock) {
        int serialNumber = getNextSerialNumber(thread);
        return new EnterMonitorEvent(threadIdMap.get(thread.getId()).intValue(),
                EventType.ENTER_MONITOR, serialNumber, createMonitor(lock));
    }

    /**
     * Creates an {@link ExitMonitorEvent} for a thread that is about to exit a monitor.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a {@code MONITOREXIT} operation. The method creates an {@link ExitMonitorEvent} for the thread and the
     * lock it wants to exit.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link ExitMonitorEvent} with the {@code EventType.EXIT_MONITOR}, the ID of the
     * thread, the generated serial number, and the monitor it wants to exit.
     * </p>
     *
     * @param thread The thread for which the {@link ExitMonitorEvent} is being created.
     * @param lock   The lock that the thread wants to exit.
     * @return The created {@link ExitMonitorEvent} for the thread.
     */
    public static ExitMonitorEvent createExitMonitorEvent(Thread thread, Object lock) {
        int serialNumber = getNextSerialNumber(thread);
        return new ExitMonitorEvent(threadIdMap.get(thread.getId()).intValue(),
                EventType.EXIT_MONITOR, serialNumber, createMonitor(lock));
    }

    /**
     * Creates a {@link SuspendEvent} for a thread that is about to be suspended.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when it decides to suspend a thread in the program under,
     * based on its strategy. The method creates a {@link SuspendEvent} for the thread and the monitor the thread wants
     * to acquire. The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link SuspendEvent} with the {@code EventType.SUSPEND}, the ID of the thread,
     * the generated serial number, and the monitor it wants to suspend.
     * </p>
     *
     * @param thread The thread for which the {@link SuspendEvent} is being created.
     * @param lock   The lock that the thread wants to suspend.
     * @return The created {@link SuspendEvent} for the thread.
     */
    public static SuspendEvent createSuspendEvent(Thread thread, Object lock) {
        int serialNumber = getNextSerialNumber(thread);
        mcThreadSerialNumber.put(threadIdMap.get(thread.getId()).intValue(), mcThreadSerialNumber.get(threadIdMap.get(thread.getId()).intValue()) - 1);
        return new SuspendEvent(EventType.SUSPEND, threadIdMap.get(thread.getId()).intValue(), serialNumber, createMonitor(lock));
    }

    /**
     * Creates a {@link SymExecutionEvent} for a thread that is about to execute a symbolic execution.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a symbolic execution. The method creates a {@link SymExecutionEvent} for the thread and the formula it
     * wants to execute symbolically. The method first generates a serial number for the event by calling the
     * {@link #getNextSerialNumber(Thread)} method. It then creates a new {@link SymExecutionEvent} with the
     * {@code EventType.SYM_EXECUTION}, the ID of the thread, the generated serial number, the result of the solver,
     * the formula to execute symbolically, and whether the formula is negatable.
     * </p>
     *
     * @param thread      The thread for which the {@link SymExecutionEvent} is being created.
     * @param formula     The formula that the thread wants to execute symbolically.
     * @param isNegatable Whether the formula is negatable.
     */
    public static SymExecutionEvent createSymExecutionEvent(Thread thread, String formula, boolean isNegatable) {
        int serialNumber = getNextSerialNumber(thread);
        return new SymExecutionEvent(threadIdMap.get(thread.getId()).intValue(), EventType.SYM_EXECUTION, serialNumber,
                solverResult, formula, isNegatable);
    }

    /**
     * Creates a {@link DeadlockEvent} for a thread that has encountered a deadlock.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a deadlock is detected in the program under test.
     * The method creates a {@link DeadlockEvent} for the thread that has encountered the deadlock.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link DeadlockEvent} with the {@code EventType.DEADLOCK} and the ID of the thread.
     * </p>
     *
     * @param thread The thread that has encountered the deadlock.
     * @return The created {@link DeadlockEvent} for the thread.
     */
    public static DeadlockEvent createDeadlockEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        return new DeadlockEvent(EventType.DEADLOCK, threadIdMap.get(thread.getId()).intValue(), serialNumber);
    }

    /**
     * Creates a {@link FailureEvent} for a thread that has encountered a failure.
     * <p>
     * This method is invoked by the {@link SchedulerThread} when a thread in the program under test has encountered a
     * failure. The method creates a {@link FailureEvent} for the thread that has encountered the failure.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link FailureEvent} with the {@code EventType.FAILURE} and the ID of the thread.
     * </p>
     *
     * @param thread The thread that has encountered the failure.
     * @return The created {@link FailureEvent} for the thread.
     */
    public static FailureEvent createFailureEvent(Thread thread) {
        int serialNumber = getNextSerialNumber(thread);
        return new FailureEvent(EventType.FAILURE, threadIdMap.get(thread.getId()).intValue(), serialNumber);
    }

    /**
     * Creates a {@link MonitorRequestEvent} for a thread that is about to request a monitor.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a {@code MONITORENTER} operation. The method creates a {@link MonitorRequestEvent} for the thread and
     * the lock it wants to enter.
     * The method first generates a serial number for the event by calling the {@link #getNextSerialNumber(Thread)}
     * method. It then creates a new {@link MonitorRequestEvent} with the {@code EventType.MONITOR_REQUEST}, the ID of
     * the thread, the generated serial number, and the monitor it wants to enter.
     * </p>
     *
     * @param thread The thread for which the {@link MonitorRequestEvent} is being created.
     * @param lock   The lock that the thread wants to enter.
     * @return The created {@link MonitorRequestEvent} for the thread.
     */
    public static MonitorRequestEvent createMonitorRequestEvent(Thread thread, Object lock) {
        int serialNumber = getNextSerialNumber(thread);
        return new MonitorRequestEvent(EventType.MONITOR_REQUEST, threadIdMap.get(thread.getId()).intValue(),
                serialNumber, createMonitor(lock));
    }

    /**
     * Creates a {@link Monitor} object for a lock that a thread is about to enter or exit.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a {@code MONITORENTER} or {@code MONITOREXIT} operation. The method creates a {@link Monitor} object for
     * the lock that the thread wants to enter or exit.
     * The method first retrieves the Class object of the lock and then creates a new {@link Monitor} with the Class
     * object and the lock.
     * The created {@link Monitor} object is then used to create an {@link EnterMonitorEvent} or {@link ExitMonitorEvent}
     * for the thread.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the lock.
     * </p>
     *
     * @param lock The lock for which the {@link Monitor} object is being created.
     * @return The created {@link Monitor} object for the lock.
     */
    private static Monitor createMonitor(Object lock) {
        Class<?> clazz = lock.getClass();
        return new Monitor(clazz, lock);
    }

    /**
     * Retrieves the {@link StaticMethodMonitor} for a synchronized static method that is about to be executed.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a synchronized static method. The method retrieves the {@link StaticMethodMonitor} for the method from
     * the {@link #staticMethodMonitorList} based on the class name, method name, and method descriptor.
     * If the {@link StaticMethodMonitor} for the method is not found in the list, a new {@link StaticMethodMonitor} is
     * created and added to the list.
     * </p>
     *
     * @param className  the fully qualified name of the class that contains the method.
     * @param methodName the name of the method.
     * @param descriptor the descriptor of the method.
     * @return the {@link StaticMethodMonitor} for the method.
     */

    public static StaticMethodMonitor getStaticMethodMonitor(String className, String methodName, String descriptor) {
        System.out.println("[Runtime Environment Message] : Getting StaticMethodMonitor for " + className + "." + methodName + descriptor);
        for (StaticMethodMonitor stm : staticMethodMonitorList) {
            if (stm.getClassName().equals(className) && stm.getMethodName().equals(methodName) && stm.getMethodDescriptor().equals(descriptor)) {
                return stm;
            }
        }
        StaticMethodMonitor staticMethodMonitor = new StaticMethodMonitor(className, methodName, descriptor);
        staticMethodMonitorList.add(staticMethodMonitor);
        return staticMethodMonitor;
    }

    /**
     * Retrieves the {@link InstanceMethodMonitor} for a synchronized instance method that is about to be executed.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a synchronized instance method. The method retrieves the {@link InstanceMethodMonitor} for the method
     * from the {@link #instanceMethodMonitorList} based on the object(this), method name, and method descriptor.
     * If the {@link InstanceMethodMonitor} for the method is not found in the list, a new {@link InstanceMethodMonitor}
     * is created and added to the list.
     * </p>
     *
     * @param obj        the object that contains the method.
     * @param methodName the name of the method.
     * @param descriptor the descriptor of the method.
     * @return the {@link InstanceMethodMonitor} for the method.
     */
    public static InstanceMethodMonitor getInstanceMethodMonitor(Object obj, String methodName, String descriptor) {
        System.out.println("[Runtime Environment Message] : Getting InstanceMethodMonitor for " + obj + "." + methodName + descriptor);
        for (InstanceMethodMonitor imm : instanceMethodMonitorList) {
            if (imm.getObject().equals(obj) && imm.getMethodName().equals(methodName) && imm.getMethodDescriptor().equals(descriptor)) {
                return imm;
            }
        }
        InstanceMethodMonitor instanceMethodMonitor = new InstanceMethodMonitor(obj, methodName, descriptor);
        instanceMethodMonitorList.add(instanceMethodMonitor);
        return instanceMethodMonitor;
    }

    /**
     * Creates a {@link Location} object for a thread that is about to perform a read or write operation on a field.
     * <p>
     * This method is invoked by the {@link RuntimeEnvironment} when a thread in the program under test is about to
     * execute a read or write operation. The method creates a {@link Location} object for the field that the thread
     * wants to access.
     * The method first retrieves the Class object of the owner class and the Field object of the field. It then
     * retrieves the current value of the field from the provided object. Using these, it creates a new {@link Location}
     * object with the Class object, the object instance, the Field object, the current value, and the descriptor of
     * the field.
     * The created {@link Location} object can then be used to create a {@link ReadEvent} or {@link WriteEvent} for the
     * thread.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the field.
     * </p>
     *
     * @param obj        The object that the thread wants to read from or write to.
     * @param owner      The fully qualified name of the class that the field belongs to.
     * @param name       The name of the field.
     * @param descriptor The type descriptor of the field.
     * @return The created {@link Location} object for the field.
     * @throws ClassNotFoundException if the owner class is not found. (Replaced by an assertion)
     * @throws NoSuchFieldException   if the field is not found in the owner class. (Replaced by an assertion)
     * @throws IllegalAccessException if the field is not accessible. (Replaced by an assertion)
     */
    private static Location createLocation(Object obj, String owner, String name, String descriptor) {
        try {
            System.out.println("[Runtime Environment Message] : Creating Location for " + owner + " $ " + name + " $ " + descriptor);
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


    private static Message createSimpleMessage(long threadId, Object value) {
        return new SimpleMessage(threadId, Thread.currentThread().getId(), value);
    }

    private static Message createTaggedMessage(long receiverTid, long senderTid, Object value, long tag) {
        return new TaggedMessage(receiverTid, senderTid, value, tag);
    }

    /**
     * Retrieves the next serial number for the events of a specific thread and increments it by one.
     * <p>
     * This method is invoked when a new event is being created for a thread in the program under test. The method
     * retrieves the current serial number for the thread's events from the {@link #mcThreadSerialNumber} map,
     * increments it by one, and then updates the map with the new serial number.
     * The serial number is used to order the events of a thread in the sequence they were created. This allows the
     * {@link SchedulerThread} to replay the events in the same order during the model checking process.
     * As this method is invoked in a single-threaded environment (guaranteed by the {@link SchedulerThread}), there is
     * no need to synchronize access to the {@link #mcThreadSerialNumber} map.
     * </p>
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
     * Creates finish object for the {@link org.mpisws.manager.ByteCodeManager} to indicate that the model checking
     * process is finished.
     * <p>
     * The finish object is a {@link Finished} object with a boolean field indicating whether the model checking process
     * should terminate and a {@link FinishedType} field indicating the type of the finish event.
     * It is serialized and written to a file named "finish.obj" in the "src/main/resources/finish" directory.
     * The file is then read by the {@link org.mpisws.manager.ByteCodeManager} to indicate that the model checking
     * process is finished.
     * </p>
     *
     * @throws RuntimeException if the file is not found or an IOException occurs.
     */
    private static void createFinishObject(FinishedType finishedType) {
        Finished finished = new Finished(true, finishedType);
        try (FileOutputStream fileOut = new FileOutputStream("src/main/resources/finish/finish.obj");
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(finished);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found");
        } catch (IOException e) {
            throw new RuntimeException("IOException");
        }
    }

    public static Thread findThreadObject(long jmcTid) {
        return threadObjectMap.get(jmcTid);
    }

    public static Thread findJVMThreadObject(long jvmTid) {
        return findThreadObject(threadIdMap.get(jvmTid));
    }

    public static void removeBlockedThreadFromReadyQueue(JMCThread jmcThread, ReceiveEvent receiveEvent) {
        readyThreadList.remove(jmcThread);
        blockedRecvThreadMap.put(threadIdMap.get(jmcThread.getId()), receiveEvent);
        BlockedRecvEvent blockedRecvEvent = createBlockedRecvEvent(jmcThread, receiveEvent);
        eventsRecord.add(blockedRecvEvent);
    }

    public static void addUnblockedThreadToReadyQueue(JMCThread jmcThread, ReceiveEvent receiveEvent) {
        readyThreadList.add(jmcThread);
        blockedRecvThreadMap.remove(threadIdMap.get(jmcThread.getId()), receiveEvent);
        UnblockedRecvEvent unblockedRecvEvent = createUnblockedRecvEvent(jmcThread, receiveEvent);
        eventsRecord.add(unblockedRecvEvent);
    }

    /**
     * Resets the {@link RuntimeEnvironment} to its initial state.
     * <p>
     * This method is invoked by the terminateExecution method after the execution of the program under test is
     * finished. It resets all the fields of the {@link RuntimeEnvironment} to their initial values, effectively
     * preparing the {@link RuntimeEnvironment} for the next execution of the program.
     * </p>
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
        mcThreadSerialNumber = new HashMap<>();
        writeEventReq = null;
        threadIdMap = new HashMap<>();
        executionFinished = false;
        eventsRecord = new ArrayList<>();
        suspendedThreads = new ArrayList<>();
        threadObjectMap = new HashMap<>();
        suspendPriority = new HashMap<>();
        if (solverType == null) {
            solver = new SymbolicSolver();
        } else {
            solver = new SymbolicSolver(solverType);
        }
        symbolicOperation = null;
        solverResult = false;
        threadParkingPermit = new HashMap<>();
        threadToPark = null;
        unparkerThread = null;
        staticMethodMonitorList = new ArrayList<>();
        instanceMethodMonitorList = new ArrayList<>();
        parkedThreadList = new ArrayList<>();
        blockedRecvThreadMap = new HashMap<>();
        threadExitMonitorReq = null;
        objectExitMonitorReq = null;
        blockingReceiveEventReq = null;
        sendEventReq = null;
        receiveEventReq = null;
        unparkeeThread = null;
    }
}