package org.mpisws.util.aux;

/** Represents a generic total order relation. */
public interface TotalOrder<T> {
    /**
     * Compares two objects of type T.
     *
     * @param t1 The first object of type T.
     * @param t2 The second object of type T.
     * @return The relation between the two objects.
     */
    Relation compare(T t1, T t2) throws InvalidComparisonException;

    /** Represents the relation between two objects. */
    enum Relation {
        GT,
        LT,
        EQ,
    }

    /** Represents an exception thrown when an invalid comparison is attempted. */
    class InvalidComparisonException extends Exception {
        public InvalidComparisonException(String message) {
            super(message);
        }
    }
}
