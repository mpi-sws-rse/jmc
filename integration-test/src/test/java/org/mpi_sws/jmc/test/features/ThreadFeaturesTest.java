package org.mpi_sws.jmc.test.features;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.api.util.concurrent.JmcThread;

public class ThreadFeaturesTest {
    public static void getCurrentThread() {
        Thread t = Thread.currentThread();
    }

    public static class TestClass {
        public static void testMethod() {
            System.out.println("Test");
        }
    }

    public static class TestClassExtension extends TestClass {
    }

    public static void callTestClass() {
        TestClassExtension.testMethod();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testGetCurrentThread() {
        // Static method invocations on extended class are fine.
        // So the following is okay.
        callTestClass();

        // But for jdk classes like Thread, it causes issues.
        getCurrentThread();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct")
    public void testGetCurrentThreadPct() {
        // Static method invocations on extended class are fine.
        // So the following is okay.
        callTestClass();

        // But for jdk classes like Thread, it causes issues.
        getCurrentThread();
    }
}
