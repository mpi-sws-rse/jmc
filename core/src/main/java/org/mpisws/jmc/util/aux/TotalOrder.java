package org.mpisws.jmc.util.aux;

/**
 * Represents a generic total order relation.
 */
public interface TotalOrder<T> {
    /**
     * Compares two objects of type T - the current and the other passed as argument.
     *
     * @param other the other object to compare to
     * @return The relation between the two objects.
     */
    Relation compare(T other) throws InvalidComparisonException;

    /**
     * Represents the relation between two objects.
     */
    enum Relation {
        GT,
        LT,
        EQ,
    }

    /**
     * Represents an exception thrown when an invalid comparison is attempted.
     */
    class InvalidComparisonException extends Exception {
        public InvalidComparisonException(String message) {
            super(message);
        }
    }
}
