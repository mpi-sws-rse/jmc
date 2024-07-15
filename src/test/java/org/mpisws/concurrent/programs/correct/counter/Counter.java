package org.mpisws.concurrent.programs.correct.counter;

import java.util.List;
import java.util.Map;

public class Counter {
    public int count = 0;

    public synchronized int test(int x, double y, double[][] n, long z, Map<Object, List<Long>> j) throws Exception {
        System.out.println("This is a test method");
        x++;
        if (x == 1) {
            x++;
            return x;
        } else {
            x--;
            return x;
        }
    }
}
