package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** C2(a): S+R^nb under asyn. {@code r: tryRecv() | s: send(r, V(7))}. */
public final class SRnb extends J1Fixture {
    /** Creates the fixture. */
    public SRnb() {
        super("s+r-nb", CommunicationModel.ASYN, List.of("r", "s"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.tryRecv(CommunicationModel.ASYN));
        Tid rt = r.tid();
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(CommunicationModel.ASYN, rt, new V(7)));
        r.await();
        s.await();
    }
}
