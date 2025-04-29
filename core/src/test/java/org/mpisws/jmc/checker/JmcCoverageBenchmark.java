package org.mpisws.jmc.checker;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.checker.exceptions.JmcCheckerTimeoutException;
import org.mpisws.jmc.programs.concurrent.CC7;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public class JmcCoverageBenchmark {

    private static Stream<Arguments> provideTrustTestCases() {
        return Stream.of(
                Arguments.of(6, Duration.of(6, ChronoUnit.MINUTES)),
                Arguments.of(7, Duration.of(10, ChronoUnit.MINUTES)));
    }

    private static Stream<Arguments> provideRandomTestCases() {
        return Stream.of(
                Arguments.of(6, Duration.of(6, ChronoUnit.MINUTES)),
                Arguments.of(6, Duration.of(12, ChronoUnit.MINUTES)),
                Arguments.of(7, Duration.of(10, ChronoUnit.MINUTES)),
                Arguments.of(7, Duration.of(20, ChronoUnit.MINUTES)));
    }

    @ParameterizedTest
    @MethodSource("provideTrustTestCases")
    public void benchmarkCorrectCounterTrust(int threads, Duration timeout)
            throws JmcCheckerException {
        System.out.println("Running with threads = " + threads);
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
                                                sConfig.getReportPath() + "/trust-" + threads,
                                                false,
                                                Duration.of(100, ChronoUnit.MILLIS)))
                        .timeout(timeout)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC1",
                        () -> {
                            CC7.main(new String[] {String.valueOf(threads)});
                        });
        try {
            jmcModelChecker.check(target);
        } catch (JmcCheckerTimeoutException ignored) {

        } catch (JmcCheckerException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("provideRandomTestCases")
    public void benchmarkCorrectCounterRandom(int threads, Duration timeout)
            throws JmcCheckerException {
        System.out.println("Running with threads = " + threads);
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyConstructor(
                                (sConfig) ->
                                        new MeasureGraphCoverageStrategy(
                                                new RandomSchedulingStrategy(sConfig.getSeed()),
                                                false,
                                                sConfig.getReportPath()
                                                        + "/random-"
                                                        + threads
                                                        + "-"
                                                        + timeout.toString(),
                                                true,
                                                Duration.of(100, ChronoUnit.MILLIS)))
                        .timeout(timeout)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC1",
                        () -> {
                            CC7.main(new String[] {String.valueOf(threads)});
                        });
        try {
            jmcModelChecker.check(target);
        } catch (JmcCheckerException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
