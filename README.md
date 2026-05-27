# JMC — Java Model Checker

A simple, easy to use, intuitive, stateless model checker for finding concurrency bugs in Java programs, including data races, atomicity violations, assertion failures, and deadlocks.

Consider a `Counter` class with two methods, one to `inc`rement the counter and one to `get` its value:

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

Normally you would write a test that spawns multiple threads to increment the counter and then check the final value:

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

To find potential bugs in this test, all you need to do is annotate the test method with `@JmcCheck` and `@JmcCheckConfiguration`:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 100)
void testCounter() {
    // ... same code as above
}
```

When you run the test, JMC systematically explores thread interleavings to uncover concurrency bugs. If it finds one, it reports the schedule that triggers the failure so you can reproduce and fix the issue.

In this example, JMC finds a bug in the `Counter` class: the final value ends up as `1` instead of `2` because of a lost-update race. JMC reports the interleaving that leads to the bug:

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

JMC supports four families of scheduling strategies for exploring thread interleavings: `random`, `systematic`, `estimation`, and `replay`.

### Random Testing

The `random` strategy explores thread interleavings with a randomized approach, selecting an enabled thread at each scheduling point at random. It offers a quick way to find bugs without exhaustive exploration. While it does not guarantee that every bug is found, it is effective for large state spaces where systematic exploration is infeasible.

`random` is the default strategy in `JmcCheckConfiguration`, so you do not need to specify it explicitly.

### Systematic Exploration

The `systematic` strategy explores all of the necessary and sufficient interleavings to find every existing bug. It uses dynamic partial-order reduction (DPOR) to reduce the exhaustive search space to a minimal set of interleavings, none of which is equivalent to another, and thus guarantees that all bugs are found. Expect a longer execution time than with `random`, especially for tests with a large state space.

#### Trust

The `trust` strategy is a state-of-the-art, DPOR-based model checking algorithm [1] for shared-memory concurrent programs. In short, `trust` starts from a random schedule and enumerates all distinct interleavings by commuting thread operations according to a dependence relation.

To use the `trust` strategy, specify it in the `JmcCheckConfiguration` annotation:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 100, strategy = "trust")
void testCounter() {
    // ... same code as above
}
```

#### ConDpor

The `ConDpor` strategy is a state-of-the-art, optimal concolic DPOR-based model checking algorithm [2] designed to handle data non-determinism in addition to scheduling non-determinism. In concurrent programs, data non-determinism arises from sources such as user input, network delays, and random value generation. `ConDpor` combines an optimized symbolic execution engine, which resolves data non-determinism, with a DPOR-based exploration technique to find all bugs caused by both scheduling and data non-determinism.

Returning to the counter example: if we modify the threads so that each one tosses a coin to decide whether to increment the counter, the test now exhibits data non-determinism on top of scheduling non-determinism. Using the `ConDpor` API, you can model this behavior as follows:

```java
void testRandomCounter() {
   Counter counter = new Counter();

   Thread t1 = new Thread(() -> {
      SymbolicBoolean x = new SymbolicBoolean("x"); // Define a symbolic boolean variable x
      SymbolicFormula smFrm = new SymbolicFormula(); // Create a symbolic formula manager
      if (smFrm.evaluate(x)) { // Evaluate x
         counter.inc();
      }
   });

   Thread t2 = new Thread(() -> {
      // Same code as t1
   });

   t1.start();
   t2.start();

   t1.join();
   t2.join();
}
```

