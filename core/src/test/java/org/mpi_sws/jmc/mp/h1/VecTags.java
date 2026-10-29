package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * TraceForge {@code tests/vec_tags.rs} (FIFO). Each vector-tag predicate selects exactly one of the
 * two messages main sends, so each message travels as the class its receive's predicate selects:
 * {@code basic}: tag {@code [7,1]} → {@code T71}, the empty tag (normalised to none) → {@code TNone};
 * {@code lexicographic}: {@code [1,5] < [2,0]} → {@code Low}, {@code [2,0]} → {@code High}.
 *
 * <p>Arguments: {@code <basic|lexicographic>}.
 */
public final class VecTags implements Runnable {

    /** Tag {@code [7,1]}. */
    record T71(int v) implements MpMessage {}

    /** Empty tag. */
    record TNone(int v) implements MpMessage {}

    /** A tag below {@code [2,0]}. */
    record Low(int v) implements MpMessage {}

    /** A tag at or above {@code [2,0]}. */
    record High(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;

    /**
     * @param args test name
     */
    public VecTags(String... args) {
        this.name = args[0].trim();
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (name) {
            case "basic" -> {
                Tid receiver = sp.spawn(() -> {
                    if (Channel.recv(M, T71.class).v() != 71) {
                        throw new AssertionError("v_exact");
                    }
                    if (Channel.recv(M, TNone.class).v() != 0) {
                        throw new AssertionError("v_none");
                    }
                });
                Channel.send(M, receiver, new TNone(0));
                Channel.send(M, receiver, new T71(71));
            }
            case "lexicographic" -> {
                Tid receiver = sp.spawn(() -> {
                    if (Channel.recv(M, Low.class).v() != 15) {
                        throw new AssertionError("low");
                    }
                    if (Channel.recv(M, High.class).v() != 20) {
                        throw new AssertionError("high");
                    }
                });
                Channel.send(M, receiver, new High(20));
                Channel.send(M, receiver, new Low(15));
            }
            default -> throw new IllegalArgumentException("unknown vec_tags test " + name);
        }
        sp.awaitAll();
    }
}
