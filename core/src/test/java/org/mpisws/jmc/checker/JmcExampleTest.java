package org.mpisws.jmc.checker;

import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.programs.atomic.counter.AtomicCounter;
import org.mpisws.jmc.programs.correct.counter.CorrectCounter;
import org.mpisws.jmc.programs.mockKafka.ShareConsumerTest;

@JmcCheckConfiguration(numIterations = 10, strategy = "random")
public class JmcExampleTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 20, strategy = "random")
    public void testAcquisitionLockTimeoutOnConsumer() {
        ShareConsumerTest.main(new String[0]);
    }

    // TODO: these tests have timeouts. Therefore needs more work
    //    @JmcCheck
    //    @JmcCheckConfiguration()
    //    @JmcTimeout(value = 1)
    //    public void testAcquisitionLockTimeoutOnConsumerTimeout() {
    //        ShareConsumerTest.main(new String[0]);
    //    }

    //    @JmcCheck
    //    public void testAcquisitionLockTimeoutOnConsumerAgain() {
    //        ShareConsumerTest.main(new String[0]);
    //    }

    @JmcCheck
    public void testRandomAtomicCounter() {
        AtomicCounter.main(new String[0]);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1, strategy = "random", debug = false)
    public void testRandomCorrectCounter() {
        CorrectCounter.main(new String[0]);
    }
}
