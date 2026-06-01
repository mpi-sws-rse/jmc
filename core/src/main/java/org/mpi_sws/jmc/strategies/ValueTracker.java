package org.mpi_sws.jmc.strategies;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is designed to help strategies to track any existing values which they must return after handling
 * a particular event. For example, Trust strategy, in symbolic mode, needs to determine a boolean value after handling
 * any symbolic event. Trust strategy uses this tracker in order to keep these results. These values must be returned
 * to the program in execution via the yield point. Thus, upon calling the nextTask method of a strategy, these values
 * can be store into the corresponding future object and be retrieved after completing the future object.
 *
 * The other example is when the guiding mode in trust strategy is enabled, the strategy needs to keep the values of
 * the symbolic events in order to use them for guiding the execution. In this case, the strategy can use this tracker
 * to store these values and retrieve them when needed.
 */
public class ValueTracker {

    /** Pending values keyed by task ID, to be delivered to each task when it is resumed. */
    private final Map<Long, Object> values;

    /** Constructs an empty value tracker. */
    public ValueTracker() {
        values = new HashMap<Long, Object>();
    }

     /**
      * Constructs a value tracker backed by the given map.
      *
      * @param values the map to use as backing storage
      */
     public ValueTracker(Map<Long, Object> values) {
        this.values = values;
    }

     /**
      * Returns the backing map of all tracked values.
      *
      * @return the map of task ID to value
      */
     public Map<Long, Object> getValues() {
        return values;
    }

    /**
     * Returns the value tracked for the given task ID.
     *
     * @param id the task ID
     * @return the tracked value, or {@code null} if none
     */
    public Object getValue(long id) {
         return values.get(id);
    }

    /**
     * Sets the value to deliver to the given task.
     *
     * @param id the task ID
     * @param value the value to track for that task
     */
    public void setValue(long id, Object value) {
        values.put(id, value);
    }

    /** Clears all tracked values. */
    public void reset() {
        values.clear();
    }

    /**
     * Adds all entries from the given map to the tracked values.
     *
     * @param newValues the values to merge in
     */
    public void updateValues(Map<Long, Object> newValues) {
        values.putAll(newValues);
    }

    /**
     * Removes the value tracked for the given task ID.
     *
     * @param id the task ID whose value to remove
     */
    public void removeValue(long id) {
        values.remove(id);
    }

    /** Clears all tracked values. */
    public void removeAll() {
        values.clear();
    }

    /**
     * Returns whether a value is tracked for the given task ID.
     *
     * @param id the task ID
     * @return {@code true} if a value is tracked for that task
     */
    public boolean containsValue(long id) {
        return values.containsKey(id);
    }

     /**
      * Returns the number of tracked values.
      *
      * @return the number of tracked values
      */
     public int size() {
        return values.size();
    }
}
