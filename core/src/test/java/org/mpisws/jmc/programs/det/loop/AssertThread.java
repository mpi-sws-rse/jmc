package org.mpisws.jmc.programs.det.loop;

public class AssertThread extends Thread {

    Numbers numbers;

    public AssertThread(Numbers numbers) {
        this.numbers = numbers;
    }

    @Override
    public void run() {
        // assert (numbers.x < numbers.n) : "numbers.x >= n";
        assert (numbers.x <= numbers.n) : "numbers.x > n";
    }
}
