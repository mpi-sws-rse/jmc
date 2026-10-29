package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * TraceForge {@code tests/minibus.rs} (FIFO), a toy Microbus: acceptor, committer, standby; main
 * sends each a {@code Config(main, acceptor, committer, standby)}, then either waits for the
 * acceptor's {@code Ack} before sending {@code Terminate} to all ({@code wait}, TraceForge
 * asserts 9 executions) or sends {@code Terminate} at once ({@code nowait}, 13). A first message
 * that is not {@code Config} is {@code assume!(false)} (encoded).
 *
 * <p>Arguments: {@code <wait|nowait>}.
 */
public final class Minibus implements Runnable {

    /** {@code Msg::Ack}. */
    record Ack() implements MpMessage {}

    /** {@code Msg::Replicate}. */
    record Replicate() implements MpMessage {}

    /** {@code Msg::StoreCommitted}. */
    record StoreCommitted() implements MpMessage {}

    /** {@code Msg::MyStatus}. */
    record MyStatus() implements MpMessage {}

    /** {@code Msg::Terminate}. */
    record Terminate() implements MpMessage {}

    /** {@code Msg::Config((cl, ac, cm, st))}. */
    record Config(Tid cl, Tid ac, Tid cm, Tid st) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final boolean wait;

    /**
     * @param args {@code wait} or {@code nowait}
     */
    public Minibus(String... args) {
        this.wait = switch (args[0].trim()) {
            case "wait" -> true;
            case "nowait" -> false;
            default -> throw new IllegalArgumentException("Minibus takes <wait|nowait>");
        };
    }

    private static Config recvConfig() {
        MpMessage m = Channel.recv(M);
        if (m instanceof Config c) {
            return c;
        }
        H1Support.assume(false, M);
        throw new IllegalStateException("panic!()");
    }

    private static void acceptor() {
        Config c = recvConfig();
        Channel.send(M, c.cm(), new Replicate());
        while (true) {
            MpMessage m = Channel.recv(M);
            if (m instanceof MyStatus) {
                Channel.send(M, c.cl(), new Ack());
            } else if (m instanceof Terminate) {
                break;
            } else {
                throw new IllegalStateException("panic!() " + m);
            }
        }
    }

    private static void committer() {
        Config c = recvConfig();
        while (true) {
            MpMessage m = Channel.recv(M);
            if (m instanceof Replicate) {
                Channel.send(M, c.st(), new StoreCommitted());
                Channel.send(M, c.ac(), new MyStatus());
            } else if (m instanceof Terminate) {
                break;
            } else if (!(m instanceof MyStatus)) {
                throw new IllegalStateException("panic!() " + m);
            }
        }
    }

    private static void standby() {
        Config c = recvConfig();
        while (true) {
            MpMessage m = Channel.recv(M);
            if (m instanceof StoreCommitted) {
                Channel.send(M, c.ac(), new MyStatus());
                Channel.send(M, c.cm(), new MyStatus());
            } else if (m instanceof Terminate) {
                break;
            } else {
                throw new IllegalStateException("panic!() " + m);
            }
        }
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        Tid acceptor = sp.spawn(Minibus::acceptor);
        Tid committer = sp.spawn(Minibus::committer);
        Tid standby = sp.spawn(Minibus::standby);
        Config config = new Config(MpProcess.self(), acceptor, committer, standby);
        for (Tid t : new Tid[] {acceptor, committer, standby}) {
            Channel.send(M, t, config);
        }
        if (wait) {
            MpMessage m = Channel.recv(M);
            if (!(m instanceof Ack)) {
                throw new IllegalStateException("panic!() " + m);
            }
        }
        for (Tid t : new Tid[] {acceptor, committer, standby}) {
            Channel.send(M, t, new Terminate());
        }
        sp.awaitAll();
    }
}
