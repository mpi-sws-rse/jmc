package org.mpi_sws.jmc.agent.visitors;

/**
 * Mutable result of the Phase-1 synchronization pre-scan.
 *
 * <p>{@link JmcVisitor#transform} creates one instance and passes it to {@link JmcSyncScanVisitor},
 * which sets the three flags below while scanning the class. The instance is then handed to {@link
 * JmcSyncMethodVisitor}, which uses the flags to decide what synchronization instrumentation to emit
 * (for example, it only instruments constructors when the class has synchronized instance methods).
 * The pre-scan is required because {@code JmcSyncMethodVisitor} must know these facts before it starts
 * rewriting method bodies.
 */
public class JmcSyncScanData {
    /** True if the class declares at least one synchronized instance method. */
    private boolean hasSyncMethods;
    /** True if the class declares at least one synchronized static method. */
    private boolean hasSyncStaticMethods;
    /** True if any method contains a synchronized block ({@code MONITORENTER}/{@code MONITOREXIT}). */
    private boolean hasSyncBlocks;

    /** Creates an instance with all flags cleared; the flags are populated by {@link JmcSyncScanVisitor}. */
    public JmcSyncScanData() {
        this.hasSyncMethods = false;
        this.hasSyncStaticMethods = false;
    }

    /**
     * Returns true if the class has synchronized methods.
     *
     * @return true if the class has synchronized methods, false otherwise
     */
    public boolean hasSyncMethods() {
        return hasSyncMethods;
    }

    /**
     * Returns true if the class has synchronized static methods.
     *
     * @return true if the class has synchronized static methods, false otherwise
     */
    public boolean hasSyncStaticMethods() {
        return hasSyncStaticMethods;
    }

    /**
     * Returns true if the class has synchronized blocks.
     *
     * @return true if the class has synchronized blocks, false otherwise
     */
    public boolean hasSyncBlocks() {
        return hasSyncBlocks;
    }

    /**
     * Sets whether the class has synchronized methods.
     *
     * @param hasSyncMethods true if the class has synchronized methods, false otherwise
     */
    public void setHasSyncMethods(boolean hasSyncMethods) {
        this.hasSyncMethods = hasSyncMethods;
    }

    /**
     * Sets whether the class has synchronized static methods.
     *
     * @param hasSyncStaticMethods true if the class has synchronized static methods, false
     *     otherwise
     */
    public void setHasSyncStaticMethods(boolean hasSyncStaticMethods) {
        this.hasSyncStaticMethods = hasSyncStaticMethods;
    }

    /**
     * Sets whether the class has synchronized blocks.
     *
     * @param hasSyncBlocks true if the class has synchronized blocks, false otherwise
     */
    public void setHasSyncBlocks(boolean hasSyncBlocks) {
        this.hasSyncBlocks = hasSyncBlocks;
    }
}
