package org.mpi_sws.jmc.agent.visitors;

/**
 * JmcSyncScanData is a data class that holds information about synchronization constructs in a
 * class. It tracks whether the class has synchronized methods, synchronized static methods, and
 * synchronized blocks.
 */
public class JmcSyncScanData {
    private boolean hasSyncMethods;
    private boolean hasSyncStaticMethods;
    private boolean hasSyncBlocks;

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
