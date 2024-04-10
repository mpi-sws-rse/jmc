package org.mpisws.concurrent.programs.complex.counter;

public class CounterThread extends Thread{
    Counter counter;
    public CounterThread(Counter counter) {
        this.counter = counter;
    }
    @Override
    public void run() {
        synchronized (counter) {
            counter.count = counter.count + 1;
            System.out.println("[" + this.getName() + " message] : " + "The counter value is " + counter.count);
        }
    }

    public void exe(){
        this.start();
    }
}
