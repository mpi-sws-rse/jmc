package org.mpi_sws.jmc.util;

/**
 * A variable-length Lamport vector clock.
 *
 * <p>Vector clocks are the mechanism the Trust model-checking strategy uses to answer
 * happens-before ("porf") queries in O(size) time (see {@code ExecutionGraphNode} in the {@code
 * strategies.trust} package). Each component of the vector corresponds to one task, and holds the
 * number of that task's events known to causally precede the event owning the clock. The clock is
 * <em>variable length</em> because tasks are discovered incrementally: it is grown on demand (see
 * {@link #grow(LamportVectorClock)}) so that clocks belonging to events from different numbers of
 * tasks can still be compared and merged.
 *
 * <p>The class implements {@link PartialOrder}: two clocks are ordered by component-wise comparison
 * and are {@code UNRELATED} when neither dominates the other (i.e. the two events are concurrent).
 */
public class LamportVectorClock implements PartialOrder<LamportVectorClock> {

    /** The per-task counters; {@code vector[i]} is the known count of task {@code i}'s events. */
    private int[] vector;

    /**
     * Creates a new Lamport vector clock with the given size.
     *
     * @param size The size of the vector clock.
     */
    public LamportVectorClock(int size) {
        this.vector = new int[size];
        for (int i = 0; i < size; i++) {
            vector[i] = 0;
        }
    }

    /**
     * Creates a new Lamport vector clock with the given vector.
     *
     * @param vector The vector clock.
     */
    public LamportVectorClock(int[] vector) {
        this.vector = new int[vector.length];
        System.arraycopy(vector, 0, this.vector, 0, vector.length);
    }

