package org.mpi_sws.jmc.strategies.tracker;

public class LockRequest {

    private final long taskId;

    private final boolean fair;

    private final boolean blocking;

    private LockRequest(long taskId, boolean fair, boolean blocking) {
        this.taskId = taskId;
        this.fair = fair;
        this.blocking = blocking;
    }

    public static LockRequest fairBlockingRequest(long taskId) {
        return new LockRequest(taskId, true, true);
    }

    public static LockRequest fairNonblockingRequest(long taskId) {
        return new LockRequest(taskId, false, false);
    }

    public static LockRequest unfairBlockingRequest(long taskId) {
        return new LockRequest(taskId, false, true);
    }

    public static LockRequest unfairNonblockingRequest(long taskId) {
        return new LockRequest(taskId, true, false);
    }

    public long getTaskId() {
        return taskId;
    }

    public boolean isFair() {
        return fair;
    }
    public boolean isBlocking() {
        return blocking;
    }
}
