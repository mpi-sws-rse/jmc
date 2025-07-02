package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.features.StaticInitBlock;

public class StaticInitTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1)
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
    public void testStaticInitBlockMultipleIterations() {

        StaticInitBlock.setX(StaticInitBlock.getX() + 1);

        assert StaticInitBlock.getStaticBlockExecutionCount()== 1 : "Static initialization block only executed once";
    }


}
