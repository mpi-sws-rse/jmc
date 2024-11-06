package org.mpisws.concurrent.programs.complex.counter;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class CounterThread extends Thread {
  Counter counter;
  ReentrantLock lock;

  public CounterThread(Counter counter, ReentrantLock lock) {
    this.counter = counter;
    this.lock = lock;
  }

  @Override
  public void run() {
    try {
      lock.lock();
      counter.count = counter.count + 1;
      lock.unlock();
    } catch (JMCInterruptException e) {
      System.out.println("[" + this.getName() + " message] : " + "The thread was interrupted.");
    }
  }

  public void exe() {
    this.start();
  }
}
