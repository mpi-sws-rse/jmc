package org.mpisws.concurrent.programs.inconsistent.counter;

public class InconsistentCounter extends Thread {

    Counter counter;

    InconsistentCounter(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        if (counter.count == 0) {
            counter.count = counter.count + 1;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        InconsistentCounter thread1 = new InconsistentCounter(counter);
        InconsistentCounter thread2 = new InconsistentCounter(counter);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        assert (counter.count == 1) : " ***The assert did not pass, the counter value is " + counter.count + "***";

        System.out.println("[" + Thread.currentThread().getName() + " message] : If you see this message, the assert passed. The counter value is " + counter.count);

    }
}