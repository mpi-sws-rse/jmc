package org.mpi_sws.jmc.mp.witness;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * WITNESS tester fixture (P2P): "srv" does one non-blocking receive ({@code tryRecv}); "c" sends A(1)
 * to srv. Two executions: srv reads ⊥, or reads c's A(1). Visible: srv and c.
 */
public final class WitnessTimeout implements MpProgram {
    @Tag("A") record A(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Handle srv = MpProcess.spawn("srv", () -> Channel.tryRecv(M, A.class));
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(M, srv.tid(), new A(1)));
        srv.await();
        c.await();
    }

    @Override public String programName() { return "witness-timeout"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("srv", "c"); }
}
