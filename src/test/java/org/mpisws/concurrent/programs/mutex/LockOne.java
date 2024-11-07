package org.mpisws.concurrent.programs.mutex;

import org.mpisws.concurrent.programs.mutex.common.LocalThread;

public class LockOne implements Lock {

    private final boolean[] flag;

    public LockOne(int n) {
        this.flag = new boolean[n];
    }

    @Override
    public void lock() {
        LocalThread t = (LocalThread) Thread.currentThread();
        int i = t.getThreadId();
        int j = 1 - i;
        flag[i] = true;
        while (flag[j]) {
            System.out.println("Thread " + i + " is waiting for thread " + j);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void unlock() {
        LocalThread t = (LocalThread) Thread.currentThread();
        int i = t.getThreadId();
        flag[i] = false;
    }
}
