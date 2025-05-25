package org.mpisws.jmc.programs.correct.counter;

import org.mpisws.jmc.runtime.JmcRuntimeUtils;

public class Counter {
    private int count = 0;

    public Counter() {
        this.count = 0;
        JmcRuntimeUtils.writeEvent(
                0, "org/mpisws/jmc/programs/correct/counter/Counter", "count", "I", this);
    }

    public int get() {
        int out = count;
        JmcRuntimeUtils.readEvent(
                "org/mpisws/jmc/programs/correct/counter/Counter", "count", "I", this);
        return out;
    }

    public void set(int value) {
        count = value;
        JmcRuntimeUtils.writeEvent(
                value, "org/mpisws/jmc/programs/correct/counter/Counter", "count", "I", this);
    }
}
