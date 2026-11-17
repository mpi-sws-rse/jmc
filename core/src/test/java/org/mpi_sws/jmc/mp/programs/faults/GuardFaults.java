package org.mpi_sws.jmc.mp.programs.faults;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;

import java.util.List;
import java.util.function.Predicate;

/**
 * A receive whose guard misbehaves. Main spawns {@code b} then {@code a} and awaits both; {@code b}
 * receives a {@code V} under the guard of the variant, {@code a} sends {@code V(1)} to {@code b}.
 * Under {@code must}, {@code b} runs first, finds no message and is parked; once {@code a} has
 * sent, the parked receive is re-issued, and its guard first runs, when no task is runnable.
 * <ul>
 *   <li>{@code none}: the guard accepts every message (control);</li>
 *   <li>{@code throws}: the guard throws an {@code IllegalStateException("guard boom")};</li>
 *   <li>{@code error}: the guard throws an {@code AssertionError("guard assertion")}, an
 *       {@code Error} rather than an {@code Exception};</li>
 *   <li>{@code self}: the guard calls {@code MpProcess.self()};</li>
 *   <li>{@code send}: the guard sends {@code V(0)} to itself.</li>
 * </ul>
 * Argument: {@code GuardFaults:<variant>}.
 */
public final class GuardFaults implements Runnable {

    /** The message. */
    public record V(int x) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String variant;

    /**
     * Creates the fixture.
     *
     * @param args the variant
     */
    public GuardFaults(String... args) {
        this.variant = args[0].trim();
        if (!List.of("none", "throws", "error", "self", "send").contains(variant)) {
            throw new IllegalArgumentException("unknown variant " + variant);
        }
    }

    @Override
    public void run() {
        Predicate<V> guard = switch (variant) {
            case "none" -> v -> true;
            case "throws" -> v -> {
                throw new IllegalStateException("guard boom");
            };
            case "error" -> v -> {
                throw new AssertionError("guard assertion");
            };
            case "self" -> v -> MpProcess.self() != null;
            case "send" -> v -> {
                Channel.send(M, MpProcess.self(), new V(0));
                return true;
            };
            default -> throw new IllegalStateException(variant);
        };
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(M, V.class, guard));
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(M, b.tid(), new V(1)));
        b.await();
        a.await();
    }
}
