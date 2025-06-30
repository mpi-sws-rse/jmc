package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.readN.Shared;
import org.mpisws.jmc.test.readN.ThreadA;

import java.util.ArrayList;
import java.util.List;


public class ReadNTest {

    private void readNProgram() {
        Shared shared = new Shared(0);
        List<ThreadA> threads = new ArrayList<ThreadA>();
        int numThreads = 10;
        for (int i = 0; i < numThreads; i++) {
            ThreadA thread = new ThreadA(shared);
            threads.add(thread);
        }
        for (int i = 0; i < numThreads; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < numThreads; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    // Running with JMC using the trust strategy.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1, strategy = "estimation", debug = true)
    public void runEstimationReadNTest() {
        readNProgram();
    }
}
