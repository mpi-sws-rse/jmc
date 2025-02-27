package org.mpisws.jmc.programs.det.loop;

import java.util.ArrayList;
import java.util.List;

public class DetLoop {

    public static void main(String[] args) {
        int SIZE = 5;
        int n = SIZE - 1;
        Numbers numbers = new Numbers(0, n);

        //        AssertThread assertThread1 = new AssertThread(numbers);
        //        assertThread1.start();

        List<IncThread> threads = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            threads.add(new IncThread(numbers));
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
