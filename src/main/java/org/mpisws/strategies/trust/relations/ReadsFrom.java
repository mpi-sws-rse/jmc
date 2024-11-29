package org.mpisws.strategies.trust.relations;

import org.mpisws.strategies.trust.Event;
import org.mpisws.strategies.trust.ExecutionGraphAdjacency;

import java.text.MessageFormat;

public class ReadsFrom implements ExecutionGraphAdjacency {

    private final Event writeEvent;
    private final Event readEvent;

    public ReadsFrom(Event writeEvent, Event readEvent) {
        this.writeEvent = writeEvent;
        this.readEvent = readEvent;
    }

    @Override
    public String key() {
        return MessageFormat.format(
                "Read({0}, {1}) -> Write({2},{3})",
                readEvent.getLocation(),
                readEvent.getTaskId(),
                writeEvent.getLocation(),
                writeEvent.getTaskId());
    }
}
