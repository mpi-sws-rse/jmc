package org.mpi_sws.jmc.util;

/**
 * A generic total order: a comparison in which every two elements are related.
 *
 * <p>Unlike {@link PartialOrder}, there is no {@code UNRELATED} outcome; a comparison that cannot be
 * made (e.g. two clock components at different indices) is signalled with {@link
 * InvalidComparisonException} instead. Implemented by {@link LamportVectorClock.Component}.
 *
 * @param <T> the type of elements being compared
 */
public interface TotalOrder<T> {
    /**
     * Compares this instance with {@code other}.
     *
     * @param other the other object to compare to
     * @return the relation of this instance to {@code other}.
     * @throws InvalidComparisonException if the two objects cannot be meaningfully compared.
     */
    Relation compare(T other) throws InvalidComparisonException;

    /** The possible outcomes of comparing two elements of a total order. */
    enum Relation {
        /** This element is strictly greater than the other. */
        GT,
        /** This element is strictly less than the other. */
        LT,
        /** The two elements are equal. */
        EQ,
    }

    /** Thrown when two elements cannot be meaningfully compared under the total order. */
    class InvalidComparisonException extends Exception {
        /**
         * Creates the exception with an explanatory message.
         *
         * @param message the detail message.
         */
        public InvalidComparisonException(String message) {
            super(message);
        }
    }
}
