package org.mpi_sws.jmc.strategies.trust;

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import static org.mpi_sws.jmc.api.JmcObject.handleHashCode;

/**
 * Identifies a shared-memory location accessed by an instrumented read or write.
 *
 * <p>A location is the pair (owning object instance, field name). Its {@link #hashCode()} combines
 * the instance's JMC identity hash with the field name, giving a value that is stable across the
 * many re-executions of a model-checking run. {@link LocationStore} maps that hash to the compact
 * integer location id stored on {@link Event}s, so that accesses to the "same" variable alias to the
 * same location in the execution graph.
 */
public class Location {
    /** The object whose field is being accessed (the class owner for static fields). */
    Object instance;
    /** The name of the field being accessed. */
    String param;

    /**
     * Creates a location for the given instance and field name.
     *
     * @param instance the owning object (or class owner for static fields).
     * @param param the field name.
     */
    public Location(Object instance, String param) {
        this.instance = instance;
        this.param = param;
    }

    /**
     * Builds a {@code Location} from a runtime read/write event.
     *
     * <p>Uses the event's {@code instance} parameter, falling back to {@code owner} for static
     * field accesses, together with the field {@code name}.
     *
     * @param runtimeEvent the runtime event describing the access.
     * @return the corresponding location.
     */
    public static Location fromRuntimeEvent(JmcRuntimeEvent runtimeEvent) {
        Object instance = runtimeEvent.getParam("instance");
        if (instance == null) {
            // This is because the call is a static method call
            instance = runtimeEvent.getParam("owner");
        }
        String param = runtimeEvent.getParam("name");
        return new Location(instance, param);
    }

    /**
     * Returns a re-execution-stable hash combining the instance's JMC identity hash and the field
     * name. This value is what {@link LocationStore} keys location ids on.
     *
     * @return the location hash.
     */
    @Override
    public int hashCode() {
        return (handleHashCode(instance) + param).hashCode();
    }
}
