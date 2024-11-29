package org.mpisws.strategies.trust.relations;

import org.mpisws.strategies.trust.Event;
import org.mpisws.strategies.trust.ExecutionGraphAdjacency;

public class Coherency implements ExecutionGraphAdjacency {
    private final Event write1;
    private final Event write2;

    public Coherency(Event write1, Event write2) {
        this.write1 = write1;
        this.write2 = write2;
    }

    public Event getWrite1() {
        return write1;
    }

    public Event getWrite2() {
        return write2;
    }

    public String key() {
        return "Coherency(" + write1.getTaskId() + ", " + write2.getTaskId() + ")";
    }
}
