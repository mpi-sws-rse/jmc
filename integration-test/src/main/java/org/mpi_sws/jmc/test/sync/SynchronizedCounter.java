package org.mpi_sws.jmc.test.sync;

/**
 * Interface for a synchronized counter that can be incremented and queried for its count.
 * Implementations should ensure thread safety when incrementing the count and retrieving it.
 */
public interface SynchronizedCounter {

    /** Increments the count in a synchronized manner. */
    void increment();

    /**
     * Increments the count by a specified value in a synchronized manner.
     *
     * @param value the value to increment the count by
     */
    void increment(int value);

    /**
     * Returns the current count.
     *
     * @return the current count
     */
    int getCount();
}
