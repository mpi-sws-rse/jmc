package org.mpisws.jmc.test.det.counter;

public class IncThread extends Thread {

    public Counter counter;
    public int id;

    public IncThread(Counter counter, int id) {
        this.counter = counter;
        this.id = id;
    }

    public void run() {
        if (id % 2 == 0) {
            counter.inc1();
        } else {
            counter.inc2();
        }
    }
}
