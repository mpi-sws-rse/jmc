package org.mpisws.jmc.test.det.counter;

public class DecThread extends Thread {

    public Counter counter;
    public int id;

    public DecThread(Counter counter, int id) {
        this.counter = counter;
        this.id = id;
    }

    public void run() {
        if (id % 2 == 0) {
            counter.dec1();
        } else {
            counter.dec2();
        }
    }
}
