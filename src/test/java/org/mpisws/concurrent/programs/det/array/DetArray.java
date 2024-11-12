package org.mpisws.concurrent.programs.det.array;

import java.util.ArrayList;
import java.util.List;

public class DetArray {

    public static void main(String[] args) {
        try {
            int SIZE = 2;
            Array array = new Array(SIZE);
            List<SetterThread> threads = new ArrayList<>(SIZE);

            for (int i = 0; i < SIZE; i++) {
                threads.add(new SetterThread(array));
            }

            int n = 2;

            for (int i = 0; i < n; i++) {
                threads.get(i).start();
            }

            for (int i = 0; i < n; i++) {
                threads.get(i).join();
            }

            int sum = 0;
            for (int i = 0; i < n; i++) {
                sum += array.a[i];
            }

            //assert (sum == SIZE - 1) : " ***The assert did not pass, the sum is " + sum + " instead of " + (SIZE - 1);
            //assert (sum <= SIZE) : " ***The assert did not pass, the sum is " + sum + " instead of " + SIZE;

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }
    }
}
