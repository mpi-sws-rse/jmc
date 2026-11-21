package org.mpi_sws.jmc.test.symb.lockrev;

import org.mpi_sws.jmc.api.symbolic.SymbolicFormula;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.integer.ArithmeticFormula;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;

import java.util.concurrent.locks.ReentrantLock;

/** Takes the lock, evaluates {@code x == 42} inside the critical section, releases the lock. */
public class EvalInLockThread extends Thread {

    private final ReentrantLock lock;
    private final SymbolicInteger x;

    public EvalInLockThread(ReentrantLock lock, SymbolicInteger x) {
        this.lock = lock;
        this.x = x;
    }

    @Override
    public void run() {
        lock.lock();
        ArithmeticFormula formula = new ArithmeticFormula();
        JmcBooleanFormula op = formula.eq(x, 42);
        SymbolicFormula symbolicFormula = new SymbolicFormula();
        symbolicFormula.evaluate(op);
        lock.unlock();
    }
}
