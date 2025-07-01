package org.mpisws.jmc.programs.det.stack.lockFree.timeStamped;

import org.mpisws.jmc.api.util.concurrent.JmcAtomicInteger;

public class TSCAS {

    public JmcAtomicInteger counter;

    public TSCAS() {
        this.counter = new JmcAtomicInteger(1);
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
