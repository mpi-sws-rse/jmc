package org.mpi_sws.jmc.programs.complex.counter;

import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;

/**
 * This is a simple concurrent counter program with complex structure. The predicted verdict is that
 * the program is thread safe.
 */
public class ComplexCounter {

    public static void main(String[] args) {
        try {
            Dummy dummy = new Dummy();
            dummy.exe();
        } catch (InterruptedException | HaltExecutionException | HaltTaskException e) {
            System.out.println("JMCInterruptException thrown");
        }
    }
}
