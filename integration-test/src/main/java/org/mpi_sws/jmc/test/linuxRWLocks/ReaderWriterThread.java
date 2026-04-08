package org.mpi_sws.jmc.test.linuxRWLocks;

public class ReaderWriterThread extends Thread {

    SharedData sharedData;
    RWLock mylock;

    public ReaderWriterThread(RWLock lock, SharedData data) {
        this.mylock = lock;
        this.sharedData = data;
    }

    /**
     *
     */
    @Override
    public void run() {
        for (int i = 0; i < 2; i++) {
            if ((i % 2) == 0) {
                mylock.readLock();
                int r = sharedData.sharedValue;
                mylock.readUnlock();
            } else {
                mylock.writeLock();
                sharedData.sharedValue = i;
                mylock.writeUnlock();
            }
        }
    }
}

