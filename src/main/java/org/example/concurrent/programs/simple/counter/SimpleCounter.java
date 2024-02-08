package org.example.concurrent.programs.simple.counter;

public class SimpleCounter extends Thread{
    private int count;
    public SimpleCounter(int count) {
        this.count = count;
    }

    public void run() {
            this.count = this.count + 1;
            System.out.println("["+this.getName()+" message] : "+"The counter value is "+this.count);
    }

    public static void main(String[] args) {
        Object lock = new Object();
        SimpleCounter thread1 = new SimpleCounter(0);
        SimpleCounter thread2 = new SimpleCounter(0);
        thread1.start();
        thread2.start();
        synchronized (lock){
            System.out.println("["+Thread.currentThread().getName()+" message] : "+"Entered the synchronized block");
        }
    }
}
