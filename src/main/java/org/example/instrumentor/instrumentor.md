# JMC Instrumentor

The `instrumentor` package in JMC is responsible for modifying the bytecode of the user program to enable the JMC runtime environment to control the scheduling of threads.

## Components

The package consists of only one main class:

- `ByteCodeModifier`: This class is responsible for modifying the bytecode of the user program. It identifies all threads creation, thread start points, and run methods in the user program and modifies them to interact with the JMC runtime environment.

## ByteCodeModifier Class

The `ByteCodeModifier` class contains several variable members and methods that are needed to modify the user program bytecode.

### Variables

- `nextVarIndex`: This variable is used to store the index of the next free local variable.
- `allByteCode`: This map stores the bytecode of the compiled classes. The key of the map is the name of the class and the value is the bytecode of the class. These classes are the user program classes that are to be modified.
- `mainClassName`: This variable is used to store the name of the main class of the user program. The main class is the class that contains the main method.
- `threadClassCandidate`: This list is used in iterative analysis to find all the classes that contains methods which create threads.
- `threadStartCandidate`: This list is used in iterative analysis to find all the classes that contains methods which start threads.
- `threadRunCandidate`: This list is used in iterative analysis to find all the classes that override the run method.
- `threadJoinCandidate`: This list is used in iterative analysis to find all the classes that contains methods which call the join method of a thread.


### Constructors

- `ByteCodeModifier(Map<String,byte[]> allByteCode,String mainClassName)`: This constructor initializes the `allByteCode` and `mainClassName` variables. `allByteCode` is a map that stores the bytecode of the compiled classes, and `mainClassName` is the name of the main class of the user program.

### Methods

- `addRuntimeEnvironment()`: This method adds the runtime environment to the main class. It modifies the bytecode of the main class to include instructions for initializing the runtime environment.

- `getNextVarIndex(byte[] byteCode, String methodName, String methodDescriptor)`: This method is used to get the index of the next free local variable in the bytecode of a specific method. It analyzes the bytecode, method name, and method descriptor to determine the next available index for a local variable.

- `modifyThreadCreation()`: This method is used to find all the points in the user program where threads are created. It iteratively analyzes the bytecode of the user program and identifies the points where new threads are created. When such a point is found, it modifies the bytecode to include a call to the `RuntimeEnvironment.addThread(Thread thread)` method, which adds the newly created thread to the `createdThreadList` of the Runtime Environment.

- `modifyThreadJoin` : This method is used to find all the points in the user program where threads are joined. It iteratively analyzes the bytecode of the user program and identifies the points where the `join()` method of a thread is called. When such a point is found, it modifies the bytecode to include a call to the `RuntimeEnvironment.threadJoin(Thread currentThread, Thread thread)` method, which makes the current thread wait until the joined thread finishes execution.

- `modifyThreadStart()`: This method is used to find all the points in the user program where threads are started. It iteratively analyzes the bytecode of the user program and identifies the points where the `start()` method of a thread is called. When such a point is found, it modifies the bytecode to include a call to the `RuntimeEnvironment.threadStart(Thread currentThread, Thread thread)` method, which adds the thread to the `readyThreadList` of the Runtime Environment.

- `modifyReadWriteOperation()`: This method is used to find all the points in the user program where fields are read and written. It iteratively analyzes the bytecode of the user program and identifies the points where `GETFIELD` and `PUTFIELD` instructions are used. When such a point is found, it modifies the bytecode to include a call to the `RuntimeEnvironment.ReadOperation(Object obj, Thread thread, String owner, String name, String descriptor)` or `RuntimeEnvironment.WriteOperation(Object obj, Object newVal, Thread thread, String owner, String name, String descriptor)` method, depending on whether it's a read or write operation.

- `modifyThreadRun()`: This method is used to find all the overridden `run` methods in the user program. It iteratively analyzes the bytecode of the user program and identifies the classes that override the `run` method of the `Thread` class. When such a class is found, it modifies the bytecode of the `run` method to include a call to the `RuntimeEnvironment.waitRequest(Thread thread)` method at the beginning of the method, which makes the thread wait until it gets permission to run.

- `modifyMonitorInstructions()`: This method is used to find all the points in the user program where monitor instructions are used. It analyzes all methods in the of the user program and identifies the points where `MONITORENTER` and `MONITOREXIT` instructions are used. When a monitor enter point is found, it modifies the bytecode to include a call to the `RuntimeEnvironment.enterMonitor(Object obj, Thread thread)` method before the `MONITORENTER` instruction and a call to the `RuntimeEnvironment.acquireMonitor(Object obj, Thread thread)` method after the `MONITORENTER` instruction. When a monitor exit point is found, it modifies the bytecode to include a call to the `RuntimeEnvironment.exitMonitor(Object obj, Thread thread)` method before the `MONITOREXIT` instruction and a call to the `RuntimeEnvironment.releaseMonitor(Object obj, Thread thread)` method after the `MONITOREXIT` instruction.

- `isCastableToThread(String className)`: This method checks if a class is castable to `Thread`. It uses reflection to load the class and checks if it is a subclass of `Thread` or implements the `Runnable` interface.