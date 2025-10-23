package org.mpi_sws.jmc.test.synth.ttaslock;


public class Worker extends Thread {
    private final TTASLock lock;
    private final LockHolder lockHolder;
    private final int id;

    public Worker(TTASLock lock, LockHolder lockHolder, int id) {
        this.lock = lock;
        this.lockHolder = lockHolder;
        this.id = id;
    }

    @Override
    public void run() {
        lock.acquire();
        lockHolder.setId(id);
        int holderId = lockHolder.getId();
        assert holderId == id : "BUG!";
        lock.release();
    }
}
