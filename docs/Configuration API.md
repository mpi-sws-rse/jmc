# Configuration for JMC

The first step to run a test with JMC requires annotating the test with `@JmcCheck` annotation. 
Subsequently, the configuration used to check the program can be updated using `@JmcCheckConfiguration` annotation which
accepts the following parameters.

- `strategy` which can be one of `"random"` or `"trust"` 
