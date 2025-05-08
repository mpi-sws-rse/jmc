The Java model checker aims to verify generic Java programs for safety and liveness issues. Internally, it uses advancements in Dynamic Partial Order Reduction algorithms such as [Must](https://dl.acm.org/doi/pdf/10.1145/3689778) and [TruSt](https://dl.acm.org/doi/pdf/10.1145/3498711) to explore all possible executions of a concurrent or distributed program.

To do so, JMC needs control over access to shared variables (in concurrent programs) and to the transport primitives (in distributed programs). JMC gains control by instrumenting the Java byte-code as a first step and then systematically explores the different inter-leavings of threads/messages by enumerating executions.

In this document, we will outline the architecture of JMC, list its various internal components, and their interactions.
## Getting Started

The simplest way to set up and run JMC is to run one of its tests
```
./gradlew test --tests 'org.mpisws.checker.ModelCheckerTest.<test name>'
```

Each test, initializes the [[ModelChecker]] with a [[Configuration]] and invokes the `check` method with the target.

Build the project using _gradle_ or the packaged wrapper `gradlew`
```
./gradlew build -x test
```
## Architecture

The JMC model checker contains the following major components. Some of them are represented by a single class.

- [[ModelChecker]]
- [[Concurrent primitives]]
- [[Runtime]]
- [[Byte code Modifier]]
- [[Symbolic execution engine]]
- [[Scheduler]]

## Developing

Refer to the [[Developing]] guide.