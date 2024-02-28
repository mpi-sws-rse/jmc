package org.example.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ModelCheckerTest {

    ModelChecker checker;

    @BeforeEach                                         
    void setUp() {
        CheckerConfiguration config = new CheckerConfiguration.ConfigurationBuilder().withVerbose(true).build();
        checker = new ModelChecker(config);
    }

    @Test                                               
    @DisplayName("Call check")   
    void testCall() {
        try {
            var t = new TestTarget(
                "org.example.concurrent.programs.wrong.counter.",
            "BuggyCounter",
            "main",
            "src/main/java/org/example/concurrent/programs/wrong/counter/");
            assertEquals(true, checker.check(t), 
                "Call works");  
        } catch (Exception e) {
            System.err.println("Exception raised: " + e);
        }
    }

}
