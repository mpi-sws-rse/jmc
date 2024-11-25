package org.mpisws.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.JmcRuntimeConfiguration;

/**
 * The JmcModelChecker class is responsible for managing the model checking process. It uses a
 * JmcCheckerConfiguration to configure the process and a JmcTestTarget to specify the program under
 * test.
 */
public class JmcModelChecker {

    private static final Logger LOGGER = LogManager.getLogger(JmcModelChecker.class);

    private final JmcCheckerConfiguration config;

    /**
     * Constructs a new JMC model checker with the given configuration.
     *
     * @param config the configuration to use
     */
    public JmcModelChecker(JmcCheckerConfiguration config) {
        this.config = config;

        JmcRuntimeConfiguration runtimeConfig = config.toRuntimeConfiguration();
        JmcRuntime.setup(runtimeConfig);
    }

    /**
     * Checks the given test target. No instrumentation involved
     *
     * @param target the test target to check
     */
    public void check(JmcTestTarget target) {
        try {
            for (int i = 0; i < config.getNumIterations(); i++) {
                try {
                    JmcRuntime.initIteration(i);
                    target.invoke();
                    JmcRuntime.resetIteration();
                } catch (HaltExecutionException e) {
                    LOGGER.error("Halting execution: {} due to exception: {}", i, e.getMessage());
                    break;
                }
            }
        } catch (HaltExecutionException e) {
            LOGGER.error("Halting checking due to exception: {}", e.getMessage());
        }
    }
}
