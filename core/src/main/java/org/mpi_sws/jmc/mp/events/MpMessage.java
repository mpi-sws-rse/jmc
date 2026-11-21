package org.mpi_sws.jmc.mp.events;

/**
 * Marker for a value that may be sent through the message-passing API — the {@code v ∈ Val} of
 * the Must paper (§3.1).
 *
 * <p><b>Value-type contract.</b> An
 * implementation must be immutable, must define {@code equals}/{@code hashCode} by value, must
 * have no identity-dependent behaviour, and must refer to processes only through {@link Tid},
 * never a raw {@code long}. Java {@code record}s over primitives, strings, other
 * {@code MpMessage}s and immutable collections satisfy this mechanically. The explicit-state
 * backend stores messages inside states and compares states for equality, and symmetry reduction
 * rewrites {@link Tid}s inside payloads; both break silently on a mutable or identity-compared
 * message.
 */
public interface MpMessage {}
