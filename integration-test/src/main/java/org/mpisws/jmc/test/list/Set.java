package org.mpisws.jmc.test.list;

/**
 * Set interface representing a collection of integers.
 */
public interface Set {

    /**
     * Adds an element to the set.
     *
     * @param i the element to be added
     */
    boolean add(int i);

    /**
     * Removes an element from the set.
     *
     * @param i the element to be removed
     * @return true if the element was successfully removed, false if it was not present
     */
    boolean remove(int i);

    /**
     * Checks if the set contains a specific element.
     *
     * @param i the element to check for
     * @return true if the element is present in the set, false otherwise
     */
    boolean contains(int i);
}
