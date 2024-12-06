package org.mpisws.util.aux;

import java.util.ArrayList;
import java.util.List;

/** Represents a Lamport vector clock used by the algorithm. It is of variable length. */
public class LamportVectorClock implements PartialOrder<LamportVectorClock> {

    private final List<Integer> vector;

    /**
     * Creates a new Lamport vector clock with the given size.
     *
     * @param size The size of the vector clock.
     */
    public LamportVectorClock(int size) {
        this.vector = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            vector.add(0);
        }
    }

    /**
     * Creates a new Lamport vector clock with the given vector.
     *
     * @param vector The vector clock.
     */
    public LamportVectorClock(List<Integer> vector) {
        this.vector = new ArrayList<>(vector.size());
        this.vector.addAll(vector);
    }

    /**
     * Creates a new Lamport vector clock with the given vector, incrementing the value at the given
     * index.
     *
     * @param other The vector clock.
     * @param index The index of the component to increment.
     */
    public LamportVectorClock(LamportVectorClock other, int index) {
        this.vector = new ArrayList<>(other.vector);
        this.vector.set(index, other.vector.get(index) + 1);
    }

    /**
     * Returns the vector clock.
     *
     * @return The vector clock.
     */
    public List<Integer> getVector() {
        return vector;
    }

    /**
     * Returns the size of the vector clock.
     *
     * @return The size of the vector clock.
     */
    public int getSize() {
        return vector.size();
    }

    /**
     * Updates the vector clock with the given vector clock.
     *
     * @param other The other vector clock.
     */
    public void update(LamportVectorClock other) {
        int size = Math.max(vector.size(), other.vector.size());
        for (int i = 0; i < size; i++) {
            if (i >= vector.size()) {
                vector.add(0);
            }
            if (i >= other.vector.size()) {
                other.vector.add(0);
            }
            vector.set(i, Math.max(vector.get(i), other.vector.get(i)));
        }
    }

    /**
     * Checks if this vector clock happens before the other vector clock.
     *
     * @param other The other vector clock.
     * @return True if this vector clock happened before the other vector clock, false otherwise.
     */
    public boolean happensBefore(LamportVectorClock other) {
        boolean happenedBefore = false;
        boolean happenedAfter = false;
        int size = Math.max(vector.size(), other.vector.size());
        for (int i = 0; i < size; i++) {
            if (i >= vector.size()) {
                vector.add(0);
            }
            if (i >= other.vector.size()) {
                other.vector.add(0);
            }
            if (vector.get(i) < other.vector.get(i)) {
                happenedBefore = true;
            } else if (vector.get(i) > other.vector.get(i)) {
                happenedAfter = true;
            }
        }
        return happenedBefore && !happenedAfter;
    }

    /**
     * Returns the string representation of the vector clock.
     *
     * @return The string representation of the vector clock.
     */
    public boolean equals(LamportVectorClock other) {
        if (vector.size() != other.vector.size()) {
            return false;
        }
        for (int i = 0; i < vector.size(); i++) {
            if (!vector.get(i).equals(other.vector.get(i))) {
                return false;
            }
        }
        return true;
    }

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

    /** Represents a component of the Lamport vector clock. */
    public static class Component implements TotalOrder<Component> {

        private final int index;
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

        @Override
        public Relation compare(Component other) throws InvalidComparisonException {
            if (this.index != other.index) {
                throw new InvalidComparisonException(
                        "Cannot compare components with different indices: "
                                + this.index
                                + " and "
                                + other.index);
            }
            int t1Component = this.clock.vector.get(this.index);
            int t2Component = other.clock.vector.get(other.index);
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
