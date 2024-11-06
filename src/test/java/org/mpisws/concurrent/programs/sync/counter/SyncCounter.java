package org.mpisws.concurrent.programs.sync.counter;

public class SyncCounter extends Thread {

  public Counter counter;

  public SyncCounter(Counter counter) {
    this.counter = counter;
  }

  @Override
  public void run() {
    counter.inc();
  }

  public static void main(String[] args) throws InterruptedException {
    Counter counter = new Counter();
    SyncCounter thread1 = new SyncCounter(counter);
    SyncCounter thread2 = new SyncCounter(counter);
    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();
    try {
      assert (counter.count == 2)
          : " ***The assert did not pass, the counter value is " + counter.count + "***";
    } catch (AssertionError e) {
      System.out.println(e.getMessage());
    }

    System.out.println(
        "[Sync Counter message] : If you see this message, the assert passed. The counter value is "
            + counter.count);
  }
}
