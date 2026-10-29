package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** C2(b): {@code r: recv(); recv() | s: send(r, V(1))} under p2p; r is blocked for good on its second receive. */
public final class BlockedAfterOne extends J1Fixture {
    /** Creates the fixture. */
    public BlockedAfterOne() {
        super("blocked-after-one", CommunicationModel.P2P, List.of("r", "s"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> {
            Channel.recv(CommunicationModel.P2P);
            Channel.recv(CommunicationModel.P2P);
        });
        Tid rt = r.tid();
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        s.await();
        r.await();
    }
}
