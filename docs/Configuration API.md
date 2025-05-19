# Configuration for JMC

The first step to run a test with JMC requires annotating the test with `@JmcCheck` annotation. 
Subsequently, the configuration used to check the program can be updated using `@JmcCheckConfiguration` annotation which
accepts the following parameters.

- `strategy` which can be one of `"random"` or `"trust"` 
- `numIterations` - an integer to specify the upper limit of the number of iterations that the model checker should explore
- `debug` - An optional flag to store all the execution graphs and the log of the exploration
- `reportPath` - A string to specify the path where the report should be generated. Defaults to `build/test-results/jmc-report`
- `seed` - An optional seed to be used by the strategy. Determines the exploration to reproduce the executions.

An additional annotation `@JmcTimeout` can be used to specify a timeout for the exploration.
This annotation accepts two parameters, a value of long type and a time unit.

Note that either `@JmcTimeout` or `numIterations` are mandatory for model checking. In the absence of both, the test will fail.