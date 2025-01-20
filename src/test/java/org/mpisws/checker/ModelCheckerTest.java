package org.mpisws.checker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.*;
import org.mpisws.solver.SMTSolverTypes;
import org.mpisws.solver.SolverApproach;


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
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.verbose = true;
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
        checker.configuration.strategyType = StrategyType.TRUST; // The StrategyType.OP_TRUST is not supported for this test
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
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
        checker.configuration.verbose = false;
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
        checker.configuration.maxIterations = 20000;
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.verbose = true;
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
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
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
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
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
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
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
        checker.configuration.maxIterations = 100;
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
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
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        System.out.println("CorrectCounter Trust Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "CorrectCounter Trust Strategy Finished");
    }

    /*
     *                             SIMPLE INCONSISTENT COUNTERS WITH THREADS SPAWNING
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
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.verbose = true;
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
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
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
        checker.configuration.verbose = true;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SimpleCounter Replay Strategy Finished");
    }

    /*
     *                                  ATOMIC COUNTER
     */

    @Test
    @DisplayName("Atomic Counter")
    void randomTestAtomicCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.atomic.counter",
                "AtomicCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/atomic/counter/"
        );
        System.out.println("AtomicCounter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.verbose = true;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "AtomicCounter Trust Strategy Finished");
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
        checker.configuration.maxIterations = 1000;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
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
        checker.configuration.strategyType = StrategyType.TRUST; // The StrategyType.OP_TRUST is not supported for this test
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
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
     *                                  SYMBOLIC COUNTER
     */

    @Test
    @DisplayName("Symbolic Counter")
    void randomTestSymbolicCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.symbolic.counter",
                "SymbolicCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/symbolic/counter/"
        );
        System.out.println("SymbolicCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SymbolicCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("Symbolic Counter")
    void trustTestSymbolicCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.symbolic.counter",
                "SymbolicCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/symbolic/counter/"
        );
        System.out.println("SymbolicCounter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SymbolicCounter Trust Strategy Finished");
    }

    @Test
    @DisplayName("Symbolic Counter")
    void replayTestSymbolicCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.symbolic.counter",
                "SymbolicCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/symbolic/counter/"
        );
        System.out.println("SymbolicCounter Replay Strategy Started");
        checker.configuration.strategyType = StrategyType.REPLAY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SymbolicCounter Replay Strategy Finished");
    }

    /*
     *                                  DET HERLIHY–WING QUEUE I
     */

    @Test
    @DisplayName("Det Herlihy-Wing Queue")
    void trustTestDetHerlihyWingQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Herlihy-Wing Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Herlihy-Wing Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET HERLIHY–WING QUEUE II
     */

    @Test
    @Disabled("This test is disabled because the HWQueue is a blocking queue and the model checker is not able to handle it")
    @DisplayName("Det Herlihy-Wing Queue II")
    void trustTestDetHerlihyWingQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client2",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Herlihy-Wing Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Herlihy-Wing Queue II Trust Strategy Finished");
    }

    /*
     *                                  DET LOCK-BASED QUEUE I
     */

    @Test
    @DisplayName("Det Lock-Based Queue")
    void trustTestDetLockBasedQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client3",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Lock-Based Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Lock-Based Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET LOCK-BASED QUEUE II
     */

    @Test
    @DisplayName("Det Lock-Based Queue II")
    void trustTestDetLockFreeQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client4",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Lock-Based Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Lock-Based Queue II Trust Strategy Finished");
    }

    /*
     *                                  DET UNBOUNDED QUEUE I
     */

    @Test
    @DisplayName("Det Unbounded Queue")
    void trustTestDetUnboundedQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client5",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Unbounded Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Unbounded Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET UNBOUNDED QUEUE II
     */

    @Test
    @DisplayName("Det Unbounded Queue II")
    void trustTestDetUnboundedQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client6",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Unbounded Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Unbounded Queue II Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET MICHAEL-SCOTT QUEUE I
     */

    @Test
    @DisplayName("Det Michael-Scott Queue")
    void trustTestDetMichaelScottQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client7",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Michael-Scott Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Michael-Scott Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET MICHAEL-SCOTT QUEUE II
     */

    @Test
    @DisplayName("Det Michael-Scott Queue II")
    void trustTestDetMichaelScottQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client8",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det Michael-Scott Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Michael-Scott Queue II Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET MICHAEL-SCOTT QUEUE I
     */

    @Test
    @DisplayName("NonDet Michael-Scott Queue")
    void trustTestNonDetMichaelScottQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client7",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Michael-Scott Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Michael-Scott Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET MICHAEL-SCOTT QUEUE II
     */

    @Test
    @DisplayName("NonDet Michael-Scott Queue II")
    void trustTestNonDetMichaelScottQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client8",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Michael-Scott Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Michael-Scott Queue II Trust Strategy Finished");
        System.gc();
    }


    /*
     *                                  NONDET HERLIHY–WING QUEUE I
     */

    @Test
    @DisplayName("NonDet Herlihy-Wing Queue")
    void trustTestNonDetHerlihyWingQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Herlihy-Wing Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Herlihy-Wing Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET HERLIHY–WING QUEUE II
     */

    @Test
    @Disabled("This test is disabled because the HWQueue is a blocking queue and the model checker is not able to handle it")
    @DisplayName("NonDet Herlihy-Wing Queue II")
    void trustTestNonDetHerlihyWingQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client2",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Herlihy-Wing Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Herlihy-Wing Queue II Trust Strategy Finished");
    }

    /*
     *                                  NONDET LOCK-BASED QUEUE I
     */

    @Test
    @DisplayName("NonDet Lock-Based Queue")
    void trustTestNonDetLockBasedQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client3",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Lock-Based Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lock-Based Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET LOCK-BASED QUEUE II
     */

    @Test
    @DisplayName("NonDet Lock-Based Queue II")
    void trustTestNonDetLockFreeQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client4",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Lock-Based Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lock-Based Queue II Trust Strategy Finished");
    }


    /*
     *                                  NONDET UNBOUNDED QUEUE I
     */

    @Test
    @DisplayName("NonDet Unbounded Queue")
    void trustTestNonDetUnboundedQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client5",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Unbounded Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Unbounded Queue Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET UNBOUNDED QUEUE II
     */

    @Test
    @DisplayName("NonDet Unbounded Queue II")
    void trustTestNonDetUnboundedQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client6",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Unbounded Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Unbounded Queue II Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET LOCK-FREE UNBOUNDED QUEUE I
     */

    @Test
    @DisplayName("NonDet Lock-Free Unbounded Queue")
    void trustTestNonDetLockFreeUnboundedQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client7",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Lock-Free Unbounded Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lock-Free Unbounded Queue Trust Strategy Finished");
    }

    /*
     *                                  NONDET LOCK-FREE UNBOUNDED QUEUE II
     */

    @Test
    @DisplayName("NonDet Lock-Free Unbounded Queue II")
    void trustTestNonDetLockFreeUnboundedQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client8",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet Lock-Free Unbounded Queue II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lock-Free Unbounded Queue II Trust Strategy Finished");
    }

    /*
     *                                  DET SV-QUEUEI
     */

    @Test
    @DisplayName("Det SV-QueueI")
    void trustDetSVQueueI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client9",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det SV-QueueI Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det SV-QueueI Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET SV-QUEUEI
     */

    @Test
    @DisplayName("NonDet SV-QueueI")
    void trustNonDetSVQueueI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client9",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet SV-QueueI Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet SV-QueueI Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET SV-QUEUEII
     */

    @Test
    @DisplayName("Det SV-QueueII")
    void trustDetSVQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client10",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det SV-QueueII Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det SV-QueueII Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET SV-QUEUEII
     */

    @Test
    @DisplayName("NonDet SV-QueueII")
    void trustNonDetSVQueueII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client10",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet SV-QueueII Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet SV-QueueII Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET SV-QUEUEIII
     */

    @Test
    @DisplayName("Det SV-QueueIII")
    void trustDetSVQueueIII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.queue",
                "Client11",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/queue/"
        );
        System.out.println("Det SV-QueueIII Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det SV-QueueIII Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET SV-QUEUEIII
     */

    @Test
    @DisplayName("NonDet SV-QueueIII")
    void trustNonDetSVQueueIII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.queue",
                "Client11",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/queue/"
        );
        System.out.println("NonDet SV-QueueIII Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet SV-QueueIII Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET LOCK-FREE STACK I
     */

    @Test
    @DisplayName("Det Lock Free Stack")
    void trustTestDetLockFreeStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Lock Free Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Lock Free Stack Trust Strategy Finished");
    }

    /*
     *                                  NONDET LOCK FREE STACK I
     */

    @Test
    @DisplayName("NonDet Lock Free Stack")
    void trustTestNonDetLockFreeStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Lock Free Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lock Free Stack Trust Strategy Finished");
    }

    /*
     *                                  DET LOCK FREE STACK II
     */

    @Test
    @DisplayName("Det Lock Free Stack II")
    void trustTestDetLockFreeStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client2",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Lock Free Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Lock Free Stack II Trust Strategy Finished");
    }

    /*
     *                                  NONDET LOCK FREE STACK II
     */

    @Test
    @DisplayName("NonDet Lock Free Stack II")
    void trustTestNonDetLockFreeStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client2",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Lock Free Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lock Free Stack II Trust Strategy Finished");
    }



    /*
     *                                  DET ELIMINATION BACKOFF STACK I
     */

    @Test
    @DisplayName("Det Elimination Backoff Stack")
    void trustTestDetEliminationBackoffStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client3",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Elimination Backoff Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Elimination Backoff Stack Trust Strategy Finished");
    }

    /*
     *                                  NONDET ELIMINATION BACKOFF STACK I
     */

    @Test
    @DisplayName("NonDet Elimination Backoff Stack")
    void trustTestNonDetEliminationBackoffStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client3",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Elimination Backoff Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Elimination Backoff Stack Trust Strategy Finished");
    }

    /*
     *                                  DET ELIMINATION BACKOFF STACK II
     */

    @Test
    @DisplayName("Det Elimination Backoff Stack II")
    void trustTestDetEliminationBackoffStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client4",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Elimination Backoff Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Elimination Backoff Stack II Trust Strategy Finished");
    }

    /*
     *                                  NONDET ELIMINATION BACKOFF STACK II
     */

    @Test
    @DisplayName("NonDet Elimination Backoff Stack II")
    void trustTestNonDetEliminationBackoffStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client4",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Elimination Backoff Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Elimination Backoff Stack II Trust Strategy Finished");
    }

    /*
     *                                  DET TIME STAMPED STACK I
     */

    @Test
    @DisplayName("Det Time Stamped Stack")
    void trustTestDetTimeStampedStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client5",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Time Stamped Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Time Stamped Stack Trust Strategy Finished");
    }

    /*
     *                                  NONDET TIME STAMPED STACK I
     */

    @Test
    @DisplayName("NonDet Time Stamped Stack")
    void trustTestNonDetTimeStampedStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client5",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Time Stamped Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Time Stamped Stack Trust Strategy Finished");
    }

    /*
     *                                  DET TIME STAMPED STACK II
     */

    @Test
    @DisplayName("Det Time Stamped Stack II")
    void trustTestDetTimeStampedStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client6",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Time Stamped Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Time Stamped Stack II Trust Strategy Finished");
    }

    /*
     *                                  NONDET TIME STAMPED STACK II
     */

    @Test
    @DisplayName("NonDet Time Stamped Stack II")
    void trustTestNonDetTimeStampedStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client6",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Time Stamped Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Time Stamped Stack II Trust Strategy Finished");
    }

    /*
     *                                  DET AFEK-GAFNI-MORRISON STACK I
     */

    @Test
    @DisplayName("Det Afek-Gafni-Morrison Stack")
    void trustTestDetAfekGafniMorrisonStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client7",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Afek-Gafni-Morrison Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Afek-Gafni-Morrison Stack Trust Strategy Finished");
    }

    /*
     *                                  DET AFEK-GAFNI-MORRISON STACK II
     */

    @Test
    @DisplayName("Det Afek-Gafni-Morrison Stack II")
    void trustTestDetAfekGafniMorrisonStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client8",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Afek-Gafni-Morrison Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Afek-Gafni-Morrison Stack II Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET AFEK-GAFNI-MORRISON STACK I
     */

    @Test
    @DisplayName("NonDet Afek-Gafni-Morrison Stack")
    void trustTestNonDetAfekGafniMorrisonStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client7",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Afek-Gafni-Morrison Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Afek-Gafni-Morrison Stack Trust Strategy Finished");
    }

    /*
     *                                  NONDET AFEK-GAFNI-MORRISON STACK II
     */

    @Test
    @DisplayName("NonDet Afek-Gafni-Morrison Stack II")
    void trustTestNonDetAfekGafniMorrisonStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client8",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Afek-Gafni-Morrison Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Afek-Gafni-Morrison Stack II Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET MICHAEL-SCOTT STACK I
     */

    @Test
    @DisplayName("Det Michael-Scott Stack")
    void trustTestDetMichaelScottStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client9",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Michael-Scott Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Michael-Scott Stack Trust Strategy Finished");
    }

    /*
     *                                  DET MICHAEL-SCOTT STACK II
     */

    @Test
    @DisplayName("Det Michael-Scott Stack II")
    void trustTestDetMichaelScottStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client10",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Michael-Scott Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Michael-Scott Stack II Trust Strategy Finished");
    }

    /*
     *                                  NONDET MICHAEL-SCOTT STACK I
     */

    @Test
    @DisplayName("NonDet Michael-Scott Stack")
    void trustTestNonDetMichaelScottStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client9",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Michael-Scott Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Michael-Scott Stack Trust Strategy Finished");
    }

    /*
     *                                  NONDET MICHAEL-SCOTT STACK II
     */

    @Test
    @DisplayName("NonDet Michael-Scott Stack II")
    void trustTestNonDetMichaelScottStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client10",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Michael-Scott Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Michael-Scott Stack II Trust Strategy Finished");
    }

    /*
     *                                  DET TREIBER STACK I
     */

    @Test
    @DisplayName("Det Treiber Stack")
    void trustTestDetTreiberStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client11",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Treiber Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Treiber Stack Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET TREIBER STACK II
     */

    @Test
    @DisplayName("Det Treiber Stack II")
    void trustTestDetTreiberStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client12",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det Treiber Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Treiber Stack II Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET TREIBER STACK I
     */

    @Test
    @DisplayName("NonDet Treiber Stack")
    void trustTestNonDetTreiberStack() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client11",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Treiber Stack Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Treiber Stack Trust Strategy Finished");
    }

    /*
     *                                  NONDET TREIBER STACK II
     */

    @Test
    @DisplayName("NonDet Treiber Stack II")
    void trustTestNonDetTreiberStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client12",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet Treiber Stack II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Treiber Stack II Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET SV-STACKI
     */

    @Test
    @DisplayName("Det SV-StackI")
    void trustTestDetSVStackI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client13",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det SV-StackI Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        int[] dataDomain = {0, 1, 2};
        int size = 4;
        checker.configuration.generateSequences(dataDomain, size); // Enumerate exhaustively sequences to handle data non-determinism
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det SV-StackI Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET SV-STACKI
     */

    @Test
    @DisplayName("NonDet SV-StackI")
    void trustTestNonDetSVStackI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client13",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet SV-StackI Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet SV-StackI Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET SV-STACKII
     */

    @Test
    @DisplayName("Det SV-StackII")
    void trustTestDetSVStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.stack",
                "Client14",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/stack/"
        );
        System.out.println("Det SV-StackII Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det SV-StackII Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  NONDET SV-STACKII
     */

    @Test
    @DisplayName("NonDet SV-StackII")
    void trustTestNonDetSVStackII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.stack",
                "Client14",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/stack/"
        );
        System.out.println("NonDet SV-StackII Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet SV-StackII Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                 DET COARSE COUNTER
     */

    @Test
    @DisplayName("Det Coarse Counter")
    void trustTestDetCoarseCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.counter",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/counter"
        );
        System.out.println("Det Coarse Counter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        int[] dataDomain = {1, 2, 3};
        int size = 3;
        checker.configuration.generateSequences(dataDomain, size); // Enumerate exhaustively sequences to handle data non-determinism
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Coarse Counter Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                 NONDET COARSE COUNTER
     */

    @Test
    @DisplayName("NonDet Coarse Counter")
    void trustTestNonDetCoarseCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.counter",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/counter"
        );
        System.out.println("NonDet Coarse Counter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Coarse Counter Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                 DET ARRAY
     */

    @Test
    @DisplayName("Det Array")
    void trustTestDetArray() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.array",
                "DetArray",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/array"
        );
        System.out.println("Det Array Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Array Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                 DET LOOP
     */

    @Test
    @DisplayName("Det Loop")
    void trustTestDetLoop() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.loop",
                "DetLoop",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/loop"
        );
        System.out.println("Det Array Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Loop Trust Strategy Finished");
    }

    /*
     *                                 DET LOOP VARIANT
     */

    @Test
    @DisplayName("Det Loop Variant")
    void trustTestDetLoopVariant() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.loopVariant",
                "DetLoop",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/loopVariant"
        );
        System.out.println("Det Array Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Loop Variant Trust Strategy Finished");
    }

    /*
     *                                 NONDET ARRAY
     */

    @Test
    @DisplayName("Nondet Array")
    void trustTestNondetArray() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.array",
                "NondetArray",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/array"
        );
        System.out.println("Nondet Array Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Nondet Array Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                 NONDET LOOP
     */

    @Test
    @DisplayName("Nondet Loop")
    void trustTestNondetLoop() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.loop",
                "NondetLoop",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/loop"
        );
        System.out.println("Nondet Array Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Nondet Loop Trust Strategy Finished");
    }

    /*
     *                                 NONDET LOOP VARIANT
     */

    @Test
    @DisplayName("Nondet Loop Variant")
    void trustTestNondetLoopVariant() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.loopVariant",
                "NondetLoop",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/loopVariant"
        );
        System.out.println("Nondet Array Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Nondet Loop Variant Trust Strategy Finished");
    }

    /*
     *                                  NONDET COARSE LIST I
     */

    @Test
    @DisplayName("NonDet Coarse List I")
    void trustTestNonDetCoarseListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Coarse List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Coarse List I Trust Strategy Finished");
    }

    /*
     *                                  NONDET COARSE LIST II
     */

    @Test
    @DisplayName("NonDet Coarse List II")
    void trustTestNonDetCoarseListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client2",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Coarse List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Coarse List II Trust Strategy Finished");
    }

    /*
     *                                  NONDET FINE LIST I
     */
    @Test
    @DisplayName("NonDet Fine List I")
    void trustTestNonDetFineListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client3",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Fine List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Fine List I Trust Strategy Finished");
    }

    /*
     *                                  NONDET FINE LIST II
     */
    @Test
    @DisplayName("NonDet Fine List II")
    void trustTestNonDetFineListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client4",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Fine List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Fine List II Trust Strategy Finished");
    }

    /*
     *                                  NONDET OPTIMIST LIST I
     */
    @Test
    @DisplayName("NonDet Optimist List I")
    void trustTestNonDetOptimistListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client5",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Optimist List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Optimist List I Trust Strategy Finished");
    }

    /*
     *                                  NONDET OPTIMIST LIST II
     */
    @Test
    @DisplayName("NonDet Optimist List II")
    void trustTestNonDetOptimistListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client6",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Optimist List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Optimist List II Trust Strategy Finished");
    }

    /*
     *                                  NONDET LAZY LIST I
     */
    @Test
    @DisplayName("NonDet Lazy List I")
    void trustTestNonDetLazyListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client7",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Lazy List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lazy List I Trust Strategy Finished");
    }

    /*
     *                                  NONDET LAZY LIST II
     */
    @Test
    @DisplayName("NonDet Lazy List II")
    void trustTestNonDetLazyListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.lists",
                "Client8",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/lists/"
        );
        System.out.println("NonDet Lazy List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lazy List II Trust Strategy Finished");
    }

    /*
     *                                  DET COARSE LIST I
     */

    @Test
    @DisplayName("Det Coarse List I")
    void trustTestDetCoarseListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Coarse List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Coarse List I Trust Strategy Finished");
    }

    /*
     *                                  DET COARSE LIST II
     */

    @Test
    @DisplayName("Det Coarse List II")
    void trustTestDetCoarseListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client2",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Coarse List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Coarse List II Trust Strategy Finished");
    }

    /*
     *                                  DET FINE LIST I
     */
    @Test
    @DisplayName("Det Fine List I")
    void trustTestDetFineListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client3",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Fine List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Fine List I Trust Strategy Finished");
    }

    /*
     *                                  DET FINE LIST II
     */
    @Test
    @DisplayName("Det Fine List II")
    void trustTestDetFineListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client4",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Fine List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Fine List II Trust Strategy Finished");
    }

    /*
     *                                  DET OPTIMIST LIST I
     */
    @Test
    @DisplayName("Det Optimist List I")
    void trustTestDetOptimistListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client5",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Optimist List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Optimist List I Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET OPTIMIST LIST II
     */
    @Test
    @DisplayName("Det Optimist List II")
    void trustTestDetOptimistListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client6",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Optimist List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Optimist List II Trust Strategy Finished");
    }

    /*
     *                                  DET LAZY LIST I
     */
    @Test
    @DisplayName("Det Lazy List I")
    void trustTestDetLazyListI() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client7",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Lazy List I Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.Z3;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Lazy List I Trust Strategy Finished");
        System.gc();
    }

    /*
     *                                  DET LAZY LIST II
     */
    @Test
    @DisplayName("Det Lazy List II")
    void trustTestDetLazyListII() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.lists",
                "Client8",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/lists/"
        );
        System.out.println("Det Lazy List II Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.NO_SOLVER;
        checker.configuration.schedulingPolicy = SchedulingPolicy.LIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Lazy List II Trust Strategy Finished");
    }

    /*
     *                                  DET LOCK-BASED ARRAY-BASED BOUNDED PRIORITY QUEUE
     */

    @Test
    @DisplayName("Det Lock-Based Array-Based Bounded Priority Queue")
    void trustTestDetLockBasedArrayBasedBoundedPriorityQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.det.pqueue",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/det/pqueue/"
        );
        System.out.println("Det Lock-Based Array-Based Bounded Priority Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Det Lock-Based Array-Based Bounded Priority Queue Trust Strategy Finished");
    }

    /*
     *                                  NOTDET LOCK-BASED ARRAY-BASED BOUNDED PRIORITY QUEUE
     */

    @Test
    @DisplayName("NonDet Lock-Based Array-Based Bounded Priority Queue")
    void trustTestNonDetLockBasedArrayBasedBoundedPriorityQueue() {
        var t = new TestTarget("org.mpisws.concurrent.programs.nondet.pqueue",
                "Client1",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/nondet/pqueue/"
        );
        System.out.println("NonDet Lock-Based Array-Based Bounded Priority Queue Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.FIFO;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "NonDet Lock-Based Array-Based Bounded Priority Queue Trust Strategy Finished");
    }



    /*
     *                                  PARKING COUNTER
     */

    @Test
    @DisplayName("Parking Counter")
    void randomTestParkingCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.parking.counter",
                "ParkingCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/parking/counter/"
        );
        System.out.println("ParkingCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.verbose = false;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "ParkingCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("Parking Counter")
    void trustTestParkingCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.parking.counter",
                "ParkingCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/parking/counter/"
        );
        System.out.println("ParkingCounter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.TRUST; // The StrategyType.OP_TRUST is not supported for this test
        checker.configuration.verbose = false;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "ParkingCounter Trust Strategy Finished");
    }

    @Test
    @DisplayName("Parking Counter")
    void replayTestParkingCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.parking.counter",
                "ParkingCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/parking/counter/"
        );
        System.out.println("ParkingCounter Replay Strategy Started");
        checker.configuration.strategyType = StrategyType.REPLAY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "ParkingCounter Replay Strategy Finished");
    }

    /*
     *                                  THREAD POOL COUNTER
     */
    @Test
    @DisplayName("Pool Counter")
    void randomTestPoolCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.pool.counter",
                "PoolCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/pool/counter/"
        );
        System.out.println("PoolCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "PoolCounter Random Strategy Finished");
    }


    /*
     *                                    SIMPLE MESSAGE
     */

    @Test
    @DisplayName("SimpleMessage")
    void randomTestSimpleMessage() {
        var t = new TestTarget("org.mpisws.concurrent.programs.simple.message",
                "SimpleMessage",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/simple/message/"
        );
        System.out.println("SimpleMessage Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "SimpleMessage Random Strategy Finished");
    }

    @Test
    @DisplayName("SimpleMessage")
    void mustTestSimpleMessage() {
        var t = new TestTarget("org.mpisws.concurrent.programs.simple.message",
                "SimpleMessage",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/simple/message/"
        );
        System.out.println("SimpleMessage Must Strategy Started");
        checker.configuration.strategyType = StrategyType.MUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "SimpleMessage Must Strategy Finished");
    }

    /*
     *                                    TAGGED MESSAGE
     */

    @Test
    @DisplayName("TaggedMessage")
    void randomTestTaggedMessage() {
        var t = new TestTarget("org.mpisws.concurrent.programs.tagged.message",
                "TaggedMessage",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/tagged/message/"
        );
        System.out.println("TaggedMessage Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "TaggedMessage Random Strategy Finished");
    }

    @Test
    @DisplayName("TaggedMessage")
    void mustTestTaggedMessage() {
        var t = new TestTarget("org.mpisws.concurrent.programs.tagged.message",
                "TaggedMessage",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/tagged/message/"
        );
        System.out.println("TaggedMessage Must Strategy Started");
        checker.configuration.strategyType = StrategyType.MUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "TaggedMessage Must Strategy Finished");
    }

    /*
     *                                    SYNC MESSAGE
     */

    @Test
    @DisplayName("SyncMessage")
    void randomTestSyncMessage() {
        var t = new TestTarget("org.mpisws.concurrent.programs.sync.message",
                "SyncMessage",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/sync/message/"
        );
        System.out.println("SyncMessage Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "SyncMessage Random Strategy Finished");
    }

    @Test
    @DisplayName("SyncMessage")
    void mustTestSyncMessage() {
        var t = new TestTarget("org.mpisws.concurrent.programs.sync.message",
                "SyncMessage",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/sync/message/"
        );
        System.out.println("SyncMessage Must Strategy Started");
        checker.configuration.strategyType = StrategyType.MUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "SyncMessage Must Strategy Finished");
    }

    /*
     *                                    MESSAGE COUNTER
     */
    @Test
    @DisplayName("MessageCounter")
    void randomTestMessageCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.message.counter",
                "MessageCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/message/counter/"
        );
        System.out.println("MessageCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "MessageCounter Random Strategy Finished");
    }

    @Test
    @DisplayName("MessageCounter")
    void mustTestMessageCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.message.counter",
                "MessageCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/message/counter/"
        );
        System.out.println("MessageCounter Must Strategy Started");
        checker.configuration.strategyType = StrategyType.MUST;
        checker.configuration.schedulingPolicy = SchedulingPolicy.NON_DET;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        checker.configuration.programType = ProgramType.MESSAGE_PASS;
        assertTrue(checker.check(t), "MessageCounter Must Strategy Finished");
    }

    /**********************************************************************************
     /*
     *                             DISABLED TESTS - DO NOT RUN
     *
     **********************************************************************************
     */

    /*
     *                                  RANDOM COUNTER
     */
    @Test
    @Disabled("This test is disabled due to using symbolic assignment")
    @DisplayName("Random Counter")
    void randomTestRandomCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.random.counter",
                "RandomCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/random/counter/"
        );
        System.out.println("RandomCounter Random Strategy Started");
        checker.configuration.strategyType = StrategyType.RANDOM;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "RandomCounter Random Strategy Finished");
    }

    @Test
    @Disabled("This test is disabled due to using symbolic assignment")
    @DisplayName("Random Counter")
    void trustTestRandomCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.random.counter",
                "RandomCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/random/counter/"
        );
        System.out.println("RandomCounter Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "RandomCounter Trust Strategy Finished");
    }

    @Test
    @Disabled("This test is disabled due to using symbolic assignment")
    @DisplayName("Random Counter")
    void replayTestRandomCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.random.counter",
                "RandomCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/random/counter/"
        );
        System.out.println("RandomCounter Replay Strategy Started");
        checker.configuration.strategyType = StrategyType.REPLAY;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "RandomCounter Replay Strategy Finished");
    }

    /*
     *                                      SYNC COUNTERS
     */

    @Test
    @Disabled("This test is disabled due to using synchronized methods")
    @DisplayName("A correct concurrent counter using synchronized methods - Random")
    void randomTestSyncCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.sync.counter",
                "SyncCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/sync/counter/"
        );
        checker.configuration.strategyType = StrategyType.RANDOM;
        System.out.println("SyncCounter Random Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SyncCounter Random Strategy Finished");
    }

    @Test
    @Disabled("This test is disabled due to using synchronized methods")
    @DisplayName("A correct concurrent counter using synchronized methods - Trust")
    void trustTestSyncCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.sync.counter",
                "SyncCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/sync/counter/"
        );
        checker.configuration.strategyType = StrategyType.TRUST;
        System.out.println("SyncCounter Trust Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "SyncCounter Trust Strategy Finished");
    }

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

    /*
     *                                      STATIC COUNTERS
     */

    @Test
    @Disabled("This test is disabled due to using static variables")
    @DisplayName("A correct concurrent counter using synchronized static methods - Random")
    void randomTestStaticCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.shared.counter",
                "StaticCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/shared/counter/"
        );
        checker.configuration.strategyType = StrategyType.RANDOM;
        System.out.println("StaticCounter Random Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "StaticCounter Random Strategy Finished");
    }

    @Test
    @Disabled("This test is disabled due to using static variables")
    @DisplayName("A correct concurrent counter using synchronized static methods - Trust")
    void trustTestStaticCounter() {
        var t = new TestTarget("org.mpisws.concurrent.programs.shared.counter",
                "StaticCounter",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/shared/counter/"
        );
        checker.configuration.strategyType = StrategyType.TRUST;
        System.out.println("StaticCounter Trust Strategy Started");
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "StaticCounter Trust Strategy Finished");
    }

    /*
     *                                 SYMBOLIC GCD
     */

    @Test
    @Disabled("This test is disabled due to using symbolic while loop")
    @DisplayName("Symbolic GCD")
    void trustTestSymbolicGCD() {
        var t = new TestTarget("org.mpisws.concurrent.programs.symbolic.gcd",
                "ParallelGCD",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/symbolic/gcd/"
        );
        System.out.println("Symbolic GCD Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = true;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Symbolic GCD Trust Strategy Finished");
    }

    /*
     *                                  CONCRETE GCD
     */

    @Test
    @Disabled("This test is disabled due to using symbolic while loop")
    @DisplayName("Concrete GCD")
    void trustTestConcreteGCD() {
        var t = new TestTarget("org.mpisws.concurrent.programs.concrete.gcd",
                "ParallelGCD",
                "main",
                "src/test/java/org/mpisws/concurrent/programs/concrete/gcd/"
        );
        System.out.println("Concrete GCD Trust Strategy Started");
        checker.configuration.strategyType = StrategyType.OPT_TRUST;
        checker.configuration.graphExploration = GraphExploration.DFS;
        checker.configuration.verbose = false;
        checker.configuration.solverApproach = SolverApproach.INCREMENTAL;
        checker.configuration.schedulingPolicy = SchedulingPolicy.RR;
        checker.configuration.solverType = SMTSolverTypes.PRINCESS;
        checker.configuration.buggyTracePath = "src/main/resources/buggyTrace/";
        checker.configuration.buggyTraceFile = "buggyTrace.obj";
        assertTrue(checker.check(t), "Symbolic GCD Trust Strategy Finished");
    }
}