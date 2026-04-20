# JMC — Java Model Checker

A simple, easy to use, intuitive stateless model checker for finding concurrency bugs such as data races, atomicity 
violations, assertion failures, and deadlocks in Java programs.

Take the following example where we have a counter class with two methods to increment and get the value of the counter:

```java
class Counter {
    int value = 0;
    
    void inc() {
        value++;
    }
    
    int get() {
        return value;
    }
}
```
Normally, you would write a test that spawns multiple threads to increment the counter and check the final value:

```java
void testCounter() {
    Counter counter = new Counter();
    
    Thread t1 = new Thread(() -> counter.inc());
    Thread t2 = new Thread(() -> counter.inc());
    
    t1.start();
    t2.start();
    
    t1.join();
    t2.join();
    
    assertEquals(2, counter.get());
}
```
In order to find any potential bugs in this test, all you have to do is annotate the test method with `JmcCheck` and 
`JmcCheckConfiguration` as shown below:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 100)
void testCounter() {
    // ... same code as above
}
```
Then, when you run the test, JMC systematically explores thread interleavings to find any concurrency bugs.
If it finds a bug, it will report the schedule that triggers the failure, allowing you to reproduce and fix the issue.

As a result of running the test, JMC will find a bug in the `Counter` class where the final value of the counter is `1`
instead of `2` due to a lost update race. JMC will report the interleaving that leads to this bug, which is:

```text
Main               | T1                  | T2
-------------------|---------------------|--------------------
1. Start(T1)       |                     |
2. Start(T2)       |                     |
                   | 3. Read(value, 0)   |
                   |                     | 4. Read(value, 0)
                   | 5. Write(value, 1)  |
                   |                     | 6. Write(value, 1)
7. Join(T1)        |                     |
8. Join(T2)        |                     |
9. Read(value, 1)  |                     |
```
## Exploration Strategies

JMC supports multiple scheduling strategies to explore thread interleavings. Mainly JMC supports 4 types of strategies,
`random`, `systematic`, `estimation`, and `replay`.

### Random Testing

The `random` strategy employs a randomized approach to explore thread interleavings. It randomly selects 
enabled threads at each scheduling point, providing a quick way to find bugs without exhaustive exploration. While it
may not guarantee finding all bugs, it can be effective for large state spaces where systematic exploration is infeasible.

Note that `random` is the default strategy in the `JmcCheckConfiguration`, so you don't need to specify it explicitly
if you want to use it.

### Systematic Exploration

The `systematic` strategy explores systematically all the necessary and sufficient interleavings to find all existing bugs.
This strategy employs dynamic partial-order reduction (DPOR) to reduce the exhaustive search space into the minimal set
of interleavings that none of them is equivalent to another, and thus guarantees finding all bugs. Note that you should 
expect a longer execution time when using this strategy compared to `random`, especially for tests with a large state space.

#### Trust

The `trust` strategy is a state-of-the-art DPOR-based model checking algorithm for shared-memory concurrent programs. 
Simply put, `trust` starts with a random schedule and enumerates all possible distinct interleavings by commuting
thread operations based on some dependence relation. 

In order to use the `trust` strategy, you can specify it in the `JmcCheckConfiguration` annotation as shown below:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 100, strategy = "trust")
void testCounter() {
    // ... same code as above
        
}
```

#### Must

TBA

#### ConDpor

TBA

### Estimation-Based Strategies

#### Testor

TBA

#### Pestor

TBA

### Replay

TBA

## Requirements

- Java 17+
- Gradle 8.4+

## Quick Start

Add the JMC Gradle plugin to your `build.gradle.kts`:

```kotlin
plugins {
    java
    id("org.mpi_sws.jmc.gradle") version "0.1.2"
}

jmc {
    version = "0.1.2"
    instrumentingPackage = listOf("com.example")
}
```

The plugin automatically resolves the JMC agent and library JARs, adds the
library as a `testImplementation` dependency, and attaches the agent to all
`Test` tasks.

If building from source, add `mavenLocal()` to both `repositories` and
`pluginManagement.repositories` in your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

### Plugin Configuration

```kotlin
jmc {
    version = "0.1.2"                                    // JMC version
    instrumentingPackage = listOf("com.example.myapp")   // packages to instrument
    excludedPackages = listOf("com.example.thirdparty")  // packages to skip
    debug = true                                         // dump instrumented bytecode
    debugPath = "build/generated/instrumented"            // where to dump it
}
```

