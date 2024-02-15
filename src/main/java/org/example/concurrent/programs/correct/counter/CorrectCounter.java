package org.example.concurrent.programs.correct.counter;

public class CorrectCounter extends Thread{
    Counter counter;
    public CorrectCounter(Counter counter) {
        this.counter = counter;
    }
    @Override
    public void run() {
        synchronized (counter){
            counter.count = counter.count + 1;
            System.out.println("[" + this.getName() + " message] : " + "The counter value is " + counter.count);
        }
    }
    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        CorrectCounter thread1 = new CorrectCounter(counter);
        CorrectCounter thread2 = new CorrectCounter(counter);
        thread1.start();
        thread2.start();
        // new
        thread1.join();
        // new
        thread2.join();
        // new : The assert should pass if synchronized, fail if not
        assert(counter.count == 2) : "["+Thread.currentThread().getName()+" message] : The assert did not pass, the counter value is " + counter.count;
    }
}
