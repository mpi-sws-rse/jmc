package org.mpi_sws.jmc.test;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.test.features.StaticInitBlock;

public class StaticInitTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1)
    @Disabled
    public void testStaticInitBlock() {
        // The function will be called multiple times
        // If the initialization is correct, and the static block is called at the beginning of each
        // iteration, the assertion will always pass.
        // If the static block is not called, the value of x will not be reset to 0,
        // and the assertion will fail after the first iteration.
        StaticInitBlock.setX(StaticInitBlock.getX() + 1);
        assert StaticInitBlock.getX() == 1 : "Static initialization block did not reset x to 0";
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    @Disabled
    public void testStaticInitBlockMultipleIterations() {
        StaticInitBlock.setX(StaticInitBlock.getX() + 1);

        assert StaticInitBlock.getX() == 1 : "Static initialization block only executed once";
    }
}