Under the hood, `ConDpor` explores all possible interleavings of the threads while also considering both possible values of the symbolic variable `x` (`true` and `false`). To use the `ConDpor` strategy, specify it in the `JmcCheckConfiguration` annotation:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 100, strategy = "trust", solver = "z3")
void testRandomCounter() {
    // ... same code as above
}
```

You can choose the solver used for concolic execution with the `solver` parameter. Supported SMT solvers include `z3`, `cvc5`, `cvc4`, `mathsat5`, `yices2`, `opensmt`, `smtinterpol`, `princess`, and `boolector`.

The `ConDpor` API supports the boolean and integer theories, so you can define symbolic variables of these types and combine them into unquantified symbolic formulas, which are then evaluated to obtain concrete values for the symbolic variables.

#### Must

To specify and model check distributed protocols implemented in Java, JMC provides the `must` strategy, a DPOR-based algorithm designed to handle several communication models, including FIFO peer-to-peer, mailbox-based, and fully asynchronous [3]. The strategy is paired with an API that lets you specify your protocol's communication model, allowing JMC to explore the necessary and sufficient interleavings to find all bugs in the protocol under that model.

The `must` strategy is not yet available in the current version of JMC. An early implementation lives in the `old-main` branch of the repository, and we plan to merge a refactored version into the main branch in the near future.

### State-Space Estimation

JMC also supports several estimation-based strategies that estimate the size of a concurrent program's state space using a DPOR-based representation of interleavings. In other words, JMC can estimate the number of distinct interleavings of a concurrent program without exploring all of them. This is useful for comparing the complexity of different tests or deciding which strategy to use for a given test. It can also indicate how long a systematic exploration is likely to take, and, when paired with the random strategy, it gives a precise notion of coverage as the percentage of interleavings explored out of the total.

#### Testor

The `testor` strategy is a budget-aware, polynomial-time, unbiased estimation algorithm (Algorithm S) [4] built on top of stateless DPOR-based model checking and stochastic enumeration techniques. It estimates the size of the state space by running `b` (budget) copies of the Trust-based estimation algorithm (Algorithm T) [4] in parallel, producing a precise estimate within a reasonably short time and modest budget, even for tests with a large state space.

To use the `testor` strategy, specify it in the `JmcCheckConfiguration` annotation:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 10, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO,
        strategy = "testor", budget = 3, timeout = 60000L)
public void testCounter() {
   // ... same code as above
}
```

The `numIterations` parameter sets the number of trials of the `testor` algorithm to run, and the `budget` parameter sets the number of parallel copies of Algorithm T to run in each trial. The `timeout` parameter sets the time limit, in milliseconds, for the entire `testor` run across all trials, giving you control over the algorithm's execution time.

The `schedulingPolicy` parameter selects the scheduling policy for the underlying Trust-based estimation algorithm (Algorithm T), either `FIFO` or `LIFO`. The estimation result of each trial is stored in a file under `build/test-results/jmc-report/`.

Note that with a budget of 1, the `testor` strategy is equivalent to running Algorithm T. To run Algorithm T, we recommend its optimized implementation, the `trust-estimation` strategy, which you can specify as follows:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 10, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO,
        strategy = "trust-estimation", timeout = 60000L)
public void testCounter() {
   // ... same code as above
}
```

To reduce the variance of Algorithm T, we implemented a variant, `wg-trust-estimation`, which assigns weights to the forward and backward revisit options in the algorithm. You can specify it as follows:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 10, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO,
        strategy = "wg-trust-estimation", timeout = 60000L)
public void testCounter() {
   // ... same code as above
}
```

#### Pestor

