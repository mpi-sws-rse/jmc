package org.mpisws.concurrent.programs.det.loopVariant;

public class AssertThread extends Thread {

    Numbers numbers;

    public AssertThread(Numbers numbers) {
        this.numbers = numbers;
    }

    @Override
    public void run() {
        // assert (numbers.x < numbers.n) : "x >= n";
        assert (numbers.x <= numbers.n) : "x >= n - 1";
    }
}
