package org.mpisws.concurrent.programs.symbolic.counter;

import org.mpisws.symbolic.SymbolicInteger;

public class SymbolicCounter extends Thread {

    Counter counter;

    public SymbolicCounter(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        counter.inc();
    }

    public static void main(String[] args) throws InterruptedException {
        SymbolicInteger x = new SymbolicInteger("x", true);
        Counter counter = new Counter(x);
        SymbolicCounter sc1 = new SymbolicCounter(counter);
        SymbolicCounter sc2 = new SymbolicCounter(counter);
        sc1.start();
        sc2.start();
        sc1.join();
        sc2.join();
        assert counter.getCount() == 2 : "Counter should be 2, but is " + counter.getCount();
        System.out.println("If you see this message, the test passed!");
    }
}
