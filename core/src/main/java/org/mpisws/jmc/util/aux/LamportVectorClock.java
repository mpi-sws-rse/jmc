package org.mpisws.jmc.util.aux;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Lamport vector clock used by the algorithm. It is of variable length.
 */
public class LamportVectorClock implements PartialOrder<LamportVectorClock> {

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
     * Creates a new Lamport vector clock with the given vector, incrementing the value at the given
     * index.
     *
     * @param other The vector clock.
     * @param index The index of the component to increment.
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
     * Updates the vector clock with the given vector clock.
     *
     * @param other The other vector clock.
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
     * Returns the string representation of the vector clock.
     *
     * @return The string representation of the vector clock.
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
     * Represents a component of the Lamport vector clock.
     */
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
