package org.example.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ModelCheckerTest {
    private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    ModelChecker checker;

    
    @BeforeAll
    public static void setUpStreams() {
        System.setOut(new PrintStream(outContent, true));
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
}
