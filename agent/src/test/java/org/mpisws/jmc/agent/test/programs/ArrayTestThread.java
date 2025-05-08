package org.mpisws.jmc.agent.test.programs;

public class ArrayTestThread extends Thread {
    @Override
    public void run() {
        System.out.println("Thread is running");
        System.out.println("Thread has finished running");
    }

    public static void main(String[] args) {
        ArrayTestThread[] threads = new ArrayTestThread[2];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new ArrayTestThread();
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }
        try {
            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Main method has finished running");
    }
}
