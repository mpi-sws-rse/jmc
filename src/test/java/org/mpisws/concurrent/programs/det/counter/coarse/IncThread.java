package org.mpisws.concurrent.programs.det.counter.coarse;

public class IncThread extends Thread {

    public CCounter counter;
    public int id;

    public IncThread(CCounter counter, int id) {
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
