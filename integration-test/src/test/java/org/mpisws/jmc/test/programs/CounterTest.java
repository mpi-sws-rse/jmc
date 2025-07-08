package org.mpisws.jmc.test.programs;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.test.ParametricCounter;
import org.mpisws.jmc.test.det.counter.CCounter;
import org.mpisws.jmc.test.det.counter.DecThread;
import org.mpisws.jmc.test.det.counter.IncThread;
import org.mpisws.jmc.test.det.counter.FCounter;

public class CounterTest {

    private void coarseCounter(String[] args) {
        int SIZE = args.length;
        CCounter counter = new CCounter();
        int NUM_INSERTIONS = (int) Math.ceil(SIZE / 2.0);
        int NUM_DELETIONS = (int) Math.floor(SIZE / 2.0);

        int i;
        IncThread[] threads = new IncThread[NUM_INSERTIONS];
        for (i = 0; i < NUM_INSERTIONS; i++) {
            int arg = Integer.parseInt(args[i]);
            threads[i] = new IncThread(counter, arg);
        }

        DecThread[] threads2 = new DecThread[NUM_DELETIONS];
        for (i = NUM_INSERTIONS; i < SIZE; i++) {
            int arg = Integer.parseInt(args[i]);
            threads2[i - NUM_INSERTIONS] = new DecThread(counter, arg);
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            threads[i].start();
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            threads2[i].start();
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
            }
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            try {
                threads2[i].join();
            } catch (InterruptedException e) {
            }
        }
    }

    private void FineCounter(String[] args) {
        int SIZE = args.length;

        FCounter counter = new FCounter();
        int NUM_INSERTIONS = (int) Math.ceil(SIZE / 2.0);
        int NUM_DELETIONS = (int) Math.floor(SIZE / 2.0);

        int i;
        IncThread[] threads = new IncThread[NUM_INSERTIONS];
        for (i = 0; i < NUM_INSERTIONS; i++) {
            int arg = Integer.parseInt(args[i]);
            threads[i] = new IncThread(counter, arg);
        }

        DecThread[] threads2 = new DecThread[NUM_DELETIONS];
        for (i = NUM_INSERTIONS; i < SIZE; i++) {
            int arg = Integer.parseInt(args[i]);
            threads2[i - NUM_INSERTIONS] = new DecThread(counter, arg);
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            threads[i].start();
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            threads2[i].start();
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
            }
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            try {
                threads2[i].join();
            } catch (InterruptedException e) {
            }
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(720) // For input 6
    public void runCoarseCounterTest() {
        // TODO : Make the test parametric
        coarseCounter(new String[] {"0", "1", "2", "0", "1", "2"});
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, debug = true)
    @JmcTrustStrategy
    // TODO :: Fix this test
    public void runFineCounterTest() {
        // TODO : Make the test parametric
        FineCounter(new String[] {"0", "1", "2", "3"});
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = true)
    public void testRandomCounter() {
        ParametricCounter counter = new ParametricCounter(2);
        counter.run();
        assert counter.getCounterValue() == 2;
    }

    @JmcCheck
    @JmcCheckConfiguration(strategy = "trust", numIterations = 100, debug = true)
    public void testTrustCounter() {
        ParametricCounter counter = new ParametricCounter(2);
        counter.run();
        assert counter.getCounterValue() == 2;
    }
}
