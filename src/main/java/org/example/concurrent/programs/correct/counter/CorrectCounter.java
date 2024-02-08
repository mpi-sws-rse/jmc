package org.example.concurrent.programs.correct.counter;

public class CorrectCounter extends Thread{
    Counter counter;
    public CorrectCounter(Counter counter) {
        this.counter = counter;
    }
    @Override
    public void run() {
        counter.count = counter.count + 1;
        System.out.println("[" + this.getName() + " message] : " + "The counter value is " + counter.count);
    }
    public static void main(String[] args) {
        Counter counter = new Counter();
        CorrectCounter thread1 = new CorrectCounter(counter);
        CorrectCounter thread2 = new CorrectCounter(counter);
        thread1.start();
        thread2.start();
    }
}
