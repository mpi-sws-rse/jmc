package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcTimeout;
import org.mpisws.jmc.programs.atomic.counter.AtomicCounter;
import org.mpisws.jmc.programs.correct.counter.CorrectCounter;
import org.mpisws.jmc.programs.mockKafka.ShareConsumerTest;



@JmcCheckConfiguration(numIterations = 10, strategy = "random", debug = true)
 public class JmcExampleTest {

    @JmcCheckConfiguration(numIterations = 20, strategy = "random", debug = true)
    @Test
    public void testAcquisitionLockTimeoutOnConsumer() {
        ShareConsumerTest.main(new String[0]);
    }


    @JmcCheckConfiguration(strategy = "random", debug = true)
    @JmcTimeout(value = 5)
    @Test
    public void testAcquisitionLockTimeoutOnConsumerTimeout() {
        ShareConsumerTest.main(new String[0]);
    }

    @Test
    public void testAcquisitionLockTimeoutOnConsumerAgain() {
        ShareConsumerTest.main(new String[0]);
    }

    @Test
    public void testRandomAtomicCounter() {
        AtomicCounter.main(new String[0]);
    }

    @JmcCheckConfiguration(numIterations = 1, strategy = "random", debug = false)
    @Test
    public void testRandomCorrectCounter() {
        CorrectCounter.main(new String[0]);
    }
}
