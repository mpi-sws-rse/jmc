package org.mpisws.jmc.checker;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.checker.exceptions.JmcCheckerTimeoutException;
import org.mpisws.jmc.programs.concurrent.CC7;
import org.mpisws.jmc.programs.det.lists.Client9;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategyConfig;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public class JmcCoverageBenchmark {

    private static Stream<Arguments> provideTrustTestCases() {
        return Stream.of(Arguments.of(7, Duration.of(5, ChronoUnit.MINUTES)));
//        ,
//                Arguments.of(8, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(9, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(12, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(15, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(20, Duration.of(30, ChronoUnit.MINUTES)));
    }

    private static Stream<Arguments> provideRandomTestCases() {
        return Stream.of(Arguments.of(7, Duration.of(5, ChronoUnit.MINUTES)));
//        ,
//                Arguments.of(8, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(9, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(12, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(15, Duration.of(30, ChronoUnit.MINUTES)),
//                Arguments.of(20, Duration.of(30, ChronoUnit.MINUTES)));
    }

    private static Stream<Arguments> provideRandomTestCasesCoarse9() {
        return Stream.of(
                Arguments.of(2, Duration.of(50000000, ChronoUnit.NANOS)),
                Arguments.of(3, Duration.of(150000000, ChronoUnit.NANOS)),
                Arguments.of(4, Duration.of(550000000, ChronoUnit.NANOS)),
                Arguments.of(5, Duration.of(5, ChronoUnit.SECONDS)),
                Arguments.of(6, Duration.of(67, ChronoUnit.SECONDS)));
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
                                        //                                        new
                                        // MeasureGraphCoverageStrategy(
                                        new TrustStrategy(
                                                sConfig.getSeed(),
                                                sConfig.getTrustSchedulingPolicy(),
                                                sConfig.getDebug(),
                                                sConfig.getReportPath()))
                        //
                        // MeasureGraphCoverageStrategyConfig.builder()
                        //                                                        .recordPath(
                        //
                        // sConfig.getReportPath()
                        //                                                                        +
                        // "/trust-"
                        //                                                                        +
                        // threads)
                        //                                                        .withFrequency(
                        //
                        // Duration.of(1, ChronoUnit.SECONDS))
                        //                                                        .build()))
                        .timeout(timeout)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC1",
                        () -> {
                            CC7.main(new String[]{String.valueOf(threads)});
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
                                                MeasureGraphCoverageStrategyConfig.builder()
                                                        .recordPath(
                                                                sConfig.getReportPath()
                                                                        + "/random-"
                                                                        + threads
                                                                        + "-"
                                                                        + timeout.toString())
                                                        .withFrequency(
                                                                Duration.of(1, ChronoUnit.SECONDS))
                                                        .build()))
                        .timeout(timeout)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC1",
                        () -> {
                            CC7.main(new String[]{String.valueOf(threads)});
                        });
        try {
            jmcModelChecker.check(target);
        } catch (JmcCheckerException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("provideRandomTestCasesCoarse9")
    public void benchmarkCoarseListRandom(int threads, Duration timeout)
            throws JmcCheckerException {
        System.out.println("Running with threads = " + threads);
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyConstructor(
                                (sConfig) ->
                                        new MeasureGraphCoverageStrategy(
                                                new RandomSchedulingStrategy(sConfig.getSeed()),
                                                MeasureGraphCoverageStrategyConfig.builder()
                                                        .recordPath(
                                                                sConfig.getReportPath()
                                                                        + "/random-"
                                                                        + threads
                                                                        + "-"
                                                                        + timeout.toString())
                                                        .debug(true)
                                                        .withFrequency(
                                                                Duration.of(1, ChronoUnit.SECONDS))
                                                        .build()))
                        .timeout(timeout)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);
        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "CoarseList",
                        () -> {
                            Client9.main(
                                    new String[]{String.valueOf(threads)});
                        });
        try {
            jmcModelChecker.check(target);
        } catch (JmcCheckerException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