## How JMC Works

JMC operates in two phases: bytecode instrumentation and controlled execution.

A Java agent instruments your compiled bytecode at class-load time, inserting
yield points at concurrency-relevant operations. During execution, the JMC
runtime serializes thread execution — only one thread runs at a time, with a
pluggable scheduler deciding which thread to resume at each yield point. Across
multiple iterations, different scheduling decisions explore different
interleavings.

You write normal Java code. The agent handles everything automatically.

### Example: Detecting a Data Race

Consider a simple shared counter:

```java
class Counter {
    int value = 0;

    void increment() {
        value++;
    }
}
```

`value++` looks atomic in source code, but compiles to separate `GETFIELD`,
`IADD`, and `PUTFIELD` bytecode instructions. Between the read and write,
another thread can read the same stale value — the classic lost-update race.

Two threads each incrementing once should produce `value == 2`, but under
certain interleavings the result is `1`:

```
T1: read value (0)
T2: read value (0)
T1: write value (1)
T2: write value (1)    ← lost update
```

### What the Agent Does

The agent transforms your code at load time. You write standard Java
concurrency primitives — `Thread`, `ReentrantLock`, `ExecutorService`, etc. —
and the agent replaces them with JMC-controlled equivalents and inserts yield
points at field accesses.

Before instrumentation:

```java
public class CounterRaceTest extends Thread {
    ReentrantLock lock;
    Counter counter;

    @Override
    public void run() {
        try {
            lock.lock();
            counter.increment();
        } finally {
            lock.unlock();
        }
    }
}
```

After instrumentation by the JMC agent:

```java
public class CounterRaceTest extends JmcThread {
    JmcReentrantLock lock;
    Counter counter;

    @Override
    public void run1() {
        try {
            JmcRuntimeUtils.readEventWithoutYield(...);
            JmcReentrantLock var10000 = this.lock;
            JmcRuntime.yield();
            var10000.lock();
            JmcRuntimeUtils.readEventWithoutYield(...);
            Counter var4 = this.counter;
            JmcRuntime.yield();
            var4.increment();
        } finally {
            JmcRuntimeUtils.readEventWithoutYield(...);
            JmcReentrantLock var5 = this.lock;
            JmcRuntime.yield();
            var5.unlock();
        }
    }
}
```

`Thread` → `JmcThread`, `ReentrantLock` → `JmcReentrantLock`, and
`JmcRuntime.yield()` calls are inserted at field accesses to create scheduling
points where the model checker can switch threads.

## Writing a Test

Annotate test methods with `@JmcCheck` to run them under the model checker.
No special types needed in your test code — use standard Java concurrency.

```java
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "random")
    void testCounterRace() throws Exception {
        Counter counter = new Counter();

        Thread t1 = new Thread(() -> counter.increment());
        Thread t2 = new Thread(() -> counter.increment());

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertEquals(2, counter.getValue());
    }
}
```

Run it:

```bash
./gradlew test --tests com.example.CounterTest.testCounterRace
```

JMC will systematically explore interleavings and report the schedule that
triggers the assertion failure.

### Using Different Strategies

The same test with the `trust` strategy for systematic exploration:

```java

@JmcCheck
@JmcCheckConfiguration(numIterations = 100, strategy = "trust", debug = true)
void testCounterRaceTrust() throws Exception {
    Counter counter = new Counter();

    Thread t1 = new Thread(() -> counter.increment());
    Thread t2 = new Thread(() -> counter.increment());

    t1.start();
    t2.start();

    t1.join();
    t2.join();

    assertEquals(2, counter.getValue());
}
```

