
# JMC Runtime Environment

The `runtime` package in JMC is responsible for establishing a higher-level environment setting over the JVM which enables the runtime environment to control the scheduling of threads in the modified user program. This setting enables JMC's scheduling algorithms to schedule the modified user program's threads, guiding their flow to detect all potential data races over shared variables between user program threads.


Throughout the remainder of this readme, we will use the term "user program" instead of "modified user program" for simplicity. For further details regarding the modifications made to the user program, please refer to the [JMC Instrumentation](../instrumentor/instrumentor.md) readme.

## Components

The package consists of two main classes:

- `SchedulerThread`: This class is responsible for controlling the flow of the program and deciding which thread to run next.  In other words, it is the scheduler of the user program threads.

- `RuntimeEnvironment`: This class provides a collection of class methods and class variables utilized for communication between user program threads and the SchedulerThread. This is achieved by defining control shared variables between them in the runtime environment. 

## SchedulerThread Class

The `SchedulerThread` class is a part of the JMC runtime environment and extends the `Thread` class. It is responsible for controlling the flow of the program and deciding which thread to run next in the user program.

### Scheduling Algorithm

Currently, the only implemented algorithm is Random Scheduling, which may overlook certain corner cases. However, we aim to implement a DPOR-based approach to cover all possible execution equivalence classes.


The `pickNextRandomThread()` method represents the implementation of Random Scheduling within the `SchedulerThread` class. When the `SchedulerThread` is initiated, there are at least one and at most two threads concurrently running. Throughout the entire execution of the user program, the `SchedulerThread` remains active. Upon initiation in `run()` method, it enters an idle loop, awaiting a "wait request" from a user program thread via the `RuntimeEnvironment` class. A thread makes this request when it encounters its next instruction to start another thread, completes its execution, enter a monitor object, or reads or writes a variable.

Upon receiving such requests, the `SchedulerThread` exits the idle loop and undergoes a synchronization phase to ensure that the requesting thread is in a wait state before entering the scheduling phase. During the scheduling phase, it examines the reason for the wait request. If the request is due to a thread wanting to finish, or a read or write operation, it calls the `pickNextRandomThread()` method.

However, if the request is due to a new thread wanting to start, or a thread wanting to enter a monitor object,  the `SchedulerThread` handles it differently. To explain the former case, as a recall, when the `SchedulerThread` wants to run a thread, it notifies it by calling the notify() method over it's lock object. A thread that is ready to start but has not waited on a lock object cannot be notified at all. To address this scenario, when the `SchedulerThread` identifies that a wait request is due to the start of a thread, it always selects that thread to run by calling its start method, before returning to its idle loop. The newly started thread, modified beforehand by the `instrumentor` package, begins with a wait request as its first instruction. Upon executing this instruction, the `SchedulerThread` re-enters its scheduling phase to select a random thread for the next run.

In the latter case, when a thread wants to enter a monitor object, the `SchedulerThread` adds the thread and the monitor object to the `monitorRequest` map. Next, the `SchedulerThread` enters a new phase, called the "Monitor Deadlock Detection" phase. In this phase, the `SchedulerThread` checks if the new enter monitor request will cause a deadlock between the threads or not. If it does, the `SchedulerThread` declares the existence of a deadlock and terminates the user program. If not, the `SchedulerThread` selects a random thread to run and returns to its idle loop. 

The `pickNextRandomThread()` method checks the number of thread in the `readyThreadList` of the `RuntimeEnvironment` class. If there is only one thread, it selects that thread to run. If there is no thread, it starts to terminate. However, if there are more than one thread, things get a bit more complicated. The `SchedulerThread` selects a random thread from the `readyThreadList` and checks if the selected thread is requested for a monitor by checking the `monitorRequest` map. If the selected thread is requested for a monitor, the `SchedulerThread` checks if the monitor object is free. If the monitor object is free, the `SchedulerThread` selects the selected thread to run. If the monitor object is not free, the `SchedulerThread` selects another random thread from the `readyThreadList`. If the selected thread is not requested for a monitor, the `SchedulerThread` selects the selected thread to run.

The `monitorsDeadlockDetection()` method in the `SchedulerThread` class is used to detect potential deadlocks in the system. A deadlock is a situation where a set of threads are unable to proceed because each thread is waiting for resources held by another thread in the set. In this case, the resources are monitor objects that threads need to acquire to proceed with their execution.

