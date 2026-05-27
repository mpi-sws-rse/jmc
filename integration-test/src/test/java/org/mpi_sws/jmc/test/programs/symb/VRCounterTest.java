package org.mpi_sws.jmc.test.programs.symb;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.symb.violation.VCounter;
import org.mpi_sws.jmc.test.symb.violation.VThread1;
import org.mpi_sws.jmc.test.symb.violation.VThread2;

public class VRCounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, solver = "z3")
    public void runVRCounterTest() {
        VCounter counter = new VCounter();
        SymbolicInteger x = new SymbolicInteger("x");
        VThread1 threadA = new VThread1(counter, x);
        VThread2 threadB = new VThread2(counter, x);

        threadA.start();
        threadB.start();

        try {
            threadA.join();
            threadB.join();
        } catch (InterruptedException e) {

        }
    }
}
