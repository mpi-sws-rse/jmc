package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.programs.concurrent.CC7;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class JmcCoverageBenchmark {
    @Test
    public void benchmarkCorrectCounterTrust() {
        for (int i = 3; i < 8; i++) {
            int localI = i;
            System.out.println("Running with i = " + i);
            int iterations = (int) Math.pow(2, 2 * (i + 1)) + 10;
            JmcCheckerConfiguration config =
                    new JmcCheckerConfiguration.Builder()
                            .strategyConstructor(
                                    (sConfig) ->
                                            new MeasureGraphCoverageStrategy(
                                                    new TrustStrategy(
                                                            sConfig.getSeed(),
                                                            sConfig.getTrustSchedulingPolicy(),
                                                            sConfig.getDebug(),
                                                            sConfig.getReportPath()),
                                                    false,
                                                    sConfig.getReportPath() + "/trust-" + localI,
                                                    Duration.of(5, ChronoUnit.MILLIS)))
                            .numIterations(iterations)
                            .debug(false)
                            .build();
            JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

            JmcTestTarget target =
                    new JmcFunctionalTestTarget(
                            "TrustCC1",
                            () -> {
                                CC7.main(new String[] {String.valueOf(localI)});
                            });
            jmcModelChecker.check(target);
        }
    }

    @Test
    public void benchmarkCorrectCounterRandom() {
        for (int i = 3; i < 8; i++) {
            int localI = i;
            System.out.println("Running with i = " + i);
            // Random math to determine the number of iterations
            int iterations = (int) Math.pow(2, 2 * (i + 1)) + 10;
            JmcCheckerConfiguration config =
                    new JmcCheckerConfiguration.Builder()
                            .strategyConstructor(
                                    (sConfig) ->
                                            new MeasureGraphCoverageStrategy(
                                                    new RandomSchedulingStrategy(sConfig.getSeed()),
                                                    false,
                                                    sConfig.getReportPath() + "/random-" + localI,
                                                    Duration.of(5, ChronoUnit.MILLIS)))
                            .numIterations(iterations)
                            .debug(false)
                            .build();
            JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

            JmcTestTarget target =
                    new JmcFunctionalTestTarget(
                            "TrustCC1",
                            () -> {
                                CC7.main(new String[] {String.valueOf(localI)});
                            });
            jmcModelChecker.check(target);
        }
    }
}
