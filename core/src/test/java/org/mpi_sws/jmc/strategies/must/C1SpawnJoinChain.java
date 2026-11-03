package org.mpi_sws.jmc.strategies.must;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * JMC-SPEED tester round 01 (C1 gate): a receiver r with two blocking p2p receives, and three senders
 * a, b, c spawned in a join chain (a; await a; b; await b; c). The join edges give
 * {@code c.send.hbIn = {a.send, b.send}} and {@code b.send.hbIn = {a.send}}.
 *
 * <p>Derived count: r's two receives read two distinct sends of the three, in either order; p2p
 * orders only messages of one sender, and each sender sends once, so every ordered pair is
 * consistent (no porf cycle: r has no outgoing edge). 3 · 2 = 6 complete graphs, 0 blocked.
 */
public final class C1SpawnJoinChain extends J1Fixture {

    /** Creates the fixture. */
    public C1SpawnJoinChain() {
        super("c1-spawn-join-chain", CommunicationModel.P2P, List.of("r", "a", "b", "c"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> {
            Channel.recv(CommunicationModel.P2P);
            Channel.recv(CommunicationModel.P2P);
        });
        Tid rt = r.tid();
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        a.await();
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(CommunicationModel.P2P, rt, new V(2)));
        b.await();
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(CommunicationModel.P2P, rt, new V(3)));
        r.await();
        c.await();
    }
}
