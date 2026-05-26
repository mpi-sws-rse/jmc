package org.mpi_sws.jmc.test.programs.symb;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.symb.rCounter.SharedCounter;
import org.mpi_sws.jmc.test.symb.rCounter.SRThread;

public class RCounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, solver = "z3")
    public void runRCounterTest() {
        SharedCounter counter = new SharedCounter();
        SymbolicInteger x1 = new SymbolicInteger("x1");
        SRThread thread1 = new SRThread(counter, x1);
        SRThread thread2 = new SRThread(counter, x1);
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
