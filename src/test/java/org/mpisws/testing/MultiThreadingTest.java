package org.mpisws.testing;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

public class MultiThreadingTest {
    @Test
    public void testMultiThreadingBlocking() {
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                int counter = 0;
                for (int j = 0; j < 10; j++) {
                    counter += 1;
                }
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
            }
        }
    }

    @Test
    public void testMultiThreadingNonBlocking() {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                int counter = 0;
                for (int j = 0; j < 10; j++) {
                    counter += 1;
                }
                while (counter != 0) {
                }
            });
            threads.add(t);
            t.start();
        }
    }
}
