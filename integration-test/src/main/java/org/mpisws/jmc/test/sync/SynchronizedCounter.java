package org.mpisws.jmc.test.sync;

/**
 * Interface for a synchronized counter that can be incremented and queried for its count.
 * Implementations should ensure thread safety when incrementing the count and retrieving it.
 */
public interface SynchronizedCounter {

    /**
     * Increments the count in a synchronized manner.
     */
    void increment();

    /**
     * Returns the current count.
     *
     * @return the current count
     */
    int getCount();
}
