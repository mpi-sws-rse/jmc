package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.programs.atomic.counter.AtomicCounter;
import org.mpisws.jmc.programs.complex.counter.ComplexCounter;
import org.mpisws.jmc.programs.concurrent.*;
import org.mpisws.jmc.programs.correct.counter.CorrectCounter;
import org.mpisws.jmc.programs.correct.counter.CorrectCounterKt;
import org.mpisws.jmc.programs.det.array.DetArray;
import org.mpisws.jmc.programs.det.loop.DetLoop;
import org.mpisws.jmc.programs.det.loopVariant.DetLoopWithLock;
import org.mpisws.jmc.programs.det.stack.Client1;
import org.mpisws.jmc.programs.det.stack.Client2;
import org.mpisws.jmc.programs.det.stack.Client3;
import org.mpisws.jmc.programs.det.stack.Client4;
import org.mpisws.jmc.programs.det.stack.Client5;
import org.mpisws.jmc.programs.det.stack.Client6;
import org.mpisws.jmc.programs.dining.DiningPhilosophers;
import org.mpisws.jmc.programs.futures.CompletableFutureP;
import org.mpisws.jmc.programs.futures.SimpleFuture;
import org.mpisws.jmc.programs.random.counter.RandomCounterIncr;
import org.mpisws.jmc.programs.wrong.counter.BuggyCounter;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class JmcModelCheckerTest {

    @Test
    void testRandomAtomicCounter() {
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
    void testRandomCorrectCounter() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(100000).build();

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
    void testRandomComplexCounter() {
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
    void testRandomDetArray() {
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
    void testRandomDetLoop() {
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
    void testRandomDetLoopWithLock() {
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
    void testRandomDetStack1() {
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
    void testRandomDetStack2() {
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
    void testRandomDetStack3() {
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
    void testRandomDetStack14() {
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
    void testRandomDetStack5() {
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
    void testRandomDetStack6() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomDetStack1",
                        () -> {
                            Client6.main(new String[0]);
                        });

        jmcModelChecker.check(target);
    }

    @Test
    void testRandomDiningPhilosophers() {
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
    void benchmarkRandomDiningPhilosophers() {
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
    void testRandomBuggyCounter() {
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
    void testRandomCorrectCounterKt() {
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
    void testRandomFutureSimple() {
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
    void testCompletableFuture() {
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
    void testRandomCounter() {
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
    void testTrustCC0() {
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
                            CC0.main(new String[] {String.valueOf(size)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTrustCC1() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
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
    void testTrustCC2() {
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
    void testTrustCC3() {
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
    void testTrustCC4() {
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
    void testTrustCC5() {
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
    void testTrustCC7() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(1000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustCC7",
                        () -> {
                            int size = 4;
                            CC7.main(new String[] {String.valueOf(size)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    void testTimeout() {
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
                            CC7.main(new String[] {"6"});
                        });
        jmcModelChecker.check(target);
    }
}
