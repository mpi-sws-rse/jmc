package org.mpisws.concurrent.programs.det.loopVariant;


import org.mpisws.util.concurrent.ReentrantLock;

import java.util.ArrayList;

public class DetLoop {

    public static void main(String[] args) {
        int SIZE = 5;
        //int n = SIZE;
        int n = 4;
        Numbers numbers = new Numbers(0, n);

//        AssertThread assertThread1 = new AssertThread(numbers);
//        assertThread1.start();

        ReentrantLock lock = new ReentrantLock();
        ArrayList<IncThread> threads = new ArrayList<>(SIZE);
        threads.add(new IncThread(lock, numbers, 1));
        threads.add(new IncThread(lock, numbers, 1));
        threads.add(new IncThread(lock, numbers, 1));
        threads.add(new IncThread(lock, numbers, 1));


        for (int i = 0; i < n; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < n; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
