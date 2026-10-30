package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * J8 export-refusal fixture (export mode): a daemon d (empty body) and two visible roles; c sends
 * {@code Ref(d)} to e. The strategy normalises d's Tid (d is named at creation), and the export
 * refuses the summary because the payload names a daemon. c sends Plain at nondet index 0
 * (iteration 0, the root) and Ref(d) at index 1 (the one stack entry, item 0 at K = 1).
 */
public final class DaemonTidPayload implements MpProgram {
    @Tag("Ref")
    record Ref(Tid t) implements MpMessage {}

    @Tag("Plain")
    record Plain(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Handle d = MpProcess.spawnDaemon(() -> { });
        MpProcess.Handle e = MpProcess.spawn("e", () -> Channel.recv(M));
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(M, e.tid(),
                Nondet.chooseIndex(2) == 0 ? new Plain(0) : new Ref(d.tid())));
        e.await();
        c.await();
    }

    @Override public String programName() { return "j8-daemon-tid"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("e", "c"); }
}
