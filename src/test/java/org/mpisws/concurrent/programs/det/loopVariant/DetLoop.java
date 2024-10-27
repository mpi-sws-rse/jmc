package org.mpisws.concurrent.programs.det.loopVariant;


import org.mpisws.util.concurrent.ReentrantLock;

import java.util.ArrayList;

public class DetLoop {

    public static void main(String[] args) {
        int SIZE = 5;
        //int n = SIZE;
        int n = SIZE - 1;
        Numbers numbers = new Numbers(0, n);

        AssertThread assertThread1 = new AssertThread(numbers);
        assertThread1.start();

        ReentrantLock lock = new ReentrantLock();
        ArrayList<IncThread> threads = new ArrayList<>(SIZE);
        for (int i = 0; i < n; i++) {
            threads.add(new IncThread(lock, numbers));
        }

        for (int i = 0; i < n; i++) {
            threads.get(i).start();
        }
    }
}
