package org.mpisws.concurrent.programs.inconsistent.counter;

/**
 * This is a simple concurrent counter program that is expected to increment the counter by 3. However, the program is
 * expected to fail because the counter is not thread-safe. In details, there is a data-race on the counter object.
 */
public class InconsistentCounter {

    Counter counter;

    InconsistentCounter(Counter counter) {
        this.counter = counter;
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        int numThreads = 4;

        Getter[] getter = new Getter[numThreads];
        Setter[] setter = new Setter[numThreads];

        for (int i = 0; i < numThreads; i++) {
            getter[i] = new Getter(counter);
        }

        for (int i = 0; i < numThreads; i++) {
            setter[i] = new Setter(counter);
        }

        for (int i = 0; i < numThreads; i++) {
            getter[i].start();
        }

        for (int i = 0; i < numThreads; i++) {
            setter[i].start();
        }

        for (int i = 0; i < numThreads; i++) {
            getter[i].join();
        }

        for (int i = 0; i < numThreads; i++) {
            setter[i].join();
        }

//        assert (counter.count == 3) : " ***The assert did not pass, the counter value is " + counter.count + "***";
//
//        System.out.println("[Final Program Message] : If you see this message, the assert passed. The counter value is " + counter.count);

    }
}