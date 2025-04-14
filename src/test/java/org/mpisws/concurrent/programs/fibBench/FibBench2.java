package org.mpisws.concurrent.programs.fibBench;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class FibBench2 {

    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();
        Shared shared = new Shared();
        int size = 5;
        T1 t1 = new T1(shared, size, lock);
        T2 t2 = new T2(shared, size, lock);

        t1.start();
        t2.start();

        try {
            boolean condI = shared.i >= 144;
            boolean condJ = shared.j >= 144;

            Utils.assertion(!(condI || condJ), "Assertion Fail! ");
        } catch (JMCInterruptException e) {

        }
    }
}
