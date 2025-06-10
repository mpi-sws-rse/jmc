package org.mpisws.concurrent.programs.rww;

public class ThreadC extends Thread {

    Shared shared;

    public ThreadC(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        shared.getX();
    }
}
