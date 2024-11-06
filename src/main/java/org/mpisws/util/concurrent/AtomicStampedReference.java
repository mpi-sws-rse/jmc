package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class AtomicStampedReference<V> {

  public int stamp;

  public V value;

  public ReentrantLock lock = new ReentrantLock();

  public AtomicStampedReference(V initialValue, int initialStamp) {
    RuntimeEnvironment.writeOperation(
        this,
        initialValue,
        Thread.currentThread(),
        "org/mpisws/util/concurrent/AtomicStampedReference",
        "value",
        "Ljava/lang/Object;");
    value = initialValue;
    RuntimeEnvironment.waitRequest(Thread.currentThread());

    RuntimeEnvironment.writeOperation(
        this,
        initialStamp,
        Thread.currentThread(),
        "org/mpisws/util/concurrent/AtomicStampedReference",
        "stamp",
        "I");
    stamp = initialStamp;
    RuntimeEnvironment.waitRequest(Thread.currentThread());
  }

  public boolean compareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp)
      throws JMCInterruptException {
    lock.lock();
    try {
      RuntimeEnvironment.readOperation(
          this,
          Thread.currentThread(),
          "org/mpisws/util/concurrent/AtomicStampedReference",
          "value",
          "Ljava/lang/Object;");
      V readValue = value;
      RuntimeEnvironment.waitRequest(Thread.currentThread());

      RuntimeEnvironment.readOperation(
          this,
          Thread.currentThread(),
          "org/mpisws/util/concurrent/AtomicStampedReference",
          "stamp",
          "I");
      int readStamp = stamp;

      if (readValue == expectedReference && readStamp == expectedStamp) {
        RuntimeEnvironment.waitRequest(Thread.currentThread());

        RuntimeEnvironment.writeOperation(
            this,
            newReference,
            Thread.currentThread(),
            "org/mpisws/util/concurrent/AtomicStampedReference",
            "value",
            "Ljava/lang/Object;");
        value = newReference;
        RuntimeEnvironment.waitRequest(Thread.currentThread());

        RuntimeEnvironment.writeOperation(
            this,
            newStamp,
            Thread.currentThread(),
            "org/mpisws/util/concurrent/AtomicStampedReference",
            "stamp",
            "I");
        stamp = newStamp;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return true;
      }
      RuntimeEnvironment.waitRequest(Thread.currentThread());
      return false;
    } finally {
      lock.unlock();
    }
  }

  public V getReference() {
    RuntimeEnvironment.readOperation(
        this,
        Thread.currentThread(),
        "org/mpisws/util/concurrent/AtomicStampedReference",
        "value",
        "Ljava/lang/Object;");
    V result = value;
    RuntimeEnvironment.waitRequest(Thread.currentThread());
    return result;
  }

  public int getStamp() {
    RuntimeEnvironment.readOperation(
        this,
        Thread.currentThread(),
        "org/mpisws/util/concurrent/AtomicStampedReference",
        "stamp",
        "I");
    int result = stamp;
    RuntimeEnvironment.waitRequest(Thread.currentThread());
    return result;
  }

  public void set(V newReference, int newStamp) throws JMCInterruptException {
    lock.lock();
    try {
      RuntimeEnvironment.writeOperation(
          this,
          newReference,
          Thread.currentThread(),
          "org/mpisws/util/concurrent/AtomicStampedReference",
          "value",
          "Ljava/lang/Object;");
      value = newReference;
      RuntimeEnvironment.waitRequest(Thread.currentThread());

      RuntimeEnvironment.writeOperation(
          this,
          newStamp,
          Thread.currentThread(),
          "org/mpisws/util/concurrent/AtomicStampedReference",
          "stamp",
          "I");
      stamp = newStamp;
      RuntimeEnvironment.waitRequest(Thread.currentThread());
    } finally {
      lock.unlock();
    }
  }

  public V get(int[] stampHolder) throws JMCInterruptException {
    lock.lock();
    try {
      RuntimeEnvironment.readOperation(
          this,
          Thread.currentThread(),
          "org/mpisws/util/concurrent/AtomicStampedReference",
          "value",
          "Ljava/lang/Object;");
      V result = value;
      RuntimeEnvironment.waitRequest(Thread.currentThread());

      RuntimeEnvironment.readOperation(
          this,
          Thread.currentThread(),
          "org/mpisws/util/concurrent/AtomicStampedReference",
          "stamp",
          "I");
      int resultStamp = stamp;
      RuntimeEnvironment.waitRequest(Thread.currentThread());

      stampHolder[0] = resultStamp;
      return result;
    } finally {
      lock.unlock();
    }
  }
}
