package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SynchronisedAnnotationTest {

    //private int sharedValue = 0;

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testSynchronisedAnnotation() {
        SynchronisedExtension e = new SynchronisedExtension();
        assertEquals(1, e.doSomething());
    }
}
