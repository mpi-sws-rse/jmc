package org.mpisws.jmc.checker;

import org.jetbrains.kotlin.ir.expressions.IrConstKind.Int;
import org.junit.jupiter.api.Test;
import org.mpisws.jmc.programs.concurrent.CC7;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

public class JmcCoverageBenchmark {
    @Test
    public void benchmarkCorrectCounterTrust() {
        HashMap<Integer, Duration> timeoutMap = new HashMap<>();
        timeoutMap.put(6, Duration.of(3, ChronoUnit.MINUTES));
        timeoutMap.put(7, Duration.of(10, ChronoUnit.MINUTES));
        timeoutMap.put(8, Duration.of(15, ChronoUnit.MINUTES));
        for (int i = 6; i < 9; i++) {
            int localI = i;
            System.out.println("Running with i = " + i);
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
                            .timeout(timeoutMap.get(localI))
                            .debug(false)
                            .build();
            JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

            JmcTestTarget target =
                    new JmcFunctionalTestTarget(
                            "TrustCC1",
                            () -> {
                                CC7.main(new String[] {String.valueOf(localI)});
                            });
            try {
                jmcModelChecker.check(target);
            } catch (JmcCheckerException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    @Test
    public void benchmarkCorrectCounterRandom() {
        HashMap<Integer, Duration> timeoutMap = new HashMap<>();
        timeoutMap.put(6, Duration.of(3, ChronoUnit.MINUTES));
        timeoutMap.put(7, Duration.of(10, ChronoUnit.MINUTES));
        timeoutMap.put(8, Duration.of(15, ChronoUnit.MINUTES));
        for (int i = 6; i < 9; i++) {
            int localI = i;
            System.out.println("Running with i = " + i);
            // Random math to determine the number of iterations
            Duration timeout = timeoutMap.get(localI);
            JmcCheckerConfiguration config =
                    new JmcCheckerConfiguration.Builder()
                            .strategyConstructor(
                                    (sConfig) ->
                                            new MeasureGraphCoverageStrategy(
                                                    new RandomSchedulingStrategy(sConfig.getSeed()),
                                                    false,
                                                    sConfig.getReportPath() + "/random-" + localI + "-" + timeout.toString(),
                                                    Duration.of(5, ChronoUnit.MILLIS)))
                            .timeout(timeout)
                            .debug(false)
                            .build();
            JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

            JmcTestTarget target =
                    new JmcFunctionalTestTarget(
                            "TrustCC1",
                            () -> {
                                CC7.main(new String[] {String.valueOf(localI)});
                            });
            try {
                jmcModelChecker.check(target);
            } catch (JmcCheckerException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
