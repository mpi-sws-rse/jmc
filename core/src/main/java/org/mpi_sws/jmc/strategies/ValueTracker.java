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

    private final Map<Long, Object> values;

    public ValueTracker() {
        values = new HashMap<Long, Object>();
    }

     public ValueTracker(Map<Long, Object> values) {
        this.values = values;
    }

     public Map<Long, Object> getValues() {
        return values;
    }

    public Object getValue(long id) {
         return values.get(id);
    }

    public void setValue(long id, Object value) {
        values.put(id, value);
    }

    public void reset() {
        values.clear();
    }

    public void updateValues(Map<Long, Object> newValues) {
        values.putAll(newValues);
    }

    public void removeValue(long id) {
        values.remove(id);
    }

    public void removeAll() {
        values.clear();
    }

    public boolean containsValue(long id) {
        return values.containsKey(id);
    }

     public int size() {
        return values.size();
    }
}
