package org.mpisws.checker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mpisws.concurrent.programs.atomic.counter.AtomicCounter;

public class JmcModelCheckerTest {

    private JmcModelChecker jmcModelChecker;

    @BeforeEach
    public void setUp() {
        jmcModelChecker = new JmcModelChecker();
    }

    @Test
    void testRandomAtomicCounter() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomAtomicCounter",
                        () -> {
                            AtomicCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }
}
