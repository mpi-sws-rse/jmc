package org.mpisws.jmc.test.features;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectAssertionFailure;

public class ExpectFailureTest {
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1)
    @JmcExpectAssertionFailure
    public void testExpectFailure() {
        // This test is expected to fail due to an assertion failure.
        assert false : "This assertion is expected to fail.";
    }
}
