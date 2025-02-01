package org.mpisws.concurrent.programs.nondet.stack.lockFree.elimination;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.AtomicStampedReference;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.concurrent.TimeoutException;

public class LockFreeExchanger<V> {

    public final int EMPTY = 0;
    public final int WAITING = 1;
    public final int BUSY = 2;

    AtomicStampedReference<V> slot = new AtomicStampedReference<V>(null, EMPTY);

    public V exchange(V myItem) throws JMCInterruptException {
        int[] stampHolder = {EMPTY};
        /*while (true) {
            SymbolicInteger timeBound = new SymbolicInteger("timeBound", false);
            ArithmeticStatement as = new ArithmeticStatement();
            as.add(timeout, (int) System.nanoTime());
            timeBound.assign(as);
            ArithmeticFormula f = new ArithmeticFormula();
            SymbolicOperation op1 = f.lt(timeBound, (int) System.nanoTime());
            SymbolicFormula sf = new SymbolicFormula();
            if (sf.evaluate(op1)) {
                throw new TimeoutException();
            }
            V yrItem = slot.get(stampHolder);
            int stamp = stampHolder[0];
            switch (stamp) {
                case EMPTY:
                    if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
                        SymbolicOperation op2 = f.gt(timeout, (int) System.nanoTime());
                        while (sf.evaluate(op2)) {
                            yrItem = slot.get(stampHolder);
                            if (stampHolder[0] == BUSY) {
                                slot.set(null, EMPTY);
                                return yrItem;
                            }
                            op2 = f.gt(timeout, (int) System.nanoTime());
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
        }*/
        // Unwinding the loop for one iteration
        V yrItem = slot.get(stampHolder);
        int stamp = stampHolder[0];
        switch (stamp) {
            case EMPTY:
                if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
                    /*while (sf.evaluate(op2)) {
                        yrItem = slot.get(stampHolder);
                        if (stampHolder[0] == BUSY) {
                            slot.set(null, EMPTY);
                            return yrItem;
                        }
                        op2 = f.gt(timeout, (int) System.nanoTime());
                    }*/
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
        return null;
    }
}
