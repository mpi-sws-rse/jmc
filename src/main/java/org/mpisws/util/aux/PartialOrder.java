package org.mpisws.util.aux;

/** Represents a generic partial order relation. */
public interface PartialOrder<T> {

    /**
     * Compares two objects of type T.
     *
     * @param t1 The first object of type T.
     * @param t2 The second object of type T.
     * @return The relation between the two objects.
     */
    Relation compare(T t1, T t2);

    /** Represents the relation between two objects. */
    enum Relation {
        GT,
        LT,
        EQ,
        UNRELATED
    }
}
