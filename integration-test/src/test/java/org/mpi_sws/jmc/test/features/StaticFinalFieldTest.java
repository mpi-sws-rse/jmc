package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StaticFinalFieldTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testStaticFinalInterfaceField() {
        ConcreteFields f = new ConcreteFields();
        assertEquals(3, f.getInitCounter());
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct")
    public void testStaticFinalInterfaceFieldPct() {
        ConcreteFields f = new ConcreteFields();
        assertEquals(3, f.getInitCounter());
    }
}
