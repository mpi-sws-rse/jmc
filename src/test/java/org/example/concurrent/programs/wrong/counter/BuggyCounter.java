package org.example.concurrent.programs.wrong.counter;

public class BuggyCounter extends Thread{
    Counter counter1;
    Counter counter2;

    public BuggyCounter(Counter counter1, Counter counter2) {
        this.counter1 = counter1;
        this.counter2 = counter2;
    }
    @Override
    public void run() {
        synchronized (this.counter1){
            this.counter1.count++;
            System.out.println("[" + this.getName() + " message] : " + "The counter1 value is " + counter1.count);
            synchronized (this.counter2){
                this.counter2.count++;
                System.out.println("[" + this.getName() + " message] : " + "The counter2 value is " + counter1.count);
            }
        }
    }
    public static void main(String[] args) {
        Counter counter1 = new Counter();
        Counter counter2 = new Counter();
        BuggyCounter thread1 = new BuggyCounter(counter1, counter2);
        BuggyCounter thread2 = new BuggyCounter(counter2, counter1);
        thread1.start();
        thread2.start();
    }
}
