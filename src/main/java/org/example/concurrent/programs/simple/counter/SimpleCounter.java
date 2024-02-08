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
        Object lock2 = new Object();
        Object lock3 = new Object();
        Object lock4 = new Object();
        SimpleCounter thread1 = new SimpleCounter(0);
        SimpleCounter thread2 = new SimpleCounter(0);
        thread1.start();
        thread2.start();
        synchronized (lock){
            synchronized (lock2){
                System.out.println("["+Thread.currentThread().getName()+" message] : "+"Entered the synchronized block");
            }
            System.out.println("["+Thread.currentThread().getName()+" message] : "+"Entered the synchronized block");
            synchronized (lock3){
                synchronized (lock4){
                    System.out.println("["+Thread.currentThread().getName()+" message] : "+"Entered the synchronized block");
                }
                System.out.println("["+Thread.currentThread().getName()+" message] : "+"Entered the synchronized block");
            }
        }
        synchronized (lock2){
            System.out.println("["+Thread.currentThread().getName()+" message] : "+"Entered the synchronized block");
        }
    }
}
