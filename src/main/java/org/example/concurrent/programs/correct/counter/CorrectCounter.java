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
        thread1.join();
        thread2.join();

        try {
            assert(counter.count == 2) : " ***The assert did not pass, the counter value is " + counter.count+"***";
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

        System.out.println("["+Thread.currentThread().getName()+" message] : If you see this message, the assert passed. The counter value is " + counter.count);

    }
}
