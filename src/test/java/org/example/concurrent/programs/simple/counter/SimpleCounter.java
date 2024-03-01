package org.example.concurrent.programs.simple.counter;

public class SimpleCounter extends Thread{
    private int count;
    public SimpleCounter(int count) {
        this.count = count;
    }

    public void run() {
        synchronized (this) {
            this.count = this.count + 1;
            System.out.println("[" + this.getName() + " message] : " + "The counter value is " + this.count);
        }
    }

    public static void main(String[] args) {
        SimpleCounter thread1 = new SimpleCounter(0);
        SimpleCounter thread2 = new SimpleCounter(0);
        thread1.start();
        thread2.start();
    }
}
