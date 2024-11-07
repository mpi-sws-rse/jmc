package org.mpisws.concurrent.programs.det.stack.lockFree.elimination;

import org.mpisws.util.concurrent.AtomicStampedReference;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LockFreeExchanger<V> {
    public final int EMPTY = 0;
    public final int WAITING = 1;
    public final int BUSY = 2;

    AtomicStampedReference<V> slot = new AtomicStampedReference<V>(null, EMPTY);

    public V exchange(V myItem, long timeout, TimeUnit unit)
            throws JMCInterruptException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        long timeBound = System.nanoTime() + nanos;
        int[] stampHolder = {EMPTY};
        while (true) {
            if (System.nanoTime() > timeBound) {
                throw new TimeoutException();
            }
            V yrItem = slot.get(stampHolder);
            int stamp = stampHolder[0];
            switch (stamp) {
                case EMPTY:
                    if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
                        while (System.nanoTime() < timeout) {
                            yrItem = slot.get(stampHolder);
                            if (stampHolder[0] == BUSY) {
                                slot.set(null, EMPTY);
                                return yrItem;
                            }
                        }
                        if (slot.compareAndSet(myItem, null, WAITING, EMPTY)) {
                            throw new TimeoutException();
                        } else {
                            yrItem = slot.get(stampHolder);
                            slot.set(null, EMPTY);
                            return yrItem;
                        }
                    }
                    break;
                case WAITING:
                    if (slot.compareAndSet(yrItem, myItem, WAITING, BUSY)) {
                        return yrItem;
                    }
                    break;
                case BUSY:
                    break;
                default: // impossible
            }
        }
    }
}