With `debug = true` and the `trust` strategy, JMC records execution graphs
that can be visualized (see [Visualizing Execution Graphs](#visualizing-execution-graphs)).

## Configuration

### @JmcCheckConfiguration

| Parameter       | Type    | Description                                 | Default                         |
|-----------------|---------|---------------------------------------------|---------------------------------|
| `strategy`      | String  | Scheduling strategy                         | `"random"`                      |
| `numIterations` | int     | Upper bound on interleavings to explore     | —                               |
| `debug`         | boolean | Store execution graphs and exploration logs | `false`                         |
| `reportPath`    | String  | Path for generated reports                  | `build/test-results/jmc-report` |
| `seed`          | long    | RNG seed for reproducibility                | —                               |

### @JmcTimeout

Sets a time limit on the exploration. Accepts a `value` (long) and a `unit`
(TimeUnit).

Either `@JmcTimeout` or `numIterations` is required — the test will fail if
neither is specified.

## Scheduling Strategies

| Strategy              | Type         | Description                                  |
|-----------------------|--------------|----------------------------------------------|
| `random`              | Randomized   | Randomly selects thread interleavings        |
| `trust`               | Systematic   | Execution-graph-based exhaustive exploration |
| `dag-estimation`      | Estimation   | DAG-based interleaving estimation            |
| `abs-dag-estimation`  | Estimation   | Abstract DAG estimation                      |
| `fj-dag-estimation`   | Estimation   | Fork-join DAG estimation                     |
| `trust-estimation`    | Estimation   | Trust-based estimation                       |
| `wg-trust-estimation` | Estimation   | Weighted-graph trust estimation              |
| `testor`              | Budget-aware | Budget-constrained testing strategy          |

## Bytecode Instrumentation

The JMC agent (`jmc-agent`) instruments classes at load time using ASM visitors.
Most concurrency-relevant operations are intercepted automatically without
requiring source-level changes.

What the agent instruments:

- Field reads and writes (instance and static) — scheduling yield points
- Synchronized methods and blocks
- Static initializers (class loading races)
- `wait()` / `notify()` / `notifyAll()`
- Native method bridges
- Atomic operations (`AtomicInteger`, `AtomicReference`, etc.)
- `Future` and `CompletableFuture` interactions
- `ReentrantLock` operations
- `ScheduledExecutorService` scheduling
- Lambda and `invokedynamic` call sites
- Enum classes are skipped (no mutable state)
- Final fields are skipped (immutable after construction)

## Supported Concurrency Primitives

JMC provides model-checker-aware replacements for standard `java.util.concurrent`
types. The agent swaps these in automatically during instrumentation.

**Threading:**

- `JmcThread` — Thread
- `JmcThreadFactory` — ThreadFactory

**Executors:**

- `JmcExecutorService` — ExecutorService
- `JmcScheduledExecutorService` — ScheduledExecutorService
- `JmcThreadPoolExecutor` — ThreadPoolExecutor
- `JmcExecutors` — Executors factory methods

**Futures:**

- `JmcFuture` — Future
- `JmcScheduledFuture` — ScheduledFuture
- `JmcCompletableFuture` — CompletableFuture

**Atomics:**

- `JmcAtomicBoolean` — AtomicBoolean
- `JmcAtomicInteger` — AtomicInteger
- `JmcAtomicLong` — AtomicLong
- `JmcAtomicReference` — AtomicReference
- `JmcAtomicReferenceArray` — AtomicReferenceArray
- `JmcAtomicStampedReference` — AtomicStampedReference

**Synchronization:**

- `JmcReentrantLock` — ReentrantLock
- `JmcLockSupport` — LockSupport

**Utilities:**

- `JmcRandom` — deterministic Random for reproducible runs
- `JmcAssert` / `JmcAssume` — model-checker-aware assertions and assumptions

## Visualizing Execution Graphs

When using the `trust` strategy with `debug = true`, JMC records execution
graphs to `build/test-results/jmc-report/`.

Visualize them with:

```bash
./scripts/visualize.sh <path-to-graph-files>
```

This starts a local web server at `http://localhost:8000`.

## Project Structure

| Module             | Description                                              |
|--------------------|----------------------------------------------------------|
| `core`             | Model checker engine, strategies, runtime, and API types |
| `agent`            | Bytecode instrumentation agent (ASM-based)               |
| `gradle-plugin`    | Gradle plugin for automatic agent attachment             |
| `integration-test` | Integration tests exercising the full stack              |

## Building from Source

```bash
git clone https://github.com/mpi-sws-rse/jmc.git
cd jmc
./gradlew clean
./gradlew :core:publish
./gradlew :agent:publish
./gradlew :gradle-plugin:publishToMavenLocal
```

To run integration tests:

```bash
./gradlew :integration-test:test --tests org.mpi_sws.jmc.test.<TestName>
```

## Documentation

See the `docs/` directory for detailed guides:

- [User Guide](docs/User%20guide.md)
- [Configuration API](docs/Configuration%20API.md)
- [Gradle Example Project Setup](docs/Gradle%20Example%20Project%20Setup.md)

## License

Apache License 2.0
