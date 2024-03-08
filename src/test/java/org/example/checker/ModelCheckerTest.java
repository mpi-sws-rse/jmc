package org.example.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.*;
// import org.junit.jupiter.api.RepeatedTest;


class ModelCheckerTest {
    private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    ModelChecker checker;

    
    @BeforeAll
    public static void setUpStreams() {
        System.out.println("Setting up streams");
        //System.setOut(new PrintStream(outContent, true));
    }

    @AfterAll
    public static void cleanUpStreams() {
        System.setOut(null);
    }

    @BeforeEach
    void setUp() {
        CheckerConfiguration config = new CheckerConfiguration.ConfigurationBuilder().withVerbose(true).build();
        checker = new ModelChecker(config);
    }

//    @AfterEach
//    void tearDown() {
//        checker = null;
//        String output = outContent.toString();
//        System.out.println("Output: " + output);
//    }

    // various "litmus tests": concurrent threads with a shared variable

    @Test
    @DisplayName("Buggy counter that deadlocks")
    void testBuggyCounterThatDeadlocks() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, IOException {
        var t = new TestTarget("org.example.concurrent.programs.wrong.counter.",
                "BuggyCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/wrong/counter/");
        assertEquals(true, checker.check(t),
                "Call works");
    }

    @Test
    @DisplayName("Inconsistent counter with a race condition")
    void testInconsistentCounter() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, IOException {
        System.out.println("InconsistentCounter");
        var t = new TestTarget("org.example.concurrent.programs.inconsistent.counter.",
                "InconsistentCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/inconsistent/counter/");
        assertEquals(true, checker.check(t),
                "Call works");
    }

    @Test
    @DisplayName("Complex counter")
    void testComplexCounter() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, IOException {
        var t = new TestTarget("org.example.concurrent.programs.complex.counter.",
                "ComplexCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/complex/counter/");
        assertEquals(true, checker.check(t),
                "Call works");
    }

    @Test
    @DisplayName("Correct counter")
    void testCorrectCounter() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, IOException {
        var t = new TestTarget("org.example.concurrent.programs.correct.counter.",
                "CorrectCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/correct/counter/");
        assertEquals(true, checker.check(t),
                "Call works");
    }

    @Test
    @DisplayName("Simple counter")
    void testSimpleCounter() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, IOException {
        var t = new TestTarget("org.example.concurrent.programs.simple.counter.",
                "SimpleCounter",
                "main",
                "src/test/java/org/example/concurrent/programs/simple/counter/");
        assertEquals(true, checker.check(t),
                "Call works");
    }

    @Test
    @DisplayName("Multiple threads each spawning new threads with a shared counter")
    void testMultipleSpawns() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, IOException {
        var t = new TestTarget("org.example.concurrent.programs.thread_dependency",
                "MultipleThreads",
                "main",
                "src/test/java/org/example/concurrent/programs/thread_dependency/");
        assertEquals(true, checker.check(t),
                "Call works");
    }
}
