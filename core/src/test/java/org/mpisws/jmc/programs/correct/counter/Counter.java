package org.mpisws.jmc.programs.correct.counter;

import org.mpisws.jmc.runtime.RuntimeUtils;

public class Counter {
    private int count = 0;

    public Counter() {
        this.count = 0;
        RuntimeUtils.writeEvent(
                this, 0, "org/mpisws/jmc/programs/correct/counter/Counter", "count", "I");
    }

    public int get() {
        int out = count;
        RuntimeUtils.readEvent(
                this, "org/mpisws/jmc/programs/correct/counter/Counter", "count", "I");
        return count;
    }

    public void set(int value) {
        count = value;
        RuntimeUtils.writeEvent(
                this, value, "org/mpisws/jmc/programs/correct/counter/Counter", "count", "I");
    }
}
