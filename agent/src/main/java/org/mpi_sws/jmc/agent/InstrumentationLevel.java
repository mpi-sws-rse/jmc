package org.mpi_sws.jmc.agent;

/**
 * Controls the granularity of bytecode instrumentation applied by the JMC agent.
 *
 * <ul>
 *   <li>{@link #FULL} — instruments every field read/write with yield points (default, current behavior).
 *       Provides the finest-grained interleaving exploration but creates a large state space.
 *   <li>{@link #SYNC_POINTS_ONLY} — only instruments synchronization operations: lock acquire/release,
 *       thread start/join, atomic operations, and volatile accesses. Skips plain field read/write
 *       instrumentation. Much faster for large codebases where most fields are accessed under locks.
 * </ul>
 */
public enum InstrumentationLevel {

    /**
     * Full instrumentation: every GETFIELD/PUTFIELD/GETSTATIC/PUTSTATIC gets a yield point.
     * This is the default and provides maximum bug-finding power at the cost of performance.
     */
    FULL,

    /**
     * Only instrument synchronization points: locks, threads, atomics, volatile fields.
     * Plain field accesses are not instrumented. This dramatically reduces the state space
     * and is suitable for initial exploration of large projects like Caffeine where most
     * concurrent state is protected by locks or atomics.
     */
    SYNC_POINTS_ONLY;

    /**
     * Parses an instrumentation level from a string. Case-insensitive.
     * Returns {@link #FULL} if the string is unrecognized.
     *
     * @param value the string to parse (e.g. "full", "sync_points_only", "SYNC_POINTS_ONLY")
     * @return the corresponding InstrumentationLevel
     */
    public static InstrumentationLevel fromString(String value) {
        if (value == null) {
            return FULL;
        }
        return switch (value.toLowerCase().replace("-", "_")) {
            case "sync_points_only", "syncpointsonly", "sync" -> SYNC_POINTS_ONLY;
            default -> FULL;
        };
    }
}
