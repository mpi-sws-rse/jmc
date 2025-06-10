package org.mpisws.concurrent.programs.violation;

import org.mpisws.symbolic.SymbolicInteger;

public class Violation {

    public static void main(String[] args) {
        Counter counter = new Counter();
        SymbolicInteger x = new SymbolicInteger("x", true);
        ThreadA threadA = new ThreadA(counter, x);
        ThreadB threadB = new ThreadB(counter, x);

        threadA.start();
        threadB.start();

        try {
            threadA.join();
            threadB.join();
        } catch (InterruptedException e) {

        }
    }
}
