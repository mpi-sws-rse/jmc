package org.mpi_sws.jmc.strategies.must;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * JMC-SPEED tester round 01 (C1 gate): a receiver r (one blocking p2p receive), a sender s1, and a
 * sender s2 spawned only after main awaited s1. s2's send has the join edge {@code s1.send → s2.send}
 * in its {@code hbIn}, so the prefix of r's receive when it reads s2's send must contain s1's send
 * although no rf or po edge connects them.
 *
 * <p>Derived count (p2p, one receive, two sends from different senders): r reads V(1) or V(2), and the
 * other message stays unread; r never blocks. 2 complete graphs, 0 blocked.
 */
public final class C1SpawnJoinRevisit extends J1Fixture {

    /** Creates the fixture. */
    public C1SpawnJoinRevisit() {
        super("c1-spawn-join-revisit", CommunicationModel.P2P, List.of("r", "s1", "s2"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        s1.await();
        MpProcess.Handle s2 = MpProcess.spawn("s2", () -> Channel.send(CommunicationModel.P2P, rt, new V(2)));
        r.await();
        s2.await();
    }
}
