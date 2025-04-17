package org.mpisws.jmc.programs.random.counter;

import org.mpisws.jmc.util.JmcRandom;
import org.mpisws.jmc.util.concurrent.JmcThread;

public class RandomCounterIncr extends JmcThread {
    private final Counter counter;
    private final JmcRandom random;

    public RandomCounterIncr(Counter counter) {
        this.counter = counter;
        this.random = new JmcRandom();
    }

    @Override
    public void run1() {
        counter.update(random.nextInt(10));
    }

    public static void main(String[] args) {
        Counter counter = new Counter();
        RandomCounterIncr thread1 = new RandomCounterIncr(counter);
        RandomCounterIncr thread2 = new RandomCounterIncr(counter);
        RandomCounterIncr thread3 = new RandomCounterIncr(counter);
        thread1.start();
        thread2.start();
        thread3.start();
        try {
            thread1.join1();
            thread2.join1();
            thread3.join1();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Final counter value: " + counter.getValue());
    }
}
