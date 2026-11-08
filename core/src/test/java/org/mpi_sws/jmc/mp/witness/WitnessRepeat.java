package org.mpi_sws.jmc.mp.witness;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * WITNESS tester fixture (P2P): "c" sends A(1) to the visible "srv"; the invisible "n" makes a nondet
 * of 3 and nothing else. Three executions, one summary: an uncovered summary occurs three times.
 */
public final class WitnessRepeat implements MpProgram {
    @Tag("A") record A(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Handle srv = MpProcess.spawn("srv", () -> Channel.recv(M, A.class));
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(M, srv.tid(), new A(1)));
        MpProcess.Handle n = MpProcess.spawn("n", () -> Nondet.chooseIndex(3));
        srv.await();
        c.await();
        n.await();
    }

    @Override public String programName() { return "witness-repeat"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("srv", "c", "n"); }
    @Override public List<String> visibleRoles() { return List.of("srv"); }
}
