package org.mpi_sws.jmc.checker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The report's total time is the wall-clock length of the check, in milliseconds. */
class TotalTimeMillisTest {

    @Test
    void totalTimeMillis_isTheCheckDuration() throws Exception {
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("random").numIterations(3).seed(1L).reportPath("build/test/total-time").build();
        long before = System.currentTimeMillis();
        JmcModelCheckerReport report =
                new JmcModelChecker(config).check(new JmcFunctionalTestTarget("empty", () -> { }));
        long wall = System.currentTimeMillis() - before;
        long total = report.getTotalTimeMillis();
        assertTrue(total >= 0 && total <= wall, "totalTimeMillis " + total + " outside [0, " + wall + "]");
    }
}
