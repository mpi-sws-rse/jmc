package org.mpisws.checker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.*;


class ModelCheckerTest {
    /*
        private static final PrintStream originalOut = System.out;
        private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    */
    private ModelChecker checker;


/*
    @BeforeAll
    public static void setUpStreams() {
        System.setOut(new PrintStream(outContent, true));
    }

    @AfterAll
    public static void cleanUpStreams() {
        System.setOut(null);
    }
*/

    @BeforeEach
    void setUp() {
        System.out.println("setUp");
        CheckerConfiguration config =
                new CheckerConfiguration.ConfigurationBuilder().withVerbose(true).build();
        checker = new ModelChecker(config);
    }

/*
    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.out.println("finished");
        checker = null;
        outContent.reset();
    }
*/

    /*
     *                                      BUGGY COUNTERS
     */

    @Test
    @DisplayName("Buggy counter that deadlocks - Random")
    void randomTestBuggyCounterThatDeadlocks() {
        var t = new TestTarget("org.mpisws.concurrent.programs.wrong.counter",
                "BuggyCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/wrong/counter/"
        );
        System.out.println("BuggyCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "BuggyCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("Buggy counter that deadlocks - Trust")
    void trustTestBuggyCounterThatDeadlocks() {
        var t = new TestTarget("org.mpisws.concurrent.programs.wrong.counter",
                "BuggyCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/wrong/counter/"
        );
        System.out.println("BuggyCounter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.TRUST;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "BuggyCounter Trust Strategy Finished");
    }

    @Test
    @DisplayName("Buggy counter that deadlocks - Replay")
    void replayTestBuggyCounterThatDeadlocks() {
        var t = new TestTarget("org.mpisws.concurrent.programs.wrong.counter",
                "BuggyCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/wrong/counter/"
        );
        System.out.println("BuggyCounter ReplayStrategy Started");
        checker.configuration.strategyType = StrategyType.REPLAY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "BuggyCounter ReplayStrategy Finished");
    }

    /*
     *                                      INCONSISTENT COUNTERS
     */

    @Test
    @DisplayName("Inconsistent counter with a race condition - Random")
    void randomTestInconsistentCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.inconsistent.counter",
                "InconsistentCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/inconsistent/counter/"
        );
        System.out.println("InconsistentCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "InconsistentCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("Inconsistent counter with a race condition - Trust")
    void trustTestInconsistentCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.inconsistent.counter",
                "InconsistentCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/inconsistent/counter/"
        );
        System.out.println("InconsistentCounter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.TRUST;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.executionGraphsPath = "src/main/resources/Visualized_Graphs/";
        assertTrue(checker.check(t), "InconsistentCounter Trust Strategy Finished");
    }

    @Test
    @DisplayName("Inconsistent counter with a race condition - Replay")
    void replayTestInconsistentCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.inconsistent.counter",
                "InconsistentCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/inconsistent/counter/"
        );
        System.out.println("InconsistentCounter Replay Strategy Started");
        checker.configuration.strategyType = StrategyType.REPLAY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "InconsistentCounter Replay Strategy Finished");
    }

    /*
     *                                      Complex CORRECT COUNTERS
     */

    @Test
    @DisplayName("A concurrent counter with nested class structure - Random")
    void randomTestComplexCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.complex.counter",
                "ComplexCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/complex/counter/"
        );
        checker.configuration.strategyType = StrategyType.RANDOM;
        System.out.println("ComplexCounter Random Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "ComplexCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("A concurrent counter with nested class structure - Trust")
    void trustTestComplexCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.complex.counter",
                "ComplexCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/complex/counter/"
        );
        checker.configuration.strategyType = StrategyType.TRUST;
        System.out.println("ComplexCounter Trust Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "ComplexCounter Trust Strategy Finished");
    }

    /*
     *                                      CORRECT COUNTERS
     */

    @Test
    @DisplayName("A correct concurrent counter using synchronized blocks - Random")
    void randomTestCorrectCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.correct.counter",
                "CorrectCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/correct/counter/"
        );
        checker.configuration.strategyType = StrategyType.RANDOM;
        System.out.println("CorrectCounter Random Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "CorrectCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("A correct concurrent counter using synchronized blocks - Trust")
    void trustTestCorrectCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.correct.counter",
                "CorrectCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/correct/counter/"
        );
        checker.configuration.strategyType = StrategyType.TRUST;
        System.out.println("CorrectCounter Trust Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "CorrectCounter Trust Strategy Finished");
    }

    /*
     *                             SIMPLE INCONSISTENT COUNTERS WITH THREADS SPAWNING
     * To see the beauty of our implemented model checker, run the following test with TrustStrategy.
     */

    @Test
    @DisplayName("A concurrent counter using nested thread spawning - Random")
    void randomTestSimpleCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.simple.counter",
                "SimpleCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/simple/counter/"
        );
        System.out.println("SimpleCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SimpleCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("A concurrent counter using nested thread spawning - Trust")
    void trustTestSimpleCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.simple.counter",
                "SimpleCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/simple/counter/"
        );
        System.out.println("SimpleCounter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.TRUST;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.executionGraphsPath = "src/main/resources/Visualized_Graphs/";
        assertTrue(checker.check(t), "SimpleCounter Trust Strategy Finished");
    }

    @Test
    @DisplayName("A concurrent counter using nested thread spawning - Replay")
    void replayTestSimpleCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.simple.counter",
                "SimpleCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/simple/counter/"
        );
        System.out.println("SimpleCounter Replay Strategy Started");
        checker.configuration.strategyType = StrategyType.REPLAY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SimpleCounter Replay Strategy Finished");
    }

    /*
     *                                  DINING PHILOSOPHERS WITH DEADLOCK
     */

    @Test
    @DisplayName("Dining philosophers problem with deadlock - Random")
    void randomTestDiningPhilosophers() {
        var t = new TestTarget("org.mpisws.concurrent.programs.dining",
                "DiningPhilosophers",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/dining/"
        );
        System.out.println("DiningPhilosophers Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "DiningPhilosophers Random Strategy Finished");
    }

    @Test
    @DisplayName("Dining philosophers problem with deadlock - Trust")
    void trustTestDiningPhilosophers() {
        var t = new TestTarget("org.mpisws.concurrent.programs.dining",
                "DiningPhilosophers",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/dining/"
        );
        System.out.println("DiningPhilosophers Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.TRUST;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.executionGraphsPath = "src/main/resources/Visualized_Graphs/";
        assertTrue(checker.check(t), "DiningPhilosophers Trust Strategy Finished");
    }

    @Test
    @DisplayName("Dining philosophers problem with deadlock - Replay")
    void replayTestDiningPhilosophers() {
        var t = new TestTarget("org.mpisws.concurrent.programs.dining",
                "DiningPhilosophers",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/dining/"
        );
        System.out.println("DiningPhilosophers Replay Strategy Started");
        checker.configuration.strategyType = StrategyType.REPLAY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "DiningPhilosophers Replay Strategy Finished");
    }

    /*
     *                                    DISABLED TESTS - DO NOT RUN
     */

    @Disabled("This test is disabled due to using lambda functions")
    @Test
    @DisplayName("Multiple threads each spawning new threads with a shared counter")
    void testMultipleSpawns() {
        var t = new TestTarget("org.mpisws.concurrent.programs.thread_dependency",
                "MultipleThreads",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/thread_dependency/"
        );
        assertTrue(checker.check(t), "Call works");
    }

    @Test
    @Disabled("This test is disabled due to using while(true) loop for thread waiting")
    @DisplayName("LockOne Mutex")
    void testMutex() {
        var t = new TestTarget("org.mpisws.concurrent.programs.mutex",
                "MainMutex",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/mutex/"
        );
        assertTrue(checker.check(t), "Call works");
    }
}