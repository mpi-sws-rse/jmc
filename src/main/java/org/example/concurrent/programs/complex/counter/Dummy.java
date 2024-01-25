package org.example.concurrent.programs.complex.counter;

public class Dummy {

    public void exe(){
        Counter counter = new Counter();
        CounterThread thread1 = new CounterThread(counter);
        thread1.setName("Thread 1");
        CounterThread thread2 = new CounterThread(counter);
        thread2.setName("Thread 2");
        thread1.exe();
        thread2.exe();
    }
}
