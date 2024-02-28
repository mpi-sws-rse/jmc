package org.example.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ModelCheckerTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    ModelChecker checker;

    /* 
    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent, true));
    }
    @AfterEach
    public void cleanUpStreams() {
        System.setOut(null);
    }
    */

    @BeforeEach                                        
    void setUp() {
        CheckerConfiguration config = new CheckerConfiguration.ConfigurationBuilder().withVerbose(true).build();
        checker = new ModelChecker(config);
    }

    @Test                                               
    @DisplayName("Call check")   
    void testCall() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        System.out.println("HERE");
            var t = new TestTarget("org.example.concurrent.programs.wrong.counter.", 
                "BuggyCounter",
                "main",
                "src/main/java/org/example/concurrent/programs/wrong/counter/");
            assertEquals(true, checker.check(t), 
                "Call works");  
    }

}