The `pestor` strategy is a polynomial-time, unbiased estimation algorithm based on Algorithm P [4] that runs on top of the `random` strategy. Compared to `testor`, it has higher variance but runs faster per trial because it relies on a lighter-weight exploration strategy. To use the `pestor` strategy, specify it in the `JmcCheckConfiguration` annotation:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 10, strategy = "pestor", timeout = 60000L)
public void testCounter() {
   // ... same code as above
}
```

To reduce the variance of the `pestor` strategy, we implemented a variant, `fj-pestor`, designed for fork-join-style concurrent programs. It uses a custom scheduling strategy that excludes thread creation and termination events from scheduling decisions, which reduces estimation variance for such programs. You can specify it as follows:

```java
@JmcCheck
@JmcCheckConfiguration(numIterations = 10, strategy = "fj-pestor", timeout = 60000L)
public void testCounter() {
   // ... same code as above
}
```

### Replay

When JMC finds a bug, it reports the schedule that triggers the failure and saves the trace under `build/test-results/jmc-report/`. You can then use this schedule to replay the execution and reproduce the bug, which is helpful for debugging and fixing the issue. To replay a schedule, annotate the test method with `@JmcReplay`:

```java
@JmcCheck
@JmcReplay
void testCounter() {
    // ... same code as above
}
```

## Requirements

- Java 17+
- Gradle 8.4+

## Getting Started

### Simple Project

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

The plugin resolves the JMC agent and library JARs, adds the library as a `testImplementation` dependency, and attaches the agent to all `Test` tasks. Run your tests as usual:

```bash
./gradlew test --tests com.example.CounterTest
```

If you are building from source, add `mavenLocal()` to both `repositories` and `pluginManagement.repositories` in your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

### Multi-Project Build

For projects that mix JMC and non-JMC tests (e.g. Apache Iceberg), use `target` to point at a subproject and `testTask` to create a dedicated task. This keeps the regular `test` task unaffected.

In the root `build.gradle`:

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
        mavenLocal()
    }
    dependencies {
        classpath 'org.mpi_sws.jmc.gradle:org.mpi_sws.jmc.gradle.gradle.plugin:0.1.2'
    }
}

apply plugin: 'org.mpi_sws.jmc.gradle'
jmc {
    version = "0.1.2"
    target = ":iceberg-core"
    testTask = "jmcTest"
    instrumentingPackage = ["org.apache.iceberg"]
    excludedPackages = ["org.apache.iceberg.relocated"]
}
```

The plugin creates a `jmcTest` task on the target subproject. Run JMC tests with:

```bash
./gradlew :iceberg-core:jmcTest --tests org.apache.iceberg.TestInMemoryCatalogJmc
```

Regular tests still run normally with `./gradlew :iceberg-core:test`.

### Plugin Configuration

| Property               | Type    | Description                                                                                 | Default                          |
|------------------------|---------|---------------------------------------------------------------------------------------------|----------------------------------|
| `version`              | String  | JMC version                                                                                 | `"0.1.2"`                        |
| `instrumentingPackage` | List    | Packages to instrument                                                                      | `[]`                             |
| `excludedPackages`     | List    | Packages to skip                                                                            | `[]`                             |
| `target`               | String  | Subproject path (e.g. `":iceberg-core"`). Empty = current project.                          | `""`                             |
| `testTask`             | String  | Task name (e.g. `"jmcTest"`). Creates the task if it doesn't exist. Empty = all Test tasks. | `""`                             |
| `debug`                | boolean | Dump instrumented bytecode to disk                                                          | `false`                          |
| `debugPath`            | String  | Where to dump instrumented bytecode                                                         | `"build/generated/instrumented"` |

## Documentation

See the [User Guide](https://jmc.mpi-sws.org/user_guide/) for a comprehensive introduction to JMC's internals, installation instructions, usage examples, and best practices.

## References

[1] Michalis Kokologiannakis, Iason Marmanis, Vladimir Gladstein, and Viktor Vafeiadis. "Truly stateless, optimal dynamic partial order reduction." Proceedings of the ACM on Programming Languages 6, no. POPL (2022): 1–28.

[2] Mohammad Hossein Khoshechin Jorshari, Michalis Kokologiannakis, Rupak Majumdar, and Srinidhi Nagendra. "Optimal Concolic Dynamic Partial Order Reduction." In 36th International Conference on Concurrency Theory (CONCUR 2025), pp. 26-1. Schloss Dagstuhl–Leibniz-Zentrum für Informatik, 2025.

[3] Constantin Enea, Dimitra Giannakopoulou, Michalis Kokologiannakis, and Rupak Majumdar. "Model checking distributed protocols in must." Proceedings of the ACM on Programming Languages 8, no. OOPSLA2 (2024): 1900–1927.

[4] A. R. Balasubramanian, Mohammad Hossein Khoshechin Jorshari, Rupak Majumdar, Umang Mathur, and Minjian Zhang. "State Space Estimation for DPOR-based Model Checkers." arXiv e-prints (2025): arXiv-2512.

## License

Apache License 2.0