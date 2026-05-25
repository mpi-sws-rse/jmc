package org.mpi_sws.jmc.test.programs.symb;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.symb.rCounter.RCounter;
import org.mpi_sws.jmc.test.symb.rCounter.RThread;

public class RCounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, solver = "z3")
    @Disabled
    public void runRCounterTest() {
        RCounter counter = new RCounter();
        SymbolicInteger x1 = new SymbolicInteger("x1");
        SymbolicInteger x2 = new SymbolicInteger("x2");
        SymbolicInteger y1 = new SymbolicInteger("y1");
        SymbolicInteger y2 = new SymbolicInteger("y2");
        SymbolicBoolean a1 = new SymbolicBoolean("a1");
        SymbolicBoolean a2 = new SymbolicBoolean("a2");
        SymbolicBoolean b1 = new SymbolicBoolean("b1");
        SymbolicBoolean b2 = new SymbolicBoolean("b2");
        RThread thread1 = new RThread(counter, x1, y1, a1, b1);
        RThread thread2 = new RThread(counter, x2, y2, a2, b2);
        thread1.start();
        thread2.start();
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
