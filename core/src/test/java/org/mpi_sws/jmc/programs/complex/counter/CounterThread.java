package org.mpi_sws.jmc.programs.complex.counter;

import org.mpi_sws.jmc.api.util.concurrent.JmcThread;
import org.mpi_sws.jmc.api.util.concurrent.JmcReentrantLock;

public class CounterThread extends JmcThread {
    Counter counter;
    JmcReentrantLock lock;

    public CounterThread(Counter counter, JmcReentrantLock lock) {
        super();
        this.counter = counter;
        this.lock = lock;
    }

    @Override
    public void run1() {
//        try {
            lock.lock();
            counter.count = counter.count + 1;
            lock.unlock();
//        } catch (JMCInterruptException e) {
//            System.out.println(
//                    "[" + this.getName() + " message] : " + "The thread was interrupted.");
//        }
    }

    public void exe() {
        this.start();
    }
}
