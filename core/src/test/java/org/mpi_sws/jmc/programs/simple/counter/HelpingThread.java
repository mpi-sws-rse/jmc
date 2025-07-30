package org.mpi_sws.jmc.programs.simple.counter;

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
