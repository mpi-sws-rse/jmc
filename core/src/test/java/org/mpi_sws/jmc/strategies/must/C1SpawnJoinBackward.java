package org.mpi_sws.jmc.strategies.must;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * JMC-SPEED tester round 01 (C1 gate): Must Ex. 4.2's shape with a join edge. s1 (task 2) sends V(1)
 * to r (task 3, addressed by {@code Tid.of(3)}, ids being minted in spawn order); r receives once;
 * main awaits s1 and only then spawns s2, which sends V(2). r's receive is inserted before s2's send,
 * so reading V(2) takes a backward revisit, and the revisiting send carries {@code hbIn = {s1.send}}:
 * the receive's new prefix {s2.send, s1.send} needs the hb join.
 *
 * <p>Derived count: r reads V(1) or V(2): 2 complete graphs, 0 blocked.
 */
public final class C1SpawnJoinBackward extends J1Fixture {

    /** Creates the fixture. */
    public C1SpawnJoinBackward() {
        super("c1-spawn-join-backward", CommunicationModel.P2P, List.of("s1", "r", "s2"));
    }

    @Override
    public void run() {
        Tid rt = Tid.of(3L);
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        if (r.tid().raw() != rt.raw()) {
            throw new IllegalStateException("receiver id " + r.tid() + " != " + rt);
        }
        s1.await();
        MpProcess.Handle s2 = MpProcess.spawn("s2", () -> Channel.send(CommunicationModel.P2P, rt, new V(2)));
        r.await();
        s2.await();
    }
}
