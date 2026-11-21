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
 * WITNESS tester fixture (P2P). Main spawns a daemon (no role; its path name is {@code m/0}) that
 * receives two A, then "w" that receives one A, then "c": c chooses {@code x} (nondet of 2); for
 * x = 0 it sends A(5) to w; in both cases it sends A(7) to the daemon. The daemon always ends blocked
 * on its second receive (which does not make the execution blocked); for x = 1, w ends blocked and
 * main ends blocked on its await of w. Visible: w.
 */
public final class WitnessBlocked implements MpProgram {
    @Tag("A") record A(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Handle d = MpProcess.spawnDaemon(() -> {
            Channel.recv(M, A.class);
            Channel.recv(M, A.class);
        });
        MpProcess.Handle w = MpProcess.spawn("w", () -> Channel.recv(M, A.class));
        MpProcess.Handle c = MpProcess.spawn("c", () -> {
            if (Nondet.chooseIndex(2) == 0) {
                Channel.send(M, w.tid(), new A(5));
            }
            Channel.send(M, d.tid(), new A(7));
        });
        w.await();
        c.await();
    }

    @Override public String programName() { return "witness-blocked"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("w", "c"); }
    @Override public List<String> visibleRoles() { return List.of("w"); }
}
