package org.mpisws.jmc.programs.correct.counter

import org.mpisws.jmc.util.concurrent.JmcReentrantLock;
import org.mpisws.jmc.util.concurrent.JmcThread

class CorrectCounterTest(val lock: JmcReentrantLock, val counter: CounterTest): JmcThread(){
    override fun run1() {
        lock.lock();
        counter.count += 1;
        lock.unlock();
    }
}

class CounterTest(var count: Int);

fun main() {
    val counter = CounterTest(0);
    val lock = JmcReentrantLock();

    val thread1 = CorrectCounterTest(lock, counter);
    val thread2 = CorrectCounterTest(lock, counter);
    val thread3 = CorrectCounterTest(lock, counter);

    thread1.start();
    thread2.start();
    thread3.start();

    try {
        thread1.join1();
        thread2.join1();
        thread3.join1();
        assert(counter.count == 3);
    } catch (e: InterruptedException) {
        e.printStackTrace();
    }
}