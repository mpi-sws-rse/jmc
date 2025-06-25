package org.mpisws.jmc.test.det.counter.coarse;

public class DecThread extends Thread {

    public CCounter counter;
    public int id;

    public DecThread(CCounter counter, int id) {
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
