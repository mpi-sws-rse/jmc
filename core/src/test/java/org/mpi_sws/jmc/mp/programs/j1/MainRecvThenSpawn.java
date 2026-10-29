package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/**
 * Spawn-edge probe: {@code main: spawn c, a; recv (from a); spawn b; await all |
 * a: send(main, V1) | b: send(c, V2) | c: recv} under p2p. Main's receive precedes the spawn of b,
 * so a's send is porf-before every event of b. (Outside the format's "spawn first" rule; a probe.)
 */
public final class MainRecvThenSpawn extends J1Fixture {
    /** Creates the fixture. */
    public MainRecvThenSpawn() {
        super("main-recv-then-spawn", CommunicationModel.P2P, List.of("c", "a", "b"));
    }

    @Override
    public void run() {
        Tid main = MpProcess.self();
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.recv(CommunicationModel.P2P));
        Tid ct = c.tid();
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(CommunicationModel.P2P, main, new V(1)));
        Channel.recv(CommunicationModel.P2P);
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(CommunicationModel.P2P, ct, new V(2)));
        a.await();
        b.await();
        c.await();
    }
}
