package org.mpi_sws.jmc.strategies.trust;

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
    /** The set of known location hash codes (see {@link Location#hashCode()}). */
    private final Set<Integer> locations;
    /** Maps a new iteration's location hash code to the equivalent hash code from a prior one. */
    private final Map<Integer, Integer> aliases;

    /**
     * The reserved location used to model thread events. Thread-start events are treated as writes
     * on this location so that a total order can be maintained between them; its value is the hash
     * of the string {@code "thread"}.
     */
    public static Integer ThreadLocation = "thread".hashCode();

    /** Constructs a new location store. */
    public LocationStore() {
        locations = new HashSet<>();
        locations.add(ThreadLocation);
        aliases = new HashMap<>();
    }

    /**
     * Adds a location hash code to the store.
     *
     * @param location the location hash code to add.
     */
    public void addLocation(Integer location) {
        locations.add(location);
    }

    /** Removes all locations from the store. */
    public void clear() {
        locations.clear();
    }

    /**
     * Removes all aliases from the store. Called by the algorithm at the start of a guiding
     * iteration, once all locations in the graph have been mapped to the current iteration's hash
     * codes.
     */
    public void clearAliases() {
        aliases.clear();
    }

    /**
     * Returns whether the given hash code is known as either a location or an alias.
     *
     * @param hashCode the hash code to look up.
     * @return true if it is a known location or alias.
     */
    public boolean contains(Integer hashCode) {
        return locations.contains(hashCode) || aliases.containsKey(hashCode);
    }

    /**
     * Returns whether the given hash code is registered as an alias.
     *
     * @param hashCode the hash code to look up.
     * @return true if it is a known alias.
     */
    public boolean containsAlias(Integer hashCode) {
        return aliases.containsKey(hashCode);
    }

    /**
     * Records that a new iteration's location {@code newL} refers to the same variable as a prior
     * iteration's location {@code oldL}.
     *
     * @param oldL the canonical (older) location hash code.
     * @param newL the new hash code to alias onto {@code oldL}.
     */
    public void addAlias(Integer oldL, Integer newL) {
        locations.add(oldL);
        aliases.put(newL, oldL);
    }

    /**
     * Returns the canonical location aliased by the given hash code, or {@code null} if none.
     *
     * @param hashCode the alias hash code.
     * @return the canonical location hash code, or {@code null}.
     */
    public Integer getAlias(Integer hashCode) {
        return aliases.get(hashCode);
    }
}
