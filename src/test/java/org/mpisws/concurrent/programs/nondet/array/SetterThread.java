package org.mpisws.concurrent.programs.nondet.array;

public class SetterThread extends Thread {

    public Array array;

    public SetterThread(Array array) {
        this.array = array;
    }

    @Override
    public void run() {
        int t = array.x;
        array.a[t] = 1;
        array.x = t + 1;
    }
}
