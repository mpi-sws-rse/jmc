package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** C6: declares one role {@code r} but spawns two processes. */
public final class TooManyProcesses extends J1Fixture {
    /** Creates the fixture. */
    public TooManyProcesses() {
        super("too-many", CommunicationModel.P2P, List.of("r"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        r.await();
        s.await();
    }
}
