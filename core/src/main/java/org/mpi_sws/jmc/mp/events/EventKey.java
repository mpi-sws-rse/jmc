package org.mpi_sws.jmc.mp.events;

/**
 * The identity of an event in a Must execution graph: the pair {@code ⟨t, i⟩} of thread id and
 * index within the thread (Definition 3.1 of the Must paper). Program order {@code po} is the
 * order of {@code idx} within one {@code tid}.
 *
 * <p>Not to be confused with {@link Envelope#sendIndex()}, which counts only the <em>sends</em>
 * of a task and is what identifies a message in a backend's delivery answer.
 *
 * @param tid the thread (task) id
 * @param idx the event's 0-based index among all events of that thread
 */
public record EventKey(long tid, long idx) implements Comparable<EventKey> {

    @Override
    public int compareTo(EventKey other) {
        int c = Long.compare(tid, other.tid);
        return c != 0 ? c : Long.compare(idx, other.idx);
    }
}
