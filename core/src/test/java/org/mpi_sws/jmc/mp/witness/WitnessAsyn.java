package org.mpi_sws.jmc.mp.witness;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * WITNESS tester fixture (ASYN): "c" sends A(1), then A(2), to "srv"; srv receives two A. Under ASYN
 * the two sends of one sender are unordered, so srv reads them in either order: two executions. Visible:
 * srv and c.
 */
public final class WitnessAsyn implements MpProgram {
    @Tag("A") record A(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.ASYN;

    @Override
    public void run() {
        MpProcess.Handle srv = MpProcess.spawn("srv", () -> {
            Channel.recv(M, A.class);
            Channel.recv(M, A.class);
        });
        MpProcess.Handle c = MpProcess.spawn("c", () -> {
            Channel.send(M, srv.tid(), new A(1));
            Channel.send(M, srv.tid(), new A(2));
        });
        srv.await();
        c.await();
    }

    @Override public String programName() { return "witness-asyn"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("srv", "c"); }
}
