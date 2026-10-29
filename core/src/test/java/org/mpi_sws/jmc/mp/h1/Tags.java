package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * TraceForge {@code tests/tags.rs} (FIFO). {@code nontrivial_tag}: {@code first}'s two receives
 * accept every tag (unguarded); {@code second}'s receive accepts tag 1, and the only tag-1 message
 * to {@code second} is main's {@code TID} → guard {@code TidTg1}. {@code tag_on_sender}: the
 * predicate {@code tid == main && tag == 2}; only main ever sends to {@code first}, so the sender
 * conjunct is vacuous and the guard is the tag-2 class.
 *
 * <p>Arguments: {@code <test>}.
 */
public final class Tags implements Runnable {

    /** {@code Msg::Val} (any tag; {@code first} does not look at tags). */
    record Val(int v) implements MpMessage {}

    /** {@code Msg::TID} sent with tag 1. */
    record TidTg1(Tid t) implements MpMessage {}

    /** {@code Msg::Val} sent with tag 2. */
    record ValTg2(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;

    /**
     * @param args test name
     */
    public Tags(String... args) {
        this.name = args[0].trim();
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (name) {
            case "nontrivial_tag" -> {
                Tid first = sp.spawn(() -> {
                    if (!(Channel.recv(M) instanceof Val)) {
                        throw new IllegalStateException("panic!()");
                    }
                    if (!(Channel.recv(M) instanceof Val)) {
                        throw new IllegalStateException("panic!()");
                    }
                });
                Tid second = sp.spawn(() -> {
                    Tid firstId = Channel.recv(M, TidTg1.class).t();
                    Channel.send(M, firstId, new Val(1));
                    Channel.send(M, firstId, new Val(2));
                });
                Channel.send(M, second, new TidTg1(first));
                Channel.send(M, first, new Val(3));
                Channel.send(M, first, new Val(4));
            }
            case "tag_on_sender" -> {
                Tid first = sp.spawn(() -> Channel.recv(M, ValTg2.class));
                Channel.send(M, first, new ValTg2(4));
            }
            default -> throw new IllegalArgumentException("unknown tags test " + name);
        }
        sp.awaitAll();
    }
}
