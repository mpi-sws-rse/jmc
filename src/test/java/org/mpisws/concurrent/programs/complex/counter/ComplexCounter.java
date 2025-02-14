package org.mpisws.concurrent.programs.complex.counter;

import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.HaltTaskException;

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
