package org.mpisws.concurrent.programs.nondet.pqueue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

import java.util.LinkedList;

public class LockBasedBin implements Bin {

    private final LinkedList<SymbolicInteger> items;
    private final ReentrantLock lock;

    public LockBasedBin() {
        items = new LinkedList<>();
        lock = new ReentrantLock();
    }

    @Override
    public void put(SymbolicInteger item) throws JMCInterruptException {
        lock.lock();
        try {
            items.add(item);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SymbolicInteger get() throws JMCInterruptException {
        lock.lock();
        try {
            return items.isEmpty() ? null : items.removeFirst();
        } finally {
            lock.unlock();
        }
    }
}
