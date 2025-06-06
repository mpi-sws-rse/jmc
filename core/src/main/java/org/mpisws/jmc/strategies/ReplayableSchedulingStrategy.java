package org.mpisws.jmc.strategies;

/**
 * A ReplayableSchedulingStrategy is a scheduling strategy that can record and replay its execution
 * trace. This is useful for debugging and testing purposes, allowing the same sequence of events to
 * be replayed multiple times.
 *
 * <p>The storage and management of the recorded trace is implementation-specific.
 *
 * <p>recordTrace() will be called by the model checker whenever a AssertionError is thrown during
 * the execution of the scheduling strategy. This allows the strategy to capture the current state
 * of the scheduling decisions made up to that point, which can then be replayed to reproduce the
 * error or analyze the behavior of the system.
 *
 * <p>replayRecordedTrace() will be called to replay the recorded trace. The checker will call this
 * method before initIteration() and allows the strategy to fetch the recorded trace and setup to
 * allow replaying in the subsequent iteration.
 */
public interface ReplayableSchedulingStrategy extends SchedulingStrategy {
    /** Records the current execution trace of the scheduling strategy. */
    void recordTrace();

    /** Replays the recorded execution trace of the scheduling strategy. */
    void replayRecordedTrace();
}
