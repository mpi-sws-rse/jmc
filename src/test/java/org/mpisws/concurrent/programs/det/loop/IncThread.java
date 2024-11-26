package org.mpisws.concurrent.programs.det.loop;

import org.mpisws.util.concurrent.JmcThread;

public class IncThread extends JmcThread {

    Numbers numbers;

    public IncThread(Numbers numbers) {
        super();
        this.numbers = numbers;
    }

    @Override
    public void run1() {
        int t;
        t = numbers.x;
        numbers.x = t + 1;
    }
}
