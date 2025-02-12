package org.mpisws.strategies.trust;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A single class to store references to locations and to keep track of location aliases.
 *
 * <p>Location objects are shared objects used in the program. Whenever a new iteration of the model
 * checker runs, we will replace the Location object associated with the old hashcode with the new
 * one and add an alias that points the new hash code to the old one.
 *
 * <p>LocationStore is accessed when events are accessed.
 *
 * <p>The lifetime of a location store is that of the algorithm.
 */
public class LocationStore {
    // A map of location hash codes to locations
    private final Set<Integer> locations;
    // When a location is replaced, a mapping is added to aliases
    private final Map<Integer, Integer> aliases;

    // A special Location to represent thread events. This is used to track total order between
    // thread start events
    // Essentially, thread starts are writes on the thread location
    public static Integer ThreadLocation = "thread".hashCode();

    /** Constructs a new location store. */
    public LocationStore() {
        locations = new HashSet<>();
        locations.add(ThreadLocation);
        aliases = new HashMap<>();
    }

    /** Add a location to the store. */
    public void addLocation(Integer location) {
        locations.add(location);
    }

    /** Remove all locations from the store. */
    public void clear() {
        locations.clear();
    }

    /** Remove all aliases from the store. */
    public void clearAliases() {
        aliases.clear();
    }

    /** Returns if a location is contained in the store. */
    public boolean contains(Integer hashCode) {
        return locations.contains(hashCode) || aliases.containsKey(hashCode);
    }

    /** Returns if an alias is contained in the store. */
    public boolean containsAlias(Integer hashCode) {
        return aliases.containsKey(hashCode);
    }

    /** Adds an alias between the two location codes. */
    public void addAlias(Integer oldL, Integer newL) {
        locations.add(oldL);
        aliases.put(newL, oldL);
    }

    /** Returns the location alias for the given hash code. */
    public Integer getAlias(Integer hashCode) {
        return aliases.get(hashCode);
    }
}
