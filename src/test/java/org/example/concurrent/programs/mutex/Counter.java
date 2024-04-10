package org.example.concurrent.programs.mutex;

public class Counter {

    private int value;
    private Lock lock;

    public Counter(final int c, Lock lock) {
        this.value = c;
        this.lock = lock;
    }

    public int getAndIncrement() {
        lock.lock();
        try {
            int temp = value;
            value = temp + 1;
            return temp;
        } finally {
            lock.unlock();
        }
    }

    public int getValue() {
        return value;
    }
}
