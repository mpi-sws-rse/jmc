# JMC User guide

## Installation

### Build from source

1. Clone the github repository

```bash
git clone git@github.com:mpi-sws-rse/jmc.git
```

2. Publish the packages locally

```bash
./gradlew clean
./gradlew :core:publish
```

### Add maven dependency

TODO

## Adding dependency

Create a new Java project and include jmc as a dependency for testing

```bash
mkdir new-project
gradle init
```

Ensure that:

1. You have gradle installed (min version 8.4)
2. Select application as the project type
3. Select Java as the implementation language (min java version 17)
4. Use JUnit Jupiter as the test framework

Inside the build.gradle.kts or build.gradle, add the following inside dependencies

```kotlin
testImplementation("org.mpisws:jmc:0.1.0")
```

Note that if you built from source then you would need to add `mavenLocal()` inside `reporsitories`

## Writing a program to test

Here is a sample test program and a test to run the model checker on.

Add the following inside `src/app/test/org/example/ExampleCounter.java`

```java
public class ExampleCounter {
    private int count = 0;

    public static void main(String[] args) {
        ExampleCounter counter = new ExampleCounter();

        JmcThread thread1 = new JmcThread(() -> count++);
        JmcThread thread2 = new JmcThread(() -> count++);

        thread1.start();
        thread2.start();

        try {
            thread1.join1();
            thread2.join1();
            assert counter.getCount() == 2;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

And the following inside `src/app/test/org/example/ExampleCounterTest.java`

```java
public class ExampleCounterTest {
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
}
```

Then to run the test with the model checker on the command line

```bash
./gradlew test --tests org.example.ExampleCounterTest.testCounter
```
