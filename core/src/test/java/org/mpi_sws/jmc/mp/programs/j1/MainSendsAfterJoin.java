package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/**
 * C2(d) probe of the spawn/join decision: {@code a: tryRecv() | b: recv() | main: spawn b, a;
 * await a; send(b, V(2)); await b} under p2p. Main spawns first and awaits every process, but sends
 * between two awaits. In TraceForge's porf ({@code ExecutionGraph::in_porf}, TJoin case) a's
 * receive is porf-before b's receive through main's join of a; without join edges it is not.
 */
public final class MainSendsAfterJoin extends J1Fixture {
    /** Creates the fixture. */
    public MainSendsAfterJoin() {
        super("main-sends-after-join", CommunicationModel.P2P, List.of("b", "a"));
    }

    @Override
    public void run() {
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(CommunicationModel.P2P));
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.tryRecv(CommunicationModel.P2P));
        a.await();
        Channel.send(CommunicationModel.P2P, b.tid(), new V(2));
        b.await();
    }
}
