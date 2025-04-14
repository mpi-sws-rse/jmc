package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.integrations.junit5.engine.JmcTest;
import org.mpisws.jmc.programs.mockKafka.ShareConsumerTest;

@JmcTest
public class JmcExampleTest {

    @JmcCheckConfiguration(
            numIterations = 10,
            strategy = "random",
            debug = true
    )
    @Test
    public void testAcquisitionLockTimeoutOnConsumer() throws InterruptedException {
        ShareConsumerTest.main(new String[0]);
    }

}
