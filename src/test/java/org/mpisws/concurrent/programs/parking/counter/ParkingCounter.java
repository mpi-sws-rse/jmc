package org.mpisws.concurrent.programs.parking.counter;

import java.util.concurrent.locks.LockSupport;

public class ParkingCounter extends Thread {

  private final Counter counter;

  public ParkingCounter(Counter counter) {
    this.counter = counter;
  }

  @Override
  public void run() {
    counter.inc();
    LockSupport.park();
  }

  public static void main(String[] args) {
    Counter counter = new Counter();
    ParkingCounter parkingCounter1 = new ParkingCounter(counter);
    ParkingCounter parkingCounter2 = new ParkingCounter(counter);

    parkingCounter1.start();
    parkingCounter2.start();

    LockSupport.unpark(parkingCounter1);
    LockSupport.unpark(parkingCounter2);

    try {
      parkingCounter1.join();
      parkingCounter2.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    System.out.println("The counter value is: " + counter.getValue());
    assert (counter.getValue() == 2) : " ***The assert did not pass***";
    System.out.println("If you see this message, the test passed!");
  }
}
