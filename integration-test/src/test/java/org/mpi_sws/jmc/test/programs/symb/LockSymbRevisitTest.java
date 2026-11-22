package org.mpi_sws.jmc.test.programs.symb;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.symb.lockrev.EvalInLockThread;
import org.mpi_sws.jmc.test.symb.lockrev.LockOnlyThread;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A backward revisit caused by a lock acquire that deletes a constraint evaluation.
 *
 * <p>{@code lockThenEval}: thread A takes the lock, evaluates {@code x == 42} and releases it;
 * thread B takes and releases the lock. Up to the equivalence of ConDpor there are four executions:
 * two orders of the critical sections, two outcomes of the evaluation. {@code lockOnly} is the
 * control without the evaluation: two executions.
 */
public class LockSymbRevisitTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, solver = "z3")
    public void lockThenEval() {
        ReentrantLock lock = new ReentrantLock();
        SymbolicInteger x = new SymbolicInteger("x");
        EvalInLockThread a = new EvalInLockThread(lock, x);
        LockOnlyThread b = new LockOnlyThread(lock);
        a.start();
        b.start();
        try {
            a.join();
            b.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, solver = "z3")
    public void lockOnly() {
        ReentrantLock lock = new ReentrantLock();
        LockOnlyThread a = new LockOnlyThread(lock);
        LockOnlyThread b = new LockOnlyThread(lock);
        a.start();
        b.start();
        try {
            a.join();
            b.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
