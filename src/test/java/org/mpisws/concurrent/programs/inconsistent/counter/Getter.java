package org.mpisws.concurrent.programs.inconsistent.counter;

public class Getter extends Thread {

    private final Counter counter;

    public Getter(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        counter.get();
    }
}
