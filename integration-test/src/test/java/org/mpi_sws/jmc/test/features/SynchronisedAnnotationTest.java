package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

public class SynchronisedAnnotationTest {

    //private int sharedValue = 0;

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testSynchronisedAnnotation() {
        SynchronisedExtension e = new SynchronisedExtension();
        assert (e.doSomething() == 1);
    }
}
