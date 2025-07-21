package org.mpisws.jmc.test.det.stack.lockFree.timeStamped;

import java.util.concurrent.atomic.AtomicInteger;

public class TSCAS {

    public AtomicInteger counter;

    public TSCAS() {
        this.counter = new AtomicInteger(1);
    }

    public TimeStamp newStamp() {
        int timeStamp = counter.get();
        // Delay is omitted
        int newTimeStamp = counter.get();
        if (timeStamp != newTimeStamp) {
            return new TimeStamp(timeStamp, newTimeStamp - 1);
        }
        if (counter.compareAndSet(timeStamp, timeStamp + 1)) {
            return new TimeStamp(timeStamp, timeStamp);
        }
        return new TimeStamp(timeStamp, counter.get() - 1);
    }
}
