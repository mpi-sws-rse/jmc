package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** M3 (J1 round 05): a terminates normally and is never awaited; b is awaited. Main silent. */
public final class UnawaitedNormal extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public UnawaitedNormal() {
        super("UnawaitedNormal", CommunicationModel.P2P, List.of("a", "b"));
    }

    @Override
    public void run() {
        runs++;
        int thisRun = runs;
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.tryRecv(CommunicationModel.P2P));
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.tryRecv(CommunicationModel.P2P));
        b.await();
        if (a == null || thisRun < 0) {
            throw new IllegalStateException();
        }
    }

    @Override
    public String toString() {
        return "runs: " + runs;
    }
}
