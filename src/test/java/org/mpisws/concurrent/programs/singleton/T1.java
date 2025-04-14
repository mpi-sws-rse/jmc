package org.mpisws.concurrent.programs.singleton;

public class T1 extends Thread {

    Shared shared;

    public T1(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        shared.c = '0';
    }
}
