package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SynchronisedAnnotationTest {

    private int sharedValue = 0;

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public synchronized void testSomething() throws InterruptedException {
        sharedValue++;

        Thread.sleep(5000);

        assertEquals(1, sharedValue);

        sharedValue = 0;
    }
}
