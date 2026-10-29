package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/**
 * Per-thread visibility: {@code a: send(m, V(1)) | m: x := recv(); send(b, x) | b: recv()} under
 * p2p with visible roles {@code [a, b]}; counts go through the invisible process {@code m}.
 */
public final class ProcessRelay extends J1Fixture {
    /** Creates the fixture. */
    public ProcessRelay() {
        super("process-relay", CommunicationModel.P2P, List.of("b", "m", "a"));
    }

    @Override
    public List<String> visibleRoles() {
        return List.of("a", "b");
    }

    @Override
    public void run() {
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(CommunicationModel.P2P));
        Tid bt = b.tid();
        MpProcess.Handle m = MpProcess.spawn("m", () -> {
            MpMessage x = Channel.recv(CommunicationModel.P2P);
            Channel.send(CommunicationModel.P2P, bt, x);
        });
        Tid mt = m.tid();
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(CommunicationModel.P2P, mt, new V(1)));
        a.await();
        m.await();
        b.await();
    }
}
