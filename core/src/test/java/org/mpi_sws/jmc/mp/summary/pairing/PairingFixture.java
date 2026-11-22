package org.mpi_sws.jmc.mp.summary.pairing;

import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;
import java.util.Optional;

/**
 * Message types for {@code PairingCheckTest}'s collection of a program's tags: a record message, a sealed message
 * interface with two records, an enum message, and a record and an enum reached only through components (an
 * {@link Optional} and a {@link List}). Not a program: only its package is scanned.
 */
public final class PairingFixture {

    private PairingFixture() {}

    /**
     * A record message.
     *
     * @param n its value
     */
    @Tag("Ping")
    public record Ping(int n) implements MpMessage {}

    /** A sealed message interface. */
    public sealed interface Cmd extends MpMessage permits Put, Get {}

    /**
     * A put.
     *
     * @param key the key
     * @param value the value, if any
     */
    @Tag("Put")
    public record Put(String key, Optional<Val> value) implements Cmd {}

    /**
     * A get.
     *
     * @param key the key
     */
    @Tag("Get")
    public record Get(String key) implements Cmd {}

    /**
     * A value, reached only through {@link Put}.
     *
     * @param colours its colours
     */
    @Tag("Val")
    public record Val(List<Colour> colours) {}

    /** A colour, reached only through {@link Val}. */
    public enum Colour { RED, GREEN }

    /** An enum message. */
    public enum Signal implements MpMessage { STOP }

    /**
     * A second {@code Ping} with another arity, as two Rust enums may each have a variant of one name.
     *
     * @param a the first value
     * @param b the second value
     */
    @Tag("Ping")
    public record Ping2(int a, int b) implements MpMessage {}
}
