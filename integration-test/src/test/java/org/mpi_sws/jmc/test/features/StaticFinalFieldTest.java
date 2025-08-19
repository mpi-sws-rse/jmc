package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

public class StaticFinalFieldTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testStaticFinalInterfaceField() {
        ConcreteFields f = new ConcreteFields();
        assert f.getInitCounter() == 3;
    }
}
