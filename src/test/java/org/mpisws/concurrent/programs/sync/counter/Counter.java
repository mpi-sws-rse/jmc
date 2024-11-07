package org.mpisws.concurrent.programs.sync.counter;

public class Counter {

    public int count = 0;

    public synchronized void inc() {
        count = count + 1;
    }

    public static synchronized int test(double y, long x, int z) {
        return (int) (x + y + z);
    }

    public int getCount() {
        return count;
    }
}
