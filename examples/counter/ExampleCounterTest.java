package org.example;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.JmcFunctionalTestTarget;
import org.mpisws.jmc.checker.JmcModelChecker;
import org.mpisws.jmc.checker.JmcTestTarget;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

public class ExampleCounterTest {
    @Test
    void testRandomExampleCounter() throws JmcCheckerException{
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomExampleCounter",
                        () -> {
                            ExampleCounterUsingAPI.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }
}
