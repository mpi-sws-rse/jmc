package org.example.concurrent.programs.dining;

import java.util.ArrayList;
import java.util.List;

public class DiningPhilosophers {

    public static void main(String[] args) {
        final int NUM_PHILOSOPHERS = 5;

        List<Philosopher> philosophers = new ArrayList<>(NUM_PHILOSOPHERS);
        List<Object> sticks = new ArrayList<>(NUM_PHILOSOPHERS);

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            sticks.add(new Object());
        }

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            Object leftFork = sticks.get(i);
            Object rightFork = sticks.get((i + 1) % NUM_PHILOSOPHERS);
            philosophers.add(new Philosopher(i, leftFork, rightFork));
        }

        for (Philosopher philosopher : philosophers) {
            philosopher.start();
        }

        for (Philosopher philosopher : philosophers) {
            try {
                philosopher.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
