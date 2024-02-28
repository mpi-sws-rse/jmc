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
        checker = new ModelChecker();
    }

    @Test                                               
    @DisplayName("Call check")   
    void testCall() {
        assertEquals(true, checker.check(), 
                "Call works");  
    }

}
