package org.mpisws.util.aux;

/**
 * Represents a generic partial order relation.
 */
public interface PartialOrder<T> {

    /**
     * Compares two objects of type T - the current instance and the other object.
     *
     * @param other the other object to compare to.
     * @return The relation between the two objects.
     */
    Relation compare(T other);

    /**
     * Represents the relation between two objects.
     */
    enum Relation {
        GT,
        LT,
        EQ,
        UNRELATED
    }
}
