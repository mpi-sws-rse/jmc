package org.mpisws.jmc.programs.dining;

import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;

import java.util.ArrayList;
import java.util.List;

/** Dining Philosophers problem with deadlock between philosophers over sharing sticks. */
public class DiningPhilosophers {

    public static void main(String[] args) {
        final int NUM_PHILOSOPHERS = 3;

        List<Philosopher> philosophers = new ArrayList<>(NUM_PHILOSOPHERS);
        List<JmcReentrantLock> sticks = new ArrayList<>(NUM_PHILOSOPHERS);

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            sticks.add(new JmcReentrantLock());
        }

        for (int i = 0; i < NUM_PHILOSOPHERS; i++) {
            JmcReentrantLock leftFork = sticks.get(i);
            JmcReentrantLock rightFork = sticks.get((i + 1) % NUM_PHILOSOPHERS);
            philosophers.add(new Philosopher(i, leftFork, rightFork));
        }

        for (Philosopher philosopher : philosophers) {
            philosopher.start();
        }

        for (Philosopher philosopher : philosophers) {
            try {
                philosopher.join1();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
