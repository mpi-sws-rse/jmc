package org.mpi_sws.jmc.mp.consistency;

import java.util.Map;
import java.util.TreeMap;

/**
 * An immutable vector clock: one non-negative counter per task id, absent components read as
 * {@code 0}.
 *
 * <p>Together with the discipline in {@link TaskClocks}, the strict order {@link #lessThan}
 * coincides with the Must {@code porf} relation on sends:
 * {@code VC(s') < VC(s)} if and only if {@code s'} is {@code porf}-before {@code s}. That
 * is what the causal-delivery rule ({@code so ≜ [S^cd]; porf; [S^cd]}, Definition 3.6) is
 * evaluated with.
 *
 * <p>Components are kept in a sorted map so that {@link #toString()} and iteration order are
 * deterministic. <b>Invariant:</b> a stored component is always {@code >= 1} — {@link #tick} only
 * adds and {@link #join} only takes maxima of stored values — so an absent component and a zero
 * component are the same thing, and map equality is clock equality.
 */
public final class VectorClock {

    /** The clock with every component at {@code 0}. */
    public static final VectorClock EMPTY = new VectorClock(new TreeMap<>());

    private final TreeMap<Long, Long> ticks;

    private VectorClock(TreeMap<Long, Long> ticks) {
        this.ticks = ticks;
    }

    /**
     * Returns the component for a task.
     *
     * @param tid the task id
     * @return its counter, {@code 0} if the task has never ticked
     */
    public long get(long tid) {
        return ticks.getOrDefault(tid, 0L);
    }

    /**
     * Returns a copy with the given task's component incremented by one.
     *
     * @param tid the task id
     * @return the advanced clock
     */
    public VectorClock tick(long tid) {
        TreeMap<Long, Long> copy = new TreeMap<>(ticks);
        copy.merge(tid, 1L, Long::sum);
        return new VectorClock(copy);
    }

    /**
     * Returns the component-wise maximum of this clock and another — the join {@code ⊔}.
     *
     * @param other the other clock
     * @return the join
     */
    public VectorClock join(VectorClock other) {
        TreeMap<Long, Long> copy = new TreeMap<>(ticks);
        other.ticks.forEach((tid, value) -> copy.merge(tid, value, Math::max));
        return new VectorClock(copy);
    }

    /**
     * Whether this clock is component-wise {@code <=} the other.
     *
     * @param other the other clock
     * @return {@code true} iff every component of this clock is at most the other's
     */
    public boolean leq(VectorClock other) {
        for (Map.Entry<Long, Long> entry : ticks.entrySet()) {
            if (entry.getValue() > other.get(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this clock is strictly below the other: {@link #leq} and not equal.
     *
     * @param other the other clock
     * @return {@code true} iff {@code this <= other} and {@code this != other}
     */
    public boolean lessThan(VectorClock other) {
        return leq(other) && !equals(other);
    }

    /**
     * Whether neither clock is below the other.
     *
     * @param other the other clock
     * @return {@code true} iff the two are incomparable
     */
    public boolean concurrentWith(VectorClock other) {
        return !leq(other) && !other.leq(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VectorClock other && ticks.equals(other.ticks);
    }

    @Override
    public int hashCode() {
        return ticks.hashCode();
    }

    @Override
    public String toString() {
        return ticks.toString();
    }
}
