package org.mpisws.jmc.strategies;

/**
 * A ReplayableSchedulingStrategy is a scheduling strategy that can record and replay its execution
 * trace. This is useful for debugging and testing purposes, allowing the same sequence of events to
 * be replayed multiple times.
 *
 * <p>The storage and management of the recorded trace is implementation-specific.
 *
 * <p>
 */
public interface ReplayableSchedulingStrategy extends SchedulingStrategy {
    /** Records the current execution trace of the scheduling strategy. */
    void recordTrace();

    /** Replays the recorded execution trace of the scheduling strategy. */
    void replayRecordedTrace();
}