The method works by checking if a new request to enter a monitor (a synchronization object in Java) will cause a deadlock among the threads. This is done whenever a thread requests to enter a monitor object.

The method uses a simple deadlock detection algorithm. The algorithm works by creating a map of thread pairs that are in the transitive closure of the union of the `monitorRequest` and `monitorList` relations. The `monitorRequest` map stores the threads that are waiting to enter a monitor, and the `monitorList` map stores the monitor objects and the threads that are currently holding them.

The algorithm first computes the transitive closure of the union of `monitorRequest` and `monitorList` relations. After computing the transitive closure, the algorithm checks if the relation is irreflexive. A relation is irreflexive if no element is related to itself.

If the algorithm detects a deadlock, it sets the `isDeadlock` variable to `true` and the `SchedulerThread` declares the existence of a deadlock and terminates the user program. If no deadlock is detected, the `SchedulerThread` proceeds to select a random thread to run and returns to its idle loop.

This method is crucial in preventing deadlocks in the system and ensuring the smooth execution of the user program.

Once all user program threads have finished, the `SchedulerThread` itself will also finish. The detailed explanation of the Random Scheduling of the `SchedulerThread` is as follows.


## RuntimeEnvironment Class

The `RuntimeEnvironment` class contains several static variables and methods that are needed to manage the threads of the user program.

### Variables

- `threadCount`: Used to generate the name of the threads as "Thread-threadCount++" when a `addThread(Thread thread)` method is called.
- `threadWaitReq`: Stores the thread that requested to wait.
- `threadWaitReqLock`: Used to synchronize the access to `threadWaitReq`.
- `threadStartReq`: Stores the thread that requested to start.
- `locks`: Stores a lock object for each specific thread. The key is the id of the thread and the value is the lock object.
- `createdThreadList`: Stores the threads that are created.
- `readyThreadList`: Stores the threads that are ready to run.
- `monitorList` : Stores the monitor objects and the threads that are waiting to enter the monitor.

### Methods

- `init(Thread main)`: Initializes the Runtime Environment. It is called by the `main` thread of the user program as a first instruction of the program.
- `addThread(Thread thread)`: Adds the `thread` to the `createdThreadList` of the Runtime Environment. It is called when a new object of the Thread class(or castable to Thread class) is created.
- `threadStart(Thread currentThread, Thread thread)`: Adds the `thread` to the `readyThreadList` of the Runtime Environment. It is called when the start() method of a thread is called.
- `getPermission(Thread thread)`: Gives the permission to the `thread` to run. For now, It is only called by the SchedulerThread to give the permission from the `main` thread to run.
- `waitRequest(Thread thread)`: Used by the `thread` to request to wait.
- `initSchedulerThread(Thread main, Thread st)`: Initializes the SchedulerThread. It is called by the `main` method of the user program.
- `finishThreadRequest(Thread thread)`: Used by the `thread` to request to finish.
- `ReadOperation(Object obj, Thread thread, String owner, String name, String descriptor)`: Used by the `thread` when its next instruction is a read operation(`GETFIELD`).
- `WriteOperation(Object obj, Object newVal, Thread thread, String owner, String name, String descriptor)`: Used by the `thread` when its next instruction is a write operation(`PUTFIELD`).
- `enterMonitor(Object lock, Thread thread)`: This method is used by the `thread` when its next instruction is a monitor enter(MONITORENTER). It is called by the `thread` to request to enter the monitor of the `lock`.
- `exitMonitor(Object lock, Thread thread)`: This method is used by the `thread` when its next instruction is a monitor exit(MONITOREXIT). It is called by the `thread` to request to exit the monitor of the `lock`.
- `acquiredLock(Object lock, Thread thread)`: This method is used by the `thread` when it acquired the `lock`. It is called by the `thread` to inform the Runtime Environment that it acquired the `lock`.
- `releasedLock(Object lock, Thread thread)`: This method is used by the `thread` when it released the `lock`. It is called by the `thread` to inform the Runtime Environment that it released the `lock`.


## Future Work

We aim to implement a DPOR-based approach to cover all possible execution equivalence classes, enhancing the effectiveness of our runtime environment in detecting potential data races.