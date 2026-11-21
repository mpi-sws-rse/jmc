package org.mpi_sws.jmc.mp.witness;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * WITNESS tester fixture (P2P). Main chooses {@code x} (a nondet of 2, before any spawn), then spawns
 * "srv", "c1", "c2"; c1 sends A(1 + 10x) to srv, c2 sends A(2); srv receives two A. Visible: srv;
 * c1 and c2 are invisible. Four executions (x in {0, 1}, srv reads c1's or c2's send first), four
 * distinct summaries.
 */
public final class WitnessTiny implements MpProgram {
    @Tag("A") record A(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        int x = Nondet.chooseIndex(2);
        MpProcess.Handle srv = MpProcess.spawn("srv", () -> {
            Channel.recv(M, A.class);
            Channel.recv(M, A.class);
        });
        MpProcess.Handle c1 = MpProcess.spawn("c1", () -> Channel.send(M, srv.tid(), new A(1 + 10 * x)));
        MpProcess.Handle c2 = MpProcess.spawn("c2", () -> Channel.send(M, srv.tid(), new A(2)));
        srv.await();
        c1.await();
        c2.await();
    }

    @Override public String programName() { return "witness-tiny"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("srv", "c1", "c2"); }
    @Override public List<String> visibleRoles() { return List.of("srv"); }
}
