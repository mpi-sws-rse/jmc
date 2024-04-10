package org.example.concurrent.programs.simple.counter;

public class SimpleCounter extends Thread{

    private Counter counter;

    public SimpleCounter(Counter count) {
        this.counter = count;
    }
    @Override
    public void run() {
        counter.increment();
        HelpingThread helpingThread = new HelpingThread(counter);
        helpingThread.start();
    }

    public static void main(String[] args) {
        Counter counter = new Counter();
        SimpleCounter thread1 = new SimpleCounter(counter);
        SimpleCounter thread2 = new SimpleCounter(counter);
        thread1.start();
        thread2.start();
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assert counter.getValue() == 4 : "Counter value is not 4";
        System.out.println("Counter value is 4");
    }
}
