package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.programs.atomic.counter.AtomicCounter;
import org.mpisws.jmc.programs.complex.counter.ComplexCounter;
import org.mpisws.jmc.programs.concurrent.*;
import org.mpisws.jmc.programs.correct.counter.CorrectCounter;
import org.mpisws.jmc.programs.correct.counter.CorrectCounterKt;
import org.mpisws.jmc.programs.det.array.DetArray;
import org.mpisws.jmc.programs.det.lists.Client10;
import org.mpisws.jmc.programs.det.lists.Client8;
import org.mpisws.jmc.programs.det.lists.Client9;
import org.mpisws.jmc.programs.det.loop.DetLoop;
import org.mpisws.jmc.programs.det.loopVariant.DetLoopWithLock;
import org.mpisws.jmc.programs.det.stack.Client1;
import org.mpisws.jmc.programs.det.stack.Client2;
import org.mpisws.jmc.programs.det.stack.Client3;
import org.mpisws.jmc.programs.det.stack.Client4;
import org.mpisws.jmc.programs.det.stack.Client5;
import org.mpisws.jmc.programs.dining.DiningPhilosophers;
import org.mpisws.jmc.programs.futures.CompletableFutureP;
import org.mpisws.jmc.programs.futures.SimpleFuture;
import org.mpisws.jmc.programs.random.counter.RandomCounterIncr;
import org.mpisws.jmc.programs.mockKafka.ShareConsumerTest;
import org.mpisws.jmc.programs.wrong.counter.BuggyCounter;
import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategyConfig;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class JmcModelCheckerTest {

    @Test
    void testRandomAtomicCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomAtomicCounter",
                        () -> {
                            AtomicCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomCorrectCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();

        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomCorrectCounter",
                        () -> {
                            CorrectCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCorrectCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .numIterations(150)
                        .strategyType("trust")
                        .debug(false)
                        .build();

        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCorrectCounter",
                        () -> {
                            CorrectCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomComplexCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomComplexCounter",
                        () -> {
                            ComplexCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetArray() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetArray",
                        () -> {
                            DetArray.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetLoop() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetLoop",
                        () -> {
                            DetLoop.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetLoopWithLock() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetLoopWithLock",
                        () -> {
                            DetLoopWithLock.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetStack1() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetStack1",
                        () -> {
                            Client1.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetStack2() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetStack1",
                        () -> {
                            Client2.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetStack3() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetStack1",
                        () -> {
                            Client3.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetStack14() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetStack1",
                        () -> {
                            Client4.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetStack5() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetStack1",
                        () -> {
                            Client5.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDetStack6() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetStack1",
                        () -> {
                            org.mpisws.jmc.programs.det.stack.Client6.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDiningPhilosophers() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(100).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDiningPhilosophers",
                        () -> {
                            DiningPhilosophers.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void benchmarkRandomDiningPhilosophers() throws JmcCheckerException {
        List<Integer> iterations = List.of(100, 1000, 10000, 30000, 60000, 100000);
        List<JmcModelCheckerReport> reports = new ArrayList<>();
        for (int i : iterations) {
            JmcCheckerConfiguration config =
                    new JmcCheckerConfiguration.Builder().numIterations(i).build();
            JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

            JmcTestTarget target =
                    new JmcFunctionalTestTarget(
                            "RandomDiningPhilosophers",
                            () -> {
                                DiningPhilosophers.main(new String[0]);
                            });

            reports.add(jmcModelChecker.check(target));
        }
        System.out.println(
                reports.stream().map(JmcModelCheckerReport::getTotalTimeMillis).toList());
    }

    @Test
    void testRandomBuggyCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomBuggyCounter",
                        () -> {
                            BuggyCounter.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomCorrectCounterKt() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(100).build();

        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomCorrectCounterKt",
                        () -> {
                            CorrectCounterKt.main();
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomFutureSimple() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(100).build();

        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomFutureSimple",
                        () -> {
                            SimpleFuture.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testCompletableFuture() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(100).build();

        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "CompletableFuture",
                        () -> {
                            CompletableFutureP.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomReactiveCounter",
                        () -> {
                            RandomCounterIncr.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomLazyList() throws JmcCheckerException {
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder().numIterations(100).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomLazyList",
                        () -> {
                            Client8.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testRandomCoarseList() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(1000).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomCoarseList",
                        () -> {
                            Client9.main(new String[]{String.valueOf(3)});
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomFineList() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(1000).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);
        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomFineList",
                        () -> {
                            Client10.main(
                                    new String[]{String.valueOf(4)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testRandomOptList() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(1000).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);
        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomOptList",
                        () -> {
                            org.mpisws.jmc.programs.det.lists.Client6.main(
                                    new String[]{String.valueOf(4)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCoarse() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .timeout(Duration.of(2, ChronoUnit.HOURS))
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCoarse",
                        () -> {
                            int size = 7;
                            Client9.main(new String[]{String.valueOf(size)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustFine() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
//                        .timeout(Duration.of(2, ChronoUnit.HOURS))
                        .numIterations(200)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustFine",
                        () -> {
                            int size = 5;
                            Client10.main(new String[]{String.valueOf(size)});
                        });
        jmcModelChecker.check(target);
    }


    @Test
    void testTrustCC0() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(1000000)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC0",
                        () -> {
                            int size = 1;
                            CC0.main(new String[]{String.valueOf(size)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCC1() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .timeout(Duration.of(2, ChronoUnit.HOURS))
                        .debug(false)
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
    void testTrustCC2() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(100)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC2",
                        () -> {
                            CC2.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCC3() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(100)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC3",
                        () -> {
                            CC3.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCC4() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(100)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC4",
                        () -> {
                            CC4.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCC5() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(100)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC5",
                        () -> {
                            CC5.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCC7() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .timeout(Duration.of(2, ChronoUnit.HOURS))
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC7",
                        () -> {
                            int size = 3;
                            CC7.main(new String[]{String.valueOf(size)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTimeout() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("random")
                        .numIterations(1000)
                        .debug(true)
                        .timeout(Duration.ofSeconds(1))
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "Timeout",
                        () -> {
                            CC7.main(new String[]{"6"});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testAcquisitionLockTimeoutOnConsumer() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomKafkaTest",
                        () -> {
                            ShareConsumerTest.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }
}
