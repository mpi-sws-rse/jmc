package org.mpisws.concurrent.programs.wrong.counter;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

/**
 * This is simple concurrent counter program that demonstrates a deadlock between two threads over
 * using two shared locks.
 */
public class BuggyCounter extends Thread {
  Counter counter1;
  Counter counter2;
  ReentrantLock lock1;
  ReentrantLock lock2;

  public BuggyCounter(
      Counter counter1, Counter counter2, ReentrantLock lock1, ReentrantLock lock2) {
    this.counter1 = counter1;
    this.counter2 = counter2;
    this.lock1 = lock1;
    this.lock2 = lock2;
  }

  @Override
  public void run() {
    try {
      lock1.lock();
      this.counter1.count++;
      lock2.lock();
      this.counter2.count++;
      lock2.unlock();
      lock1.unlock();
    } catch (JMCInterruptException e) {
      System.out.println(
          "[" + Thread.currentThread().getName() + " message] : The thread is interrupted");
    }
  }

  public static void main(String[] args) throws InterruptedException {
    Counter counter1 = new Counter();
    Counter counter2 = new Counter();
    ReentrantLock lock1 = new ReentrantLock();
    ReentrantLock lock2 = new ReentrantLock();

    BuggyCounter thread1 = new BuggyCounter(counter1, counter2, lock1, lock2);
    BuggyCounter thread2 = new BuggyCounter(counter2, counter1, lock2, lock1);

    thread1.start();
    thread2.start();

    thread1.join();
    thread2.join();

    System.out.println(
        "["
            + Thread.currentThread().getName()
            + " message] : The counter1 value is "
            + counter1.count
            + " and the counter2 value is "
            + counter2.count);
  }
}
