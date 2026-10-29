package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** Shape refusal: process a spawns process c (and awaits it); main spawns a and awaits it. */
public final class ProcessSpawnsProcess extends J1Fixture {
    /** Creates the fixture. */
    public ProcessSpawnsProcess() {
        super("process-spawns-process", CommunicationModel.P2P, List.of("a", "c"));
    }

    @Override
    public void run() {
        MpProcess.Handle a = MpProcess.spawn("a", () -> {
            Channel.tryRecv(CommunicationModel.P2P);
            MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.tryRecv(CommunicationModel.P2P));
            c.await();
        });
        a.await();
    }
}
