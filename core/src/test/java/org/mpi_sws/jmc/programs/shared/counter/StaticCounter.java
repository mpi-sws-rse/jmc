package org.mpi_sws.jmc.programs.shared.counter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StaticCounter extends Thread {

    @Override
    public void run() {
        Counter.increment();
    }

    public static void main(String[] args) throws InterruptedException {
        StaticCounter c1 = new StaticCounter();
        StaticCounter c2 = new StaticCounter();
        c1.start();
        c2.start();
        c1.join();
        c2.join();
        try {
            assertEquals(2, Counter.value,
                    " ***The assert did not pass, the counter value is " + Counter.value + "***");
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

        System.out.println(
                "[Sync Counter message] : If you see this message, the assert passed. The counter"
                    + " value is "
                        + Counter.value);
    }
}
