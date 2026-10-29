package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** Shape refusal: main spawns b, then a, which awaits b; main awaits a and b. */
public final class ProcessAwaitsProcess extends J1Fixture {
    /** Creates the fixture. */
    public ProcessAwaitsProcess() {
        super("process-awaits-process", CommunicationModel.P2P, List.of("b", "a"));
    }

    @Override
    public void run() {
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.tryRecv(CommunicationModel.P2P));
        MpProcess.Handle a = MpProcess.spawn("a", () -> {
            Channel.tryRecv(CommunicationModel.P2P);
            b.await();
        });
        a.await();
        b.await();
    }
}
