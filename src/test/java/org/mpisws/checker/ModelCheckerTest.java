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
        CheckerConfiguration config = new CheckerConfiguration.ConfigurationBuilder().withVerbose(true).build();
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
     * As the following test uses synchronized blocks, it can be run with RandomStrategy and ReplayStrategy only.
     */

    @Test
    @DisplayName("Buggy counter that deadlocks - RandomStrategy")
    void randomTestBuggyCounterThatDeadlocks() {
        var t = new TestTarget("org.example.concurrent.programs.wrong.counter",
                    "BuggyCounter",
                    "main",
                    "src/test/java/org/example/concurrent/programs/wrong/counter/"
        );
        System.out.println("BuggyCounter RandomStrategy Started");
        checker.configuration.strategyType = StrategyType.RANDOMSTRAREGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "BuggyCounter RandomStrategy Finished");
    }

    @Test
    @DisplayName("Buggy counter that deadlocks - ReplayStrategy")
    void replayTestBuggyCounterThatDeadlocks() {
        var t = new TestTarget("org.example.concurrent.programs.wrong.counter",
                "BuggyCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/wrong/counter/"
        );
        System.out.println("BuggyCounter ReplayStrategy Started");
        checker.configuration.strategyType = StrategyType.REPLAYSTRATEGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "BuggyCounter ReplayStrategy Finished");
    }

    /*
     *                                      INCONSISTENT COUNTERS
     */

    @Test
    @DisplayName("Inconsistent counter with a race condition - RandomStrategy")
    void randomTestInconsistentCounter() {
        System.out.println("InconsistentCounter");
        var t = new TestTarget("org.example.concurrent.programs.inconsistent.counter",
                        "InconsistentCounter",
                        "main",
                        "src/test/java/org/example/concurrent/programs/inconsistent/counter/"
        );
        System.out.println("InconsistentCounter - RandomStrategy");
        checker.configuration.strategyType = StrategyType.RANDOMSTRAREGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "call works");
    }

    @Test
    @DisplayName("Inconsistent counter with a race condition - TrustStrategy")
    void trustTestInconsistentCounter() {
        System.out.println("InconsistentCounter");
        var t = new TestTarget("org.example.concurrent.programs.inconsistent.counter",
                "InconsistentCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/inconsistent/counter/"
        );
        System.out.println("InconsistentCounter TrustStrategy Started");
        checker.configuration.strategyType = StrategyType.TRUSTSTRATEGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.executionGraphsPath = "src/main/resources/Visualized_Graphs/";
        assertTrue(checker.check(t), "InconsistentCounter TrustStrategy Finished");
    }

    @Test
    @DisplayName("Inconsistent counter with a race condition - Replay")
    void replayTestInconsistentCounter() {
        System.out.println("InconsistentCounter");
        var t = new TestTarget("org.example.concurrent.programs.inconsistent.counter",
                "InconsistentCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/inconsistent/counter/"
        );
        System.out.println("InconsistentCounter ReplayStrategy Started");
        checker.configuration.strategyType = StrategyType.REPLAYSTRATEGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "InconsistentCounter ReplayStrategy Finished");
    }

    /*
     *                                      Complex CORRECT COUNTERS
     * As the following test uses synchronized blocks, it can be run with RandomStrategy only.
     */

    @Test
    @DisplayName("A concurrent counter with nested class structure - RandomStrategy")
    void randomTestComplexCounter() {
        var t = new TestTarget("org.example.concurrent.programs.complex.counter",
                        "ComplexCounter",
                        "main",
                        "src/test/java/org/example/concurrent/programs/complex/counter/"
        );
        checker.configuration.strategyType = StrategyType.RANDOMSTRAREGY;
        System.out.println("ComplexCounter RandomStrategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "ComplexCounter RandomStrategy Finished");
    }

    /*
     *                                      CORRECT COUNTERS
     * As the following test uses synchronized blocks, it can be run with RandomStrategy only.
     */

    @Test
    @DisplayName("A correct concurrent counter using synchronized blocks - RandomStrategy")
    void randomTestCorrectCounter() {
        var t = new TestTarget("org.example.concurrent.programs.correct.counter",
                    "CorrectCounter",
                    "main",
                    "src/test/java/org/example/concurrent/programs/correct/counter/"
        );
        checker.configuration.strategyType = StrategyType.RANDOMSTRAREGY;
        System.out.println("CorrectCounter RandomStrategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "CorrectCounter RandomStrategy Finished");
    }

    /*
     *                             SIMPLE INCONSISTENT COUNTERS WITH THREADS SPAWNING
     * To see the beauty of our implemented model checker, run the following test with TrustStrategy.
     */

    @Test
    @DisplayName("A concurrent counter using nested thread spawning - RandomStrategy")
    void randomTestSimpleCounter() {
        var t = new TestTarget("org.example.concurrent.programs.simple.counter",
                    "SimpleCounter",
                    "main",
                    "src/test/java/org/example/concurrent/programs/simple/counter/"
        );
        System.out.println("SimpleCounter RandomStrategy Started");
        checker.configuration.strategyType = StrategyType.RANDOMSTRAREGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SimpleCounter RandomStrategy Finished");
    }

    @Test
    @DisplayName("A concurrent counter using nested thread spawning - TrustStrategy")
    void trustTestSimpleCounter() {
        var t = new TestTarget("org.example.concurrent.programs.simple.counter",
                "SimpleCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/simple/counter/"
        );
        System.out.println("SimpleCounter TrustStrategy Started");
        checker.configuration.strategyType = StrategyType.TRUSTSTRATEGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.executionGraphsPath = "src/main/resources/Visualized_Graphs/";
        assertTrue(checker.check(t), "SimpleCounter TrustStrategy Finished");
    }

    @Test
    @DisplayName("A concurrent counter using nested thread spawning - ReplayStrategy")
    void replayTestSimpleCounter() {
        var t = new TestTarget("org.example.concurrent.programs.simple.counter",
                "SimpleCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/simple/counter/"
        );
        System.out.println("SimpleCounter ReplayStrategy Started");
        checker.configuration.strategyType = StrategyType.REPLAYSTRATEGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SimpleCounter ReplayStrategy Finished");
    }

    /*
     *                                  DINING PHILOSOPHERS WITH DEADLOCK
     * As the following test uses synchronized blocks, it can be run with RandomStrategy and ReplayStrategy only.
     */

    @Test
    @DisplayName("Dining philosophers problem with deadlock - RandomStrategy")
    void randomTestDiningPhilosophers() {
        var t = new TestTarget("org.example.concurrent.programs.dining",
                "DiningPhilosophers",
                "main",
                "src/test/java/org/example/concurrent/programs/dining/"
        );
        System.out.println("DiningPhilosophers RandomStrategy Started");
        checker.configuration.strategyType = StrategyType.RANDOMSTRAREGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "DiningPhilosophers RandomStrategy Finished");
    }

    @Test
    @DisplayName("Dining philosophers problem with deadlock - ReplayStrategy")
    void replayTestDiningPhilosophers() {
        var t = new TestTarget("org.example.concurrent.programs.dining",
                "DiningPhilosophers",
                "main",
                "src/test/java/org/example/concurrent/programs/dining/"
        );
        System.out.println("DiningPhilosophers ReplayStrategy Started");
        checker.configuration.strategyType = StrategyType.REPLAYSTRATEGY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "DiningPhilosophers ReplayStrategy Finished");
    }

    /*
     *                                    DISABLED TESTS - DO NOT RUN
     */

    @Disabled("This test is disabled due to using lambda functions")
    @Test
    @DisplayName("Multiple threads each spawning new threads with a shared counter")
    void testMultipleSpawns() {
        var t = new TestTarget("org.example.concurrent.programs.thread_dependency",
                    "MultipleThreads",
                    "main",
                    "src/test/java/org/example/concurrent/programs/thread_dependency/"
        );
        assertTrue(checker.check(t), "Call works");
    }

    @Test
    @Disabled("This test is disabled due to using while(true) loop for thread waiting")
    @DisplayName("LockOne Mutex")
    void testMutex() {
        var t = new TestTarget("org.example.concurrent.programs.mutex",
                    "MainMutex",
                    "main",
                    "src/test/java/org/example/concurrent/programs/mutex/"
        );
        assertTrue(checker.check(t), "Call works");
    }
}