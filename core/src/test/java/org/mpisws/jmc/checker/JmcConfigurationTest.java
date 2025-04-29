package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.checker.exceptions.JmcInvalidConfigurationException;

import java.time.Duration;

public class JmcConfigurationTest {

    @Test
    public void testValidConfiguration1() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
    }

    @Test
    public void testValidConfiguration2() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().timeout(Duration.ofSeconds(1)).build();
    }

    @Test
    public void testInvalidConfiguration() {
        try {
            JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder().build();
        } catch (JmcInvalidConfigurationException expected) {
            // Expected exception
        }
    }
}
