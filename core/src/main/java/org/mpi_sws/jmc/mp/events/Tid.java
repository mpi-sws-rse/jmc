package org.mpi_sws.jmc.mp.events;

import java.util.Objects;

/**
 * A process (task) identifier — the {@code t ∈ Tid} of the Must paper (§3.1).
 *
 * <p>The identity is the raw JMC task id alone. The optional {@link #role()} is metadata for
 * symmetry reduction (processes of the same role are interchangeable) and is
 * deliberately <em>excluded</em> from {@link #equals(Object)} and {@link #hashCode()}, so that
 * tagging a role never changes program behaviour.
 *
 * <p>This type is pure: it has no notion of "the current task". Runtime-hosted code obtains its
 * own id from {@code org.mpi_sws.jmc.api.mp.MpProcess#self()}.
 */
public final class Tid implements Comparable<Tid> {

    private final long raw;
    private final String role;

    private Tid(long raw, String role) {
        this.raw = raw;
        this.role = Objects.requireNonNull(role, "role");
    }

    /**
     * Returns the identifier for the given raw JMC task id, with no role.
     *
     * @param raw the JMC task id (1-indexed in the runtime; 0-indexed internally in Trust)
     * @return the identifier
     */
    public static Tid of(long raw) {
        return new Tid(raw, "");
    }

    /**
     * Returns the identifier for the given raw JMC task id, tagged with a role.
     *
     * @param raw the JMC task id
     * @param role the role name (for symmetry reduction); the empty string means "no role"
     * @return the identifier
     */
    public static Tid of(long raw, String role) {
        return new Tid(raw, role);
    }

    /**
     * Returns the raw JMC task id.
     *
     * @return the raw id
     */
    public long raw() {
        return raw;
    }

    /**
     * Returns the role name.
     *
     * @return the role, or the empty string when none was set
     */
    public String role() {
        return role;
    }

    /**
     * Returns a copy of this identifier tagged with the given role. Identity is unchanged.
     *
     * @param role the role name
     * @return the tagged identifier
     */
    public Tid withRole(String role) {
        return new Tid(raw, role);
    }

    /** Orders identifiers by raw id, which gives every canonical form in the core a fixed order. */
    @Override
    public int compareTo(Tid other) {
        return Long.compare(raw, other.raw);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Tid other && other.raw == raw;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(raw);
    }

    @Override
    public String toString() {
        return role.isEmpty() ? "Tid(" + raw + ")" : "Tid(" + raw + ":" + role + ")";
    }
}
