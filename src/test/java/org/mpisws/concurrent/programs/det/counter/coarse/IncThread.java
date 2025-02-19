package org.mpisws.concurrent.programs.det.counter.coarse;

import org.mpisws.util.concurrent.JMCInterruptException;

public class IncThread extends Thread {

    public CCounter counter;
    public int id;

    public IncThread(CCounter counter, int id) {
        this.counter = counter;
        this.id = id;
    }

    public void run() {
        if (id % 2 == 0) {
            try {
                counter.inc1();
            } catch (JMCInterruptException e) {

            }
        } else {
            try {
                counter.inc2();
            } catch (JMCInterruptException e) {

            }
        }
    }
}
