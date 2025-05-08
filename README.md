# JMC model checker

A simple, easy to use, intuitive Java model checker.

Take the following example use case:

```java

@Test
void testRandomBuggyCounter() {
    JmcCheckerConfiguration config =
            new JmcCheckerConfiguration.Builder().numIterations(10).build();
    JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

    JmcTestTarget target =
            new JmcFunctionalTestTarget(
                    "RandomBuggyCounter",
                    () -> {
                        BuggyCounter.main(new String[0]);
                    });

    jmcModelChecker.check(target);
}
```

where `BuggyCounter` is a simple buggy counter class:

```java
public class BuggyCounter {
    private int count = 0;

    public static void main(String[] args) {
        BuggyCounter buggyCounter = new BuggyCounter();

        JmcThread thread1 = new JmcThread(() -> count++);
        JmcThread thread2 = new JmcThread(() -> count++);

        thread1.start();
        thread2.start();

        try {
            thread1.join1();
            thread2.join1();
            assert buggyCounter.getCount() == 2;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

Without any locks in place to synchronize shared access between the threads, the model checker will find the execution
where the assertion fails.

It will do so by exploring all possible interleavings of the threads. We gain control of the interleaving by extending
the Thread class to JmcThread and implementing control yields in the start and join1 methods.

Alternatively by using locks to synchronize access to the shared variable count:

```java
public class CorrectCounter {
    private int count = 0;
    private JmcRenetrantLock lock = new JmcRenetrantLock();

    public static void main(String[] args) {
        BuggyCounter buggyCounter = new BuggyCounter();

        JmcThread thread1 = new JmcThread(() -> {
            lock.lock();
            count++;
            lock.unlock();
        });
        JmcThread thread2 = new JmcThread(() -> {
            lock.lock();
            count++;
            lock.unlock();
        });

        thread1.start();
        thread2.start();

        try {
            thread1.join1();
            thread2.join1();
            assert buggyCounter.getCount() == 2;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

we can verify that the assertion will never fail by exploring all possible interleavings of the threads.

## Running integration tests

To run the tests, you can use the following command:

```bash
./gradlew clean
./gradlew :core:publish
./gradlew :integration-test:test --tests org.mpisws.jmc.test.<test name>
```

An integration test brings together the different components to effectively test the system using JMC.
The different components are

1. **Instrumentation Agent** - Located in `agent` directory and build using `./gradlew :agent:agentJar`. The resulting Jar should be passed as an `-agent` argument to the JVM.
2. **JMC Model Checker** - Located in `core` directory and build using `./gradlew :core:publish`. The resulting Jar should be passed as a dependency to the test.
3. **JMC Test Target** - Located in `integration-test` directory and build using `./gradlew :integration-test:test`

## Concurrent primitives supported

As evident in the example, we provide support for concurrent primitives by extending the Thread and ReentrantLock
classes.

Currently supported primitives are:

- `JmcThread` (extends Thread)
- `JmcRenetrantLock` (extends ReentrantLock)
- `JmcExecutorService` (extends ExecutorService)
- `JmcFuture` (extends Future)

By replacing the Thread primitives with the JmcThread primitives, we can control the interleaving of the threads across
different use cases such as ThreadPools and Executors.

## Pending work

- [ ] Implementing support for Park and Unpark
- [ ] Migrating automatic instrumentation of Java bytecode to replace the manual instrumentation