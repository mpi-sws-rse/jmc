package org.mpisws.concurrent.programs.inconsistent.counter;

public class Setter extends Thread {

    private final Counter counter;

    public Setter(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        counter.set(0);
    }
}
