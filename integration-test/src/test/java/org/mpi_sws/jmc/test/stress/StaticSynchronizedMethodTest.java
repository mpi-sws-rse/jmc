package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

/**
 * Test to verify JMC handles static synchronized methods correctly.
 * This reproduces the issue seen with HadoopTables.createOrGetLockManager().
 */
public class StaticSynchronizedMethodTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testStaticSynchronizedMethod() {
        // Call a static synchronized method
        String result = TestClass.getOrCreateValue();
        System.out.println("Result: " + result);
    }

    /**
     * Test class with a static synchronized method, similar to HadoopTables.
     * No explicit static initializer block.
     */
    static class TestClass {
        private static String value;

        // Static synchronized method - JMC should register a lock for this
        private static synchronized String getOrCreateValue() {
            if (value == null) {
                value = "initialized";
            }
            return value;
        }
    }
}
