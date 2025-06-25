package org.mpisws.jmc.test.atomic.counter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounter {

    public static void main(String[] args) {
        int length = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        AtomicInteger counter = new AtomicInteger();
        List<Adder> adders = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Adder adder = new Adder(counter);
            adders.add(adder);
        }

        for (Adder adder : adders) {
            adder.start();
        }

        for (Adder adder : adders) {
            try {
                adder.join();
            } catch (InterruptedException e) {
            }
        }
    }
}