    /**
     * Creates a new clock by copying {@code other} and incrementing the component at {@code index}
     * by one.
     *
     * <p>This is how a fresh event's clock is derived from its program-order predecessor: the new
     * event inherits the predecessor's clock and bumps its own task's component. If {@code index}
     * lies beyond {@code other}'s current length (a newly seen task), both this clock and {@code
     * other} are grown to {@code index + 1} components, padding with zeros.
     *
     * @param other The clock to copy from (may be grown as a side effect).
     * @param index The index of the component to increment (typically the owning task's id).
     * @throws IllegalArgumentException If {@code index} is negative.
     */
    public LamportVectorClock(LamportVectorClock other, int index) {
        if (index >= other.vector.length) {
            this.vector = new int[index + 1];
            System.arraycopy(other.vector, 0, this.vector, 0, other.vector.length);
            other.vector = new int[index + 1];
            System.arraycopy(this.vector, 0, other.vector, 0, other.vector.length);
            for (int i = other.vector.length; i < index + 1; i++) {
                this.vector[i] = 0;
                other.vector[i] = 0;
            }
        } else if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        } else {
            this.vector = new int[other.vector.length];
            System.arraycopy(other.vector, 0, this.vector, 0, other.vector.length);
        }
        this.vector[index] = other.vector[index] + 1;
    }

    /**
     * Returns the vector clock.
     *
     * @return The vector clock.
     */
    public int[] getVector() {
        return vector;
    }

    /**
     * Returns the size of the vector clock.
     *
     * @return The size of the vector clock.
     */
    public int getSize() {
        return vector.length;
    }

    /**
     * Grows the shorter of this clock and {@code other} (in place) so both have the same number of
     * components, zero-padding the newly added ones.
     *
     * <p>Used before comparing or merging two clocks so that missing (unseen-task) components are
     * treated as zero.
     *
     * @param other The other clock, which may be grown as a side effect.
     * @return The common length of the two clocks after growing.
     */
    private int grow(LamportVectorClock other) {
        // Can't copy values. Need to initialize zeros here.
        if (other.vector.length > vector.length) {
            int[] newVector = new int[other.vector.length];
            System.arraycopy(this.vector, 0, newVector, 0, this.vector.length);
            this.vector = newVector;
            return other.vector.length;
        } else if (vector.length > other.vector.length) {
            int[] newVector = new int[vector.length];
            System.arraycopy(other.vector, 0, newVector, 0, other.vector.length);
            other.vector = newVector;
            return this.vector.length;
        }
        return this.vector.length;
    }

    /**
     * Merges {@code other} into this clock by taking the component-wise maximum.
     *
     * <p>This is the join used when an event acquires a new causal predecessor (e.g. a reads-from
     * edge): the event's clock must dominate every predecessor's clock. Both clocks are grown to a
     * common length first.
     *
     * @param other The other vector clock to merge in.
     */
    public void update(LamportVectorClock other) {
        grow(other);
        if (this.vector.length != other.vector.length) {
            throw new RuntimeException("Vector sizes do not match");
        }
        for (int i = 0; i < vector.length; i++) {
            this.vector[i] = Math.max(this.vector[i], other.vector[i]);
        }
    }

    /**
     * Checks if this vector clock happens before the other vector clock. (less than or equal to)
     *
     * @param other The other vector clock.
     * @return True if this vector clock happened before the other vector clock, false otherwise.
     */
    public boolean happensBefore(LamportVectorClock other) {
        boolean happenedBefore = false;
        boolean happenedAfter = false;
        int size = this.vector.length;
        if (vector.length != other.vector.length) {
            size = grow(other);
        }
        for (int i = 0; i < size; i++) {
            if (vector[i] <= other.vector[i]) {
                happenedBefore = true;
            } else if (vector[i] > other.vector[i]) {
                happenedAfter = true;
            }
        }
        return happenedBefore && !happenedAfter;
    }

    /**
     * Checks whether this clock equals {@code other} component-wise.
     *
     * <p>Note this is a type-specific overload (it takes a {@code LamportVectorClock}, not {@code
     * Object}), so it does not override {@link Object#equals(Object)}. Two clocks of different
     * lengths are never equal.
     *
     * @param other The other vector clock.
     * @return True if the two clocks have identical components, false otherwise.
     */
    public boolean equals(LamportVectorClock other) {
        if (vector.length != other.vector.length) {
            return false;
        }
        for (int i = 0; i < vector.length; i++) {
            if (vector[i] != other.vector[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the maximum value in the vector clock.
     *
     * @return The maximum value in the vector clock.
     */
    public int max() {
        int max = 0;
        for (Integer integer : vector) {
            if (integer > max) {
                max = integer;
            }
        }
        return max;
    }

    /**
     * Compares this clock with {@code other} in the happens-before partial order.
     *
     * @param other The other vector clock.
     * @return {@code LT} if this happens before {@code other}, {@code GT} if {@code other} happens
     *     before this, {@code EQ} if they are equal, and {@code UNRELATED} if the two events are
     *     concurrent.
     */
    @Override
    public Relation compare(LamportVectorClock other) {
        if (this.happensBefore(other)) {
            return Relation.LT;
        } else if (other.happensBefore(this)) {
            return Relation.GT;
        } else if (this.equals(other)) {
            return Relation.EQ;
        } else {
            return Relation.UNRELATED;
        }
    }

    /**
     * A single component (one task's counter) of a {@link LamportVectorClock}, viewed as a {@link
     * TotalOrder}.
     *
     * <p>Whereas whole clocks form only a partial order, the counters at a <em>fixed</em> index are
     * totally ordered. Wrapping one index as a {@code Component} lets callers compare two events'
     * progress along a single task using the generic {@link TotalOrder} contract.
     */
    public static class Component implements TotalOrder<Component> {

        /** The index into the backing clock that this component refers to. */
        private final int index;
        /** The clock this component reads its counter from. */
        private final LamportVectorClock clock;

        /**
         * Constructs a new {@link Component} with the given index and vector clock.
         *
         * @param index The index of the component.
         * @param clock The vector clock.
         */
        public Component(int index, LamportVectorClock clock) {
            this.index = index;
            this.clock = clock;
        }

        /**
         * Compares this component with {@code other}, which must refer to the same index.
         *
         * @param other The other component (must share this component's index).
         * @return {@code LT}, {@code GT}, or {@code EQ} according to the two counters.
         * @throws InvalidComparisonException If the two components refer to different indices.
         */
        @Override
        public Relation compare(Component other) throws InvalidComparisonException {
            if (this.index != other.index) {
                throw new InvalidComparisonException(
                        "Cannot compare components with different indices: "
                                + this.index
                                + " and "
                                + other.index);
            }
            int t1Component = this.clock.vector[this.index];
            int t2Component = other.clock.vector[other.index];
            if (t1Component < t2Component) {
                return Relation.LT;
            } else if (t1Component > t2Component) {
                return Relation.GT;
            } else {
                return Relation.EQ;
            }
        }
    }
}
