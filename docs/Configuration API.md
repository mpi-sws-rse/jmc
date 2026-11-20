# Configuration for JUnit tests

Runnable example, from JMC's own tests (`core/src/test/java/org/mpi_sws/jmc/checker/JmcExampleTest.java`):

```java
@JmcCheckConfiguration(numIterations = 10, strategy = "random")   // the class default
public class JmcExampleTest {
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1, strategy = "random", debug = false)   // wins for this method
    public void testRandomCorrectCounter() {
        CorrectCounter.main(new String[0]);
    }
}
```

```bash
./gradlew :core:test --tests org.mpi_sws.jmc.checker.JmcExampleTest.testRandomCorrectCounter
```

A test runs under JMC when it is annotated with `@JmcCheck` (on a method or a class). `@JmcCheckConfiguration` (method
or class; the method's wins) sets the exploration:

| Parameter | Default | Meaning |
|---|---|---|
| `strategy` | `"random"` | `random`, `pct`, `fair-pct`, `trust`, the estimation strategies `testor`, `pestor`, `fj-pestor`, `trust-estimation`, `wg-trust-estimation`, `abs-dag-estimation`, and `mp-random`, `must` for message-passing programs |
| `numIterations` | `100` | the most iterations explored |
| `timeout` | `-1` | in milliseconds; `-1` for none |
| `seed` | `0` | `0` takes a fresh seed from the clock |
| `solver` | `"off"` | for concolic exploration with `trust`: `z3`, `cvc5`, `cvc4`, `mathsat5`, `yices2`, `opensmt`, `smtinterpol`, `princess`, `boolector` |
| `schedulingPolicy` | `RANDOM` | `RANDOM`, `FIFO` or `LIFO` |
| `bugDepth` | `3` | for `pct` |
| `pctFairBound` | `0` | for `fair-pct` |
| `budget` | `2` | for the estimation strategies |
| `debug` | `false` | record the execution graphs and the exploration log |
| `reportPath` | `"build/test-results/jmc-report"` | where the report and replay files are written |

`@JmcTimeout(value, unit)` (unit defaults to seconds) bounds the exploration by time. A test with `@JmcCheck` but no
`@JmcCheckConfiguration` has no iteration bound and must carry `@JmcTimeout`, or it fails with "Either numIterations
or timeout must be set".

Other annotations: `@JmcExpectExecutions(n)` (the expected number of completed, non-blocked executions),
`@JmcExpectAssertionFailure`, `@JmcReplay` (replay the saved failing schedule), `@JmcIgnoreInstrumentation` (on a
class: do not instrument it), `@JmcTrustStrategy` (trust's own options) and `@JmcMeasureGraphCoverage` (applied only
together with `@JmcTrustStrategy`).
