package org.mpisws.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.concurrent.programs.atomic.counter.AtomicCounter;
import org.mpisws.concurrent.programs.complex.counter.ComplexCounter;
import org.mpisws.concurrent.programs.concurrent.ConcurrentCounter;
import org.mpisws.concurrent.programs.correct.counter.CorrectCounter;
import org.mpisws.concurrent.programs.det.array.DetArray;
import org.mpisws.concurrent.programs.det.loop.DetLoop;
import org.mpisws.concurrent.programs.det.loopVariant.DetLoopWithLock;
import org.mpisws.concurrent.programs.det.stack.Client1;
import org.mpisws.concurrent.programs.det.stack.Client2;
import org.mpisws.concurrent.programs.det.stack.Client3;
import org.mpisws.concurrent.programs.det.stack.Client4;
import org.mpisws.concurrent.programs.det.stack.Client5;
import org.mpisws.concurrent.programs.det.stack.Client6;
import org.mpisws.concurrent.programs.dining.DiningPhilosophers;
import org.mpisws.concurrent.programs.wrong.counter.BuggyCounter;

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
                new JmcCheckerConfiguration.Builder().numIterations(1000).build();

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
                new JmcCheckerConfiguration.Builder().numIterations(10).build();
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
    void testTrustConcurrentCounter() {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(10)
                        .debug(true)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TrustBuggyCounter",
                        () -> {
                            ConcurrentCounter.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }
}
