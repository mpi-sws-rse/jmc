package org.mpisws.concurrent.programs.rww;

public class ThreadB extends Thread {

    Shared shared;

    public ThreadB(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        shared.getX();
    }
}
