package org.mpisws.jmc.test.synth.big0;

public class ThreadThree extends Thread {

    Data data;

    public ThreadThree(Data data) {
        this.data = data;
    }

    @Override
    public void run() {
        data.setX(1);
        data.setY(1);
        data.setX(2);
        data.setY(2);
        data.setX(3);
        data.setY(3);
    }
}
