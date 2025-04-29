package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.programs.correct.counter.UnInstrumentedCounter;

public class JmcInstrumentationTest {
    @Test
    @JmcCheck
    public void testRandomUnInstrumentedCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();

        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomCorrectCounter",
                        () -> {
                            UnInstrumentedCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }
}
