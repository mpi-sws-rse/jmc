package org.mpi_sws.jmc.programs.det.loopVariant;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssertThread extends Thread {

    Numbers numbers;

    public AssertThread(Numbers numbers) {
        this.numbers = numbers;
    }

    @Override
    public void run() {
        // assert (numbers.x < numbers.n) : "x >= n";
        assertTrue(numbers.x <= numbers.n, "x >= n - 1");
    }
}
