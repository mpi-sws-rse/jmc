package org.mpisws.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.concurrent.programs.atomic.counter.AtomicCounter;

public class JmcModelCheckerTest {

    @Test
    void testRandomAtomicCounter() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomAtomicCounter",
                        () -> {
                            AtomicCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }
}
