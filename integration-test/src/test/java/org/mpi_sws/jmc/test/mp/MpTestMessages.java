package org.mpi_sws.jmc.test.mp;

import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * The message values the route-A wiring tests send — the {@code v ∈ Val} of the Must paper (§3.1),
 * satisfying the value-type contract of {@link MpMessage} (plan/state/mp-contract.md §4.6):
 * {@code record}s over primitives and {@link Tid}, immutable, compared by value.
 *
 * <p><b>Why they live here rather than nested in each test class.</b> This package is instrumented
 * by the agent ({@code instrumentingPackages=org.mpi_sws.jmc.test} in
 * {@code integration-test/build.gradle.kts}), so every {@code GETFIELD} in a class of this package
 * would normally become a {@code READ_EVENT} plus a yield. A record's components are {@code final},
 * and {@code JmcReadWriteVisitor} skips final fields, so a record accessor emits nothing — which
 * matters because a receive <em>guard</em> is evaluated by the strategy from inside
 * {@code SchedulingStrategy.updateEvent}, on the requesting task's own thread. A guard that read a
 * non-final instrumented field would re-enter the runtime while an event was being processed.
 * Guards in these tests therefore only call record accessors and {@code Class::isInstance}.
 *
 * <p>This class holds no state of any kind, static or otherwise.
 */
public final class MpTestMessages {

    private MpTestMessages() {}

    /**
     * A plain integer-carrying message.
     *
     * @param v the payload
     */
    public record Val(int v) implements MpMessage {}

    /**
     * A request that names its sender, since a receiver never learns the sender otherwise
     * (plan/state/mp-contract.md §4.7).
     *
     * @param from the sender, to reply to
     * @param v the payload
     */
    public record Ping(Tid from, int v) implements MpMessage {}

    /**
     * The reply to a {@link Ping}.
     *
     * @param v the payload
     */
    public record Pong(int v) implements MpMessage {}
}
