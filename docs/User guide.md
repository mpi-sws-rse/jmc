# JMC user guide: JUnit tests under JMC

This page covers model checking ordinary Java code with JUnit 5 tests. For message-passing programs and conformance
checking, see [must-api.md](must-api.md) and [conformance.md](conformance.md).

## Installation

JMC needs Java 17+ and Gradle 8.4+. A test runs under JMC's agent, which instruments the code under test; the Gradle
plugin `org.mpi_sws.jmc.gradle` adds the library to `testImplementation` and attaches the agent to the test tasks
(see the [README](../README.md#getting-started)).

The library `org.mpi-sws.jmc:jmc` and the agent `org.mpi-sws.jmc:jmc-agent` (0.2.0) are on Maven Central; the plugin
is not yet on the Gradle Plugin Portal. Build it from source and publish it (with the library and the agent) to your
local Maven repository:

```bash
git clone https://github.com/mpi-sws-rse/jmc.git
cd jmc
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to the repositories of your build and to `pluginManagement.repositories` in
`settings.gradle.kts`. A project without a build yet: [Gradle Example Project Setup.md](Gradle%20Example%20Project%20Setup.md).

## Writing a test

Annotate a JUnit test with `@JmcCheck` and a configuration ([Configuration API.md](Configuration%20API.md)):

```java
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

class CounterTest {
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust")
    void testCounter() throws InterruptedException {
        Counter counter = new Counter();
        Thread t1 = new Thread(counter::inc);
        Thread t2 = new Thread(counter::inc);
        t1.start(); t2.start();
        t1.join(); t2.join();
        assert counter.get() == 2;
    }
}
```

Run it as usual, e.g. `./gradlew test --tests org.example.CounterTest`. JMC's own integration tests are examples:
`integration-test/src/test/java/org/mpi_sws/jmc/test/CounterTest.java`.

## Visualizing the execution graphs

With the `trust` strategy and `debug = true`, JMC records the execution graphs in the report directory
(`build/test-results/jmc-report/` by default). To view them, run from the root of the JMC repository:

```bash
./scripts/visualize_graphs.sh <relative-path-to-graph-files>
```

for example `./scripts/visualize_graphs.sh core/build/test-results/jmc-report/`. The script starts a local web
server; open `http://localhost:8000`.
