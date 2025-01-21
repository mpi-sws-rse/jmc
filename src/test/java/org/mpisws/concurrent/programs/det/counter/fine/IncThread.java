package org.mpisws.concurrent.programs.det.counter.fine;

import org.mpisws.util.concurrent.JMCInterruptException;

public class IncThread extends Thread {

    FCounter counter;
    int id;

    public IncThread(FCounter counter, int id) {
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
