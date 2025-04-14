package org.mpisws.concurrent.programs.singleton;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;
import org.mpisws.util.concurrent.Utils;

public class SingletonWUP {

    public static void main(String[] args) {
        Shared shared = new Shared();
        ReentrantLock lock = new ReentrantLock();
        T0 t0 = new T0(shared, lock);

        t0.start();

        try {
            t0.join();
            Utils.assertion(shared.c == 'X' || shared.c == 'Y', "shared.c != 'X'");
        } catch (InterruptedException e) {

        } catch (JMCInterruptException e) {

        }
    }
}
