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
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust")
    void testRandomBuggyCounter() throws JmcCheckerException {
        BuggyCounterUsingAPI.main(new String[0]);
    }
}
```

To run the test with the model checker on the command line use the following command.

```bash
./gradlew clean
./gradlew test --tests org.example.ExampleCounterTest.testRandomBuggyCounter
```

## Visualizing the execution graphs

When running a test with the Trust strategy, the model checker will record the execution graphs
if the debug flag (in the configuration) is set to true. The graphs are stored in the
`build/test-results/jmc-report/` directory of the project.

For example, if the tests in `core/src/test` are run then the execution graphs are stored in
`core/build/test-results/jmc-report/`.

Once the test is run, the execution graphs can be visualized using the following command from the root directory of the
project.

```bash
./scripts/visualize.sh <relative-path-to-graph-files>
```

For example, if the test is run in `core/src/test` then the command would be

```bash
./scripts/visualize.sh core/build/test-results/jmc-report/
```

The script will spin up a local web server. Open the URL `http://localhost:8000` in a web browser to view the execution
graphs.
