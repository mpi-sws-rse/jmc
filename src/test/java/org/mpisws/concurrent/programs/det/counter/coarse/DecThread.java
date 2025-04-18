package org.mpisws.concurrent.programs.det.counter.coarse;

import org.mpisws.util.concurrent.JMCInterruptException;

public class DecThread extends Thread {

    public CCounter counter;
    public int id;

    public DecThread(CCounter counter, int id) {
        this.counter = counter;
        this.id = id;
    }

    public void run() {
        if (id % 2 == 0) {
            try {
                counter.dec1();
            } catch (JMCInterruptException e) {

            }
        } else {
            try {
                counter.dec2();
            } catch (JMCInterruptException e) {

            }
        }
    }
}
