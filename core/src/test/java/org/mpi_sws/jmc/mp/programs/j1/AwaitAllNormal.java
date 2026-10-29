package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** M3 (J1 round 05): control of UnawaitedNormal: both awaited, nobody fails. */
public final class AwaitAllNormal extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public AwaitAllNormal() {
        super("AwaitAllNormal", CommunicationModel.P2P, List.of("a", "b"));
    }

    @Override
    public void run() {
        runs++;
        int thisRun = runs;
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.tryRecv(CommunicationModel.P2P));
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.tryRecv(CommunicationModel.P2P));
        b.await();
        a.await();
        if (thisRun < 0) {
            throw new IllegalStateException();
        }
    }

    @Override
    public String toString() {
        return "runs: " + runs;
    }
}
