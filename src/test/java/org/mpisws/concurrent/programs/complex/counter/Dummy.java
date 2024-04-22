package org.mpisws.concurrent.programs.complex.counter;

public class Dummy {

    public void exe() throws InterruptedException {
        Counter counter = new Counter();
        CounterThread thread1 = new CounterThread(counter);
        thread1.setName("Thread 1");
        CounterThread thread2 = new CounterThread(counter);
        thread2.setName("Thread 2");
        thread1.exe();
        thread2.exe();
        thread1.join();
        thread2.join();
        assert (counter.count == 2) : " ***The assert did not pass, the counter value is " + counter.count + "***";
        System.out.println("[" + Thread.currentThread().getName() + " message] : If you see this message, the assert passed. The counter value is " + counter.count);
    }
}
