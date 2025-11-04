package org.mpi_sws.jmc.test.linuxRWLocks;

public class ReaderThread extends Thread {

    RWLock mylock;
    SharedData sharedData;

    public ReaderThread(RWLock lock, SharedData data) {
        this.mylock = lock;
        this.sharedData = data;
    }

    @Override
    public void run() {
        mylock.readLock();
        //int r = sharedData.sharedValue;
        mylock.readUnlock();
    }
}
