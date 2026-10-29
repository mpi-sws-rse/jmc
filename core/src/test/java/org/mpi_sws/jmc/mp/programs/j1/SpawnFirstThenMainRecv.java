package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/**
 * Spawn-edge timing probe, within the format: {@code main: spawn c, a, b; recv (from a); await all |
 * a: send(main, V1) | b: send(c, V2) | c: recv} under p2p. Main has no event when it spawns b, so no
 * spawn edge exists and a's send is never porf-before b's: one summary, counts
 * {@code [0,0,0,0,0,1]}, whatever order b starts in relative to main's receive.
 */
public final class SpawnFirstThenMainRecv extends J1Fixture {
    /** Creates the fixture. */
    public SpawnFirstThenMainRecv() {
        super("spawn-first-then-main-recv", CommunicationModel.P2P, List.of("c", "a", "b"));
    }

    @Override
    public void run() {
        Tid main = MpProcess.self();
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.recv(CommunicationModel.P2P));
        Tid ct = c.tid();
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(CommunicationModel.P2P, main, new V(1)));
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(CommunicationModel.P2P, ct, new V(2)));
        Channel.recv(CommunicationModel.P2P);
        a.await();
        b.await();
        c.await();
    }
}
