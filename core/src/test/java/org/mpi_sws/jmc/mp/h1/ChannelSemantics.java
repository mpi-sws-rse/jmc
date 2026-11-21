package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * TraceForge {@code tests/channel_semantics.rs}: {@code spurious_timeout} (a NoOrder channel owned
 * by main: self-send, non-blocking receive) and {@code global_unique_channel} (two threads each
 * send a freshly built channel to main; a fresh channel is a distinct value, here a record holding
 * the sending thread's index, which only has to be distinct).
 *
 * <p>Arguments: {@code <test>}.
 */
public final class ChannelSemantics implements Runnable {

    /** An integer payload. */
    record I(int v) implements MpMessage {}

    /** A channel value: distinct per construction site. */
    record Chan(int id) implements MpMessage {}

    private final String name;

    /**
     * @param args test name
     */
    public ChannelSemantics(String... args) {
        this.name = args[0].trim();
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        Tid me = MpProcess.self();
        switch (name) {
            case "spurious_timeout" -> {
                Channel.send(CommunicationModel.ASYN, me, new I(1));
                Channel.tryRecv(CommunicationModel.ASYN);
            }
            case "global_unique_channel" -> {
                CommunicationModel m = CommunicationModel.P2P;
                sp.spawn(() -> Channel.send(m, me, new Chan(1)));
                sp.spawn(() -> Channel.send(m, me, new Chan(2)));
                MpMessage c1 = Channel.recv(m);
                MpMessage c2 = Channel.recv(m);
                if (c1.equals(c2)) {
                    throw new AssertionError("assert_ne!(ch1, ch2)");
                }
            }
            default -> throw new IllegalArgumentException("unknown channel_semantics test " + name);
        }
        sp.awaitAll();
    }
}
