package org.mpisws.concurrent.programs.dining;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class Philosopher extends Thread {
    private final int id;
    private final ReentrantLock leftStick;
    private final ReentrantLock rightStick;

    public Philosopher(int id, ReentrantLock leftFork, ReentrantLock rightFork) {
        this.id = id;
        this.leftStick = leftFork;
        this.rightStick = rightFork;
    }

    private void think() {
        System.out.println("Philosopher " + id + " is thinking.");
        // Thread.sleep(1000);
    }

    private void tryToEat() throws JMCInterruptException {
        synchronized (rightStick) {
            synchronized (leftStick) {
                eat();
            }
            System.out.println("Philosopher " + id + " has put down the left stick.");
        }
        System.out.println("Philosopher " + id + " has put down the right stick.");
    }

    private void eat() {
        System.out.println("Philosopher " + id + " is eating.");
        // Thread.sleep(1000);
    }

    @Override
    public void run() {
        try {
            think();
            tryToEat();
        } catch (JMCInterruptException e) {

        }
    }
}
