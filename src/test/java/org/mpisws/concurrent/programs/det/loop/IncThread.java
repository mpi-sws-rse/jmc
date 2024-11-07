package org.mpisws.concurrent.programs.det.loop;

public class IncThread extends Thread {

    Numbers numbers;

    public IncThread(Numbers numbers) {
        this.numbers = numbers;
    }

    @Override
    public void run() {
        int t;
        t = numbers.x;
        numbers.x = t + 1;
    }
}
