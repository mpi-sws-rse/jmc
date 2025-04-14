package org.mpisws.concurrent.programs.sigma;

public class T extends Thread {

    public Shared shared;

    public T(Shared shared) {
        this.shared = shared;
    }

    @Override
    public void run() {
        shared.arrayIndex++;
        shared.array[shared.arrayIndex] = 1;
    }
}
