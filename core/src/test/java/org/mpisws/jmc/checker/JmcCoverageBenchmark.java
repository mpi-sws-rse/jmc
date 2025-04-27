package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.programs.concurrent.CC7;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

public class JmcCoverageBenchmark {
    @Test
    public void benchmarkCorrectCounterTrust() {
        for (int i = 4; i < 5; i++) {
            int finalI = i;
            System.out.println("Running with i = " + i);
            //            int iterations = (int) Math.pow(2, 2 * i + 1) + 10;
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
                                                    sConfig.getReportPath() + "/trust-" + finalI))
                            .numIterations(1000)
                            .debug(false)
                            .build();
            JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

            JmcTestTarget target =
                    new JmcFunctionalTestTarget(
                            "TrustCC1",
                            () -> {
                                CC7.main(new String[] {String.valueOf(finalI)});
                            });
            jmcModelChecker.check(target);
        }
    }

    @Test
    public void benchmarkCorrectCounterRandom() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyConstructor(
                                (sConfig) ->
                                        new MeasureGraphCoverageStrategy(
                                                new RandomSchedulingStrategy(sConfig.getSeed()),
                                                false,
                                                sConfig.getReportPath() + "/random"))
                        .numIterations(1000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC1",
                        () -> {
                            CC7.main(new String[] {"4"});
                        });
        jmcModelChecker.check(target);
    }
}
