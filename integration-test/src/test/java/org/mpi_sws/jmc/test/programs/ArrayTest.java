package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.test.det.array.Array;
import org.mpi_sws.jmc.test.det.array.SetterThread;

import java.util.ArrayList;
import java.util.List;

public class ArrayTest {

    private void detArray(int SIZE) {
        try {
            Array array = new Array(SIZE);
            List<SetterThread> threads = new ArrayList<>(SIZE);

            for (int i = 0; i < SIZE; i++) {
                threads.add(new SetterThread(array));
            }

            int n = SIZE;

            for (int i = 0; i < n; i++) {
                threads.get(i).start();
            }

            for (int i = 0; i < n; i++) {
                threads.get(i).join();
            }

            int sum = 0;
            for (int i = 0; i < n; i++) {
                sum += array.a[i];
            }

            //assert (sum == SIZE - 1) : " ***The assert did not pass, the sum is " + sum + " instead of " + (SIZE - 1);
            //assert (sum <= SIZE) : " ***The assert did not pass, the sum is " + sum + " instead of " + SIZE;

        } catch (InterruptedException e) {

        } catch (AssertionError e) {

        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(14400) // For input 5
    public void runDetArrayTest() {
        detArray(5);
    }
}
