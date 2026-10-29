package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** C2(b'): {@code r: recv()} alone under p2p; blocked with an empty word. */
public final class BlockedAlone extends J1Fixture {
    /** Creates the fixture. */
    public BlockedAlone() {
        super("blocked-alone", CommunicationModel.P2P, List.of("r"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        r.await();
    }
}
