package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.programs.concurrent.CC1;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

public class JmcCoverageBenchmark {
    @Test
    public void benchmarkCorrectCounterTrust() {
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
                                                sConfig.getDebug(),
                                                sConfig.getReportPath() + "/trust"))
                        .numIterations(100)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC1",
                        () -> {
                            CC1.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void benchmarkCorrectCounterRandom() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyConstructor(
                                (sConfig) ->
                                        new MeasureGraphCoverageStrategy(
                                                new RandomSchedulingStrategy(sConfig.getSeed()),
                                                sConfig.getDebug(),
                                                sConfig.getReportPath() + "/random"))
                        .numIterations(100)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC1",
                        () -> {
                            CC1.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }
}
