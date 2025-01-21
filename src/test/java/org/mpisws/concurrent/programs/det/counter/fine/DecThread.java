package org.mpisws.concurrent.programs.det.counter.fine;

public class DecThread extends Thread {

    public FCounter counter;
    public int id;

    public DecThread(FCounter counter, int id) {
        this.counter = counter;
        this.id = id;
    }

    public void run() {
        if (id % 2 == 0) {
            try {
                counter.dec1();
            } catch (Exception e) {

            }
        } else {
            try {
                counter.dec2();
            } catch (Exception e) {

            }
        }
    }
}
