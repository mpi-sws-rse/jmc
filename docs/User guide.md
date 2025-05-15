# JMC User guide

## Installation

### Build from source

This step is needed only if you do not wish to use the central maven repository to add JMC as a dependency.

1. Clone the github repository

```bash
git clone git@github.com:mpi-sws-rse/jmc.git
```

2. Publish the packages locally

```bash
./gradlew clean
./gradlew :core:publish
```

## Adding dependency

This assumes that there is an existing java project to run the model checker on. If not, refer
to [Project setup instructions](Gradle%20Example%20Project%20Setup.md)

In the `app/build.gradle.kts` or `app/build.gradle` file, add the following inside `dependencies`

```kotlin
testImplementation("org.mpisws:jmc:0.1.0")
```

Note that if you built from source then you would need to add `mavenLocal()` inside `repositories`

## Writing a program to test

Here is a sample test program and a test to run the model checker on.

Copy the `BuggyCounterUsingAPI.java` and `ExampleCounterTest.java` from (here)[] and add them inside
`app/src/test/java/org/example/BuggyCounterUsingAPI.java` and `app/src/test/java/org/example/ExampleCounterTest.java`

The test looks as follows.

```java
public class ExampleCounterTest {
    @Test
    void testRandomBuggyCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomBuggyCounter",
                        () -> {
                            BuggyCounterUsingAPI.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }
}
```

First initializing the configuration for the model checker and then specifying the target to model check and eventually
running the `checker.check` method.

To run the test with the model checker on the command line use the following command.

```bash
./gradlew clean
./gradlew test --tests org.example.ExampleCounterTest.testRandomBuggyCounter
```
