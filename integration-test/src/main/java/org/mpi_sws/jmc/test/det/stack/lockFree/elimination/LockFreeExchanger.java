package org.mpi_sws.jmc.test.det.stack.lockFree.elimination;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;

import java.util.concurrent.atomic.AtomicStampedReference;

public class LockFreeExchanger<V> {
    public final int EMPTY = 0;
    public final int WAITING = 1;
    public final int BUSY = 2;

    AtomicStampedReference<V> slot = new AtomicStampedReference<V>(null, EMPTY);

    public V exchange(V myItem) {
        int[] stampHolder = {EMPTY};
        // Unwinding the loop for one iteration
        V yrItem = slot.get(stampHolder);
        int stamp = stampHolder[0];
        switch (stamp) {
            case EMPTY:
                if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
                    // Unwinding the loop for one iteration
                    yrItem = slot.get(stampHolder);
                    if (stampHolder[0] == BUSY) {
                        slot.set(null, EMPTY);
                        return yrItem;
                    }

                    if (slot.compareAndSet(myItem, null, WAITING, EMPTY)) {
                        //throw new TimeoutException();
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
        JmcAssume.assume(false);
        return null;
    }
}
