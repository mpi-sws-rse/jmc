package org.mpisws.concurrent.programs.complex.counter;

import org.mpisws.util.concurrent.JMCInterruptException;

/**
 * This is a simple concurrent counter program with complex structure. The predicted verdict is that
 * the program is thread safe.
 */
public class ComplexCounter {

    public static void main(String[] args) {
        try {
            Dummy dummy = new Dummy();
            dummy.exe();
        } catch (JMCInterruptException | InterruptedException e) {
            System.out.println("JMCInterruptException thrown");
        }
    }
}
