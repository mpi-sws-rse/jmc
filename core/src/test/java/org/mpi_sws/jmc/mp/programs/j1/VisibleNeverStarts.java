package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** C6: declares roles {@code [a, b]} but spawns only {@code a} (which does one non-blocking receive; main silent). */
public final class VisibleNeverStarts extends J1Fixture {
    /** Creates the fixture. */
    public VisibleNeverStarts() {
        super("never-starts", CommunicationModel.P2P, List.of("a", "b"));
    }

    @Override
    public void run() {
        // round 04: main is silent (the shape rule refuses a receiving main), a does one tryRecv
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.tryRecv(CommunicationModel.P2P));
        a.await();
    }
}
