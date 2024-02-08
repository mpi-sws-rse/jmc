package org.example.concurrent.programs.wrong.counter;

public class BuggyCounter extends Thread{
    Counter counter;
    public BuggyCounter(Counter counter) {
        this.counter = counter;
    }
    @Override
    public void run() {
        counter.count = counter.count + 1;
        System.out.println("["+this.getName()+" message] : "+"The counter value is "+counter.count);
    }
    public static void main(String[] args) {
        Counter counter = new Counter();
        BuggyCounter thread1 = new BuggyCounter(counter);
        BuggyCounter thread2 = new BuggyCounter(counter);
        thread1.start();
        thread2.start();
    }
}
