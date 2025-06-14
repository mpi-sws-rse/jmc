package org.mpisws.jmc.agent.visitors;

public class JmcSyncScanData {
    private boolean hasSyncMethods;
    private boolean hasSyncStaticMethods;

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

    public void setHasSyncMethods(boolean hasSyncMethods) {
        this.hasSyncMethods = hasSyncMethods;
    }

    public void setHasSyncStaticMethods(boolean hasSyncStaticMethods) {
        this.hasSyncStaticMethods = hasSyncStaticMethods;
    }
}
