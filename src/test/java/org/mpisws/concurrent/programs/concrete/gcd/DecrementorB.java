package org.mpisws.concurrent.programs.concrete.gcd;

public class DecrementorB extends Thread {

  Object lock;
  public Numbers n;

  public DecrementorB(Numbers n, Object lock) {
    this.n = n;
    this.lock = lock;
  }

  @Override
  public void run() {
    while (n.a != n.b) {
      // synchronized (lock) {
      if (n.b > n.a) {
        n.b = n.b - n.a;
      }
      // }
    }
  }
}
