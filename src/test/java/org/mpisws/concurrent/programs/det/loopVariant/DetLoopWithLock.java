package org.mpisws.concurrent.programs.det.loopVariant;

import org.mpisws.util.concurrent.ReentrantLock;

import java.util.ArrayList;

public class DetLoopWithLock {

    public static void main(String[] args) {
        int SIZE = 2;
        // int n = SIZE;
        int n = 2;
        Numbers numbers = new Numbers(0, n);

        //        AssertThread assertThread1 = new AssertThread(numbers);
        //        assertThread1.start();

        ReentrantLock lock = new ReentrantLock();
        ArrayList<IncThread> threads = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            threads.add(new IncThread(lock, numbers, i + 1));
        }

        for (int i = 0; i < n; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < n; i++) {
            try {
                threads.get(i).join1();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
