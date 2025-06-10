package org.mpisws.concurrent.programs.rww;

public class ThreadA extends Thread {

    Shared shared;

    public ThreadA(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        shared.getX();
    }
}
