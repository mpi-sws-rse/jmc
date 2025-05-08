package org.mpisws.jmc.programs.shared.counter;

public class Counter {

    public static int value;

    public static synchronized void increment() {
        value++;
    }
}
