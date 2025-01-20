package org.mpisws.concurrent.programs.nondet.counter.coarse;

import java.util.ArrayList;

public class CCounter {

    int c1 = 0;
    int c2 = 0;

    public CCounter() {
    }

    public void inc1() {
        c1 = c1 + 1;
    }

    public void inc2() {
        c2 = c2 + 1;
    }

    public void dec1() {
        c1 = c1 - 1;
    }

    public void dec2() {
        c2 = c2 - 1;
    }
}
