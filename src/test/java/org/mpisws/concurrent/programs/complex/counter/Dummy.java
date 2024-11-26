package org.mpisws.concurrent.programs.complex.counter;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Dummy {

    public void exe() throws InterruptedException, JMCInterruptException {
        Counter counter = new Counter();
        ReentrantLock lock = new ReentrantLock();
        CounterThread thread1 = new CounterThread(counter, lock);
        CounterThread thread2 = new CounterThread(counter, lock);
        thread1.exe();
        thread2.exe();
        thread1.join1();
        thread2.join1();
        assert counter.count == 2;
        System.out.println(
                "["
                        + Thread.currentThread().getName()
                        + " message] : If you see this message, the assert passed. The counter"
                        + " value is "
                        + counter.count);
    }
}
