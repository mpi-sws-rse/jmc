package org.mpisws.jmc.agent.visitors;

public class JmcSyncScanData {
    private boolean hasSyncMethods;
    private boolean hasSyncStaticMethods;
    private boolean hasSyncBlocks;

    public JmcSyncScanData() {
        this.hasSyncMethods = false;
        this.hasSyncStaticMethods = false;
    }

    public boolean hasSyncMethods() {
        return hasSyncMethods;
    }
    public boolean hasSyncStaticMethods() {
        return hasSyncStaticMethods;
    }

    public boolean hasSyncBlocks() {
        return hasSyncBlocks;
    }

    public void setHasSyncMethods(boolean hasSyncMethods) {
        this.hasSyncMethods = hasSyncMethods;
    }

    public void setHasSyncStaticMethods(boolean hasSyncStaticMethods) {
        this.hasSyncStaticMethods = hasSyncStaticMethods;
    }

    public void setHasSyncBlocks(boolean hasSyncBlocks) {
        this.hasSyncBlocks = hasSyncBlocks;
    }
}
