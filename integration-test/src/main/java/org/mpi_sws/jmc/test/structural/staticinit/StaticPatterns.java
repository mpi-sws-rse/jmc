package org.mpi_sws.jmc.test.structural.staticinit;

import java.util.concurrent.atomic.AtomicInteger;

public class StaticPatterns {

    // ========== Test Helper Classes ==========

    /**
     * Simple class with static initialization.
     * The static block will be transformed to $staticInit by JMC.
     */
    public static class ClassWithStaticInit {
        public static String VALUE;

        static {
            // This will become $staticInit() with yield points
            VALUE = "initialized";
        }
    }

    /**
     * Class with complex static initialization involving computation.
     */
    public static class ComplexStaticInit {
        public static int BASE_VALUE;
        public static int COMPUTED_VALUE;

        static {
            // Multiple field writes = multiple yield points
            BASE_VALUE = 10;
            COMPUTED_VALUE = BASE_VALUE + 32;  // Should be 42
        }
    }

    /**
     * Class with multiple static fields.
     */
    public static class MultipleStaticFields {
        public static String FIELD1;
        public static String FIELD2;
        public static String FIELD3;

        static {
            FIELD1 = "field1";
            FIELD2 = "field2";
            FIELD3 = "field3";
        }
    }
    /**
     * Class with static initialization that has side effects.
     * This helps detect if static init runs multiple times.
     */
    public static class StaticInitCounter {
        private static AtomicInteger globalCounter = new AtomicInteger(0);
        public static int VALUE;

        static {
            // Increment global counter to track how many times this runs
            globalCounter.incrementAndGet();
            VALUE = 100;
        }

        public static int getGlobalCounter() {
            return globalCounter.get();
        }

        public static void resetGlobalCounter() {
            globalCounter.set(0);
        }
    }

    /**
     * Class mimicking Iceberg's SnapshotSummary with method chaining.
     * This simulates: Joiner.on(",").withKeyValueSeparator("=")
     */
    public static class IcebergLikeClass {
        public static String JOINER;

        static {
            // Method chaining that creates intermediate objects
            String temp = new StringBuilder()
                    .append("joiner")
                    .append("-")
                    .append("configured")
                    .toString();
            JOINER = temp;
        }
    }

    public static class A {
        public static int x = 3;
    }

    public static class B {
        public static int y = A.x;
    }
}
