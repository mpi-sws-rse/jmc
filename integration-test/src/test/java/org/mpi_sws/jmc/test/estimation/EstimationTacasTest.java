package org.mpi_sws.jmc.test.estimation;

import org.mpi_sws.jmc.test.synth.big0.*;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.synth.ttaslock.LockHolder;
import org.mpi_sws.jmc.test.synth.ttaslock.TTASLock;
import org.mpi_sws.jmc.test.synth.ttaslock.Worker;

public class EstimationTacasTest {

    /**
     * big0(): This program has 4 threads and 4 shared variables (x,y,z,w).
     * Each thread accesses the shared variables in a different pattern.
     * ThreadOne reads all variables multiple times. ThreadTwo reads some variables.
     * ThreadThree writes to x,y twice. ThreadFour writes to z,w twice.
     */
    private void big0() {
        Data data = new Data();
        ThreadFour threadFour = new ThreadFour(data);
        ThreadThree threadThree = new ThreadThree(data);
        ThreadTwo threadTwo = new ThreadTwo(data);
        ThreadOne threadOne = new ThreadOne(data);

        threadFour.start();
        threadThree.start();
        threadTwo.start();
        threadOne.start();

        try {
            threadOne.join();
            threadTwo.join();
            threadThree.join();
            threadFour.join();
        } catch (InterruptedException e) {

        }
    }

    /**
     * ttasLock(n): This program has n threads competing for a TTAS lock.
     * Each thread acquires the lock, sets a shared variable (lockHolder) to its
     * id, reads back the variable, and asserts that the value read is equal to its
     * id. This checks for mutual exclusion and ensures that the shared variable is
     * not corrupted.
     *
     * @param n number of threads
     */
    private void ttasLock(int n) {
        TTASLock lock = new TTASLock();
        LockHolder lockHolder = new LockHolder();
        Worker[] workers = new Worker[n];
        for (int i = 0; i < n; i++) {
            workers[i] = new Worker(lock, lockHolder, i);
        }
        for (int i = 0; i < n; i++) {
            workers[i].start();
        }
        for (int i = 0; i < n; i++) {
            try {
                workers[i].join();
            } catch (InterruptedException e) {

            }
        }
    }

    /** ----------------------------------------------------*/

    /**
     * big0 test suite
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. Fork-Join DAG-based estimation
     * 4. TruSt-based estimation
     * 5. Weighted TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(loggerTree = true, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    @JmcExpectExecutions(69112)
    public void runBig0Trust() {
        big0();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000, strategy = "dag-estimation", debug = false)
    public void runBig0DagEstimation() {
        big0();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000, strategy = "fj-dag-estimation", debug = false)
    public void runBig0FjDagEstimation() {
        big0();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 2000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO, debug = false)
    public void runBig0TrustEstimation() {
        big0();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runBig0WgTrustEstimation() {
        big0();
    }

    /** ----------------------------------------------------*/

    /**
     * ttasLock test suite
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. Fork-Join DAG-based estimation
     * 4. TruSt-based estimation
     * 5. Weighted TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    @JmcExpectExecutions(1656) // For n = 2 and 3 is 16 and 1656
    public void runTtasLockTrust() {
        ttasLock(7);
    }
}
