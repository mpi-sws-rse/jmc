package org.mpi_sws.jmc.util;

/**
 * A generic partial order: a comparison that may leave two elements <em>unrelated</em>.
 *
 * <p>Implemented by {@link LamportVectorClock} so that execution-graph events can be compared in the
 * happens-before order, where concurrent events are {@code UNRELATED}.
 *
 * @param <T> the type of elements being compared
 */
public interface PartialOrder<T> {

    /**
     * Compares this instance with {@code other}.
     *
     * @param other the other object to compare to.
     * @return the relation of this instance to {@code other}.
     */
    Relation compare(T other);

    /** The possible outcomes of comparing two elements of a partial order. */
    enum Relation {
        /** This element is strictly greater than the other. */
        GT,
        /** This element is strictly less than the other. */
        LT,
        /** The two elements are equal. */
        EQ,
        /** The two elements are incomparable (neither precedes the other). */
        UNRELATED
    }
}
