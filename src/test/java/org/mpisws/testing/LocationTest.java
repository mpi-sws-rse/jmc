package org.mpisws.testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class LocationTest {

    private static Object testObject = null;

    @BeforeAll
    public static void setUp() {
        testObject = new Object();
    }

    public static void init() {
        if (testObject == null) {
            testObject = new Object();
        }
    }

    @Test
    public void testHashCode() {
        int initialHashCode = testObject.hashCode();
        for (int i = 0; i < 100; i++) {
            init();
            assert testObject.hashCode() == initialHashCode;
        }
    }
}
