package org.example.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.*;


class ModelCheckerTest {
//    private static final PrintStream originalOut = System.out;
//    private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private ModelChecker checker;


//    @BeforeAll
//    public static void setUpStreams() {
//        System.setOut(new PrintStream(outContent, true));
//    }
//
//    @AfterAll
//    public static void cleanUpStreams() {
//        System.setOut(null);
//    }

    @BeforeEach
    void setUp() {
        System.out.println("setUp");
        CheckerConfiguration config = new CheckerConfiguration.ConfigurationBuilder().withVerbose(true).build();
        checker = new ModelChecker(config);
    }

//    @AfterEach
//    void tearDown() {
//        System.setOut(originalOut);
//        System.out.println("finished");
//        checker = null;
//        outContent.reset();
//    }

    // various "litmus tests": concurrent threads with a shared variable

    @Test
    @DisplayName("Buggy counter that deadlocks")
    void testBuggyCounterThatDeadlocks() {
        var t = new TestTarget("org.example.concurrent.programs.wrong.counter.",
                    "BuggyCounter",
                    "main",
                    "src/test/java/org/example/concurrent/programs/wrong/counter/"
        );
        assertTrue(checker.check(t), "Call works");
    }



    @Test
    @DisplayName("Inconsistent counter with a race condition")

    void testInconsistentCounter() {
        System.out.println("InconsistentCounter");
        var t = new TestTarget("org.example.concurrent.programs.inconsistent.counter.",
                        "InconsistentCounter",
                        "main",
                        "src/test/java/org/example/concurrent/programs/inconsistent/counter/"
        );
        System.out.println("InconsistentCounter finished");
        assertTrue(checker.check(t), "call works");
    }

    @Disabled("This test is disabled")
    @Test
    @DisplayName("Complex counter")
    void testComplexCounter() {
        var t = new TestTarget("org.example.concurrent.programs.complex.counter.",
                        "ComplexCounter",
                        "main",
                        "src/test/java/org/example/concurrent/programs/complex/counter/"
        );
        assertTrue(checker.check(t), "Call works");
    }

    @Test
    @DisplayName("Correct counter")
    void testCorrectCounter() {
        var t = new TestTarget("org.example.concurrent.programs.correct.counter.",
                    "CorrectCounter",
                    "main",
                    "src/test/java/org/example/concurrent/programs/correct/counter/"
        );
        assertTrue(checker.check(t), "Call works");
    }

    @Disabled("This test is disabled")
    @Test
    @DisplayName("Simple counter")
    void testSimpleCounter() {
        var t = new TestTarget("org.example.concurrent.programs.simple.counter.",
                    "SimpleCounter",
                    "main",
                    "src/test/java/org/example/concurrent/programs/simple/counter/"
        );
        assertTrue(checker.check(t), "Call works");
    }

    @Disabled("This test is disabled")
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
}
