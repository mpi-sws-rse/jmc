package org.mpisws.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.*;

/**
 * The JmcModelChecker class is responsible for managing the model checking process. It uses a
 * JmcCheckerConfiguration to configure the process and a JmcTestTarget to specify the program under
 * test.
 */
public class JmcModelChecker {

    private static final Logger LOGGER = LogManager.getLogger(JmcModelChecker.class);

    private final JmcCheckerConfiguration config;

    /** Constructs a new JMC model checker with the default configuration. */
    public JmcModelChecker() {
        this(new JmcCheckerConfiguration.Builder().build());
    }

    /**
     * Constructs a new JMC model checker with the given configuration.
     *
     * @param config the configuration to use
     */
    public JmcModelChecker(JmcCheckerConfiguration config) {
        this.config = config;
    }

    /**
     * Checks the given test target. No instrumentation involved
     *
     * @param target the test target to check
     */
    public void check(JmcTestTarget target) {
        JmcRuntimeConfiguration runtimeConfig = config.toRuntimeConfiguration();
        JmcRuntime.setup(runtimeConfig);
        int numIterations = config.getNumIterations();
        try {
            for (int i = 0; i < numIterations; i++) {
                try {
                    JmcRuntime.initIteration(i);
                    target.invoke();
                    RuntimeEvent mainEndEvent =
                            new RuntimeEvent.Builder()
                                    .type(RuntimeEventType.FINISH_EVENT)
                                    .taskId(1L)
                                    .build();
                    JmcRuntime.updateEvent(mainEndEvent);
                    JmcRuntime.resetIteration();
                } catch (HaltExecutionException e) {
                    LOGGER.error("Halting execution: {} due to exception: {}", i, e.getMessage());
                    break;
                } catch (AssertionError e) {
                    LOGGER.error("Assertion error in iteration {}: {}", i, e.getMessage());
                    // TODO: capture the report
                    break;
                }
            }
        } catch (HaltExecutionException e) {
            LOGGER.error("Halting checking due to exception: {}", e.getMessage());
        } finally {
            JmcRuntime.tearDown();
        }
    }
}
