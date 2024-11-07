package org.mpisws.concurrent.programs.inconsistent.counter;

/**
 * This is a simple concurrent counter program that is expected to increment the counter by 3.
 * However, the program is expected to fail because the counter is not thread-safe. In details,
 * there is a data-race on the counter object.
 */
public class InconsistentCounter extends Thread {

    Counter counter;

    InconsistentCounter(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        counter.count = counter.count + 1;
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        InconsistentCounter thread1 = new InconsistentCounter(counter);
        InconsistentCounter thread2 = new InconsistentCounter(counter);
        InconsistentCounter thread3 = new InconsistentCounter(counter);

        thread1.start();
        thread2.start();
        thread3.start();

        thread1.join();
        thread2.join();
        thread3.join();

        assert (counter.count == 3)
                : " ***The assert did not pass, the counter value is " + counter.count + "***";

        System.out.println(
                "[Final Program Message] : If you see this message, the assert passed. The counter"
                    + " value is "
                        + counter.count);
    }
}
