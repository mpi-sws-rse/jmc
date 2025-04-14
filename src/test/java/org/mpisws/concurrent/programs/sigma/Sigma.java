package org.mpisws.concurrent.programs.sigma;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;

public class Sigma {

    public static void main(String[] args) {
        int SIZE = 16;
        Shared shared = new Shared(SIZE);
        ArrayList<T> threads = new ArrayList<>(SIZE);

        for (int i = 0; i < SIZE; i++) {
            T thread = new T(shared);
            threads.add(thread);
        }

        for (int i = 0; i < SIZE; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < SIZE; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                // Handle the exception
            }
        }
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += shared.array[i];
        }

        try {
            Utils.assertion(sum == SIZE, "Sum is not equal to SIZE");
        } catch (JMCInterruptException e) {

        }
    }
}
