package org.mpisws.concurrent.programs.simple.counter;

public class HelpingThread extends Thread {

    Counter counter;

    HelpingThread(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        counter.increment();
    }
}
