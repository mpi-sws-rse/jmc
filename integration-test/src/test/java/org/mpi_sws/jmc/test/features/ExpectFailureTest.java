package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectAssertionFailure;

public class ExpectFailureTest {
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1)
    @JmcExpectAssertionFailure
    public void testExpectFailure() {
        // This test is expected to fail due to an assertion failure.
        assert false : "This assertion is expected to fail.";
    }
}
