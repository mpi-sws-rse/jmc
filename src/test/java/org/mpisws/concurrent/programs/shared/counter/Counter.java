package org.mpisws.concurrent.programs.shared.counter;

public class Counter {

    public static int value;

    public static synchronized void increment() {
        value++;
    }
}
