package org.mpisws.jmc.programs.mutex;

import org.mpisws.jmc.programs.mutex.common.LocalThread;

public class MainMutex {

    public static void main(String[] args) throws InterruptedException {
        int incrementCount = 5;
        Lock lock = new LockOne(2);
        final Counter counter = new Counter(0, lock);

        Thread threadOne = new LocalThread(counter, incrementCount, 0);
        Thread threadTwo = new LocalThread(counter, incrementCount, 1);

        threadOne.start();
        threadTwo.start();

        threadOne.join();
        threadTwo.join();

        System.out.println(counter.getValue());
    }
}
