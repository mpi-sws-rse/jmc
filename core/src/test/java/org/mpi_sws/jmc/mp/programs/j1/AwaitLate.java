package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** M3 (J1 round 05): a sends to b and has terminated before b receives; main awaits b first, then a. Accepted. */
public final class AwaitLate extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public AwaitLate() {
        super("AwaitLate", CommunicationModel.P2P, List.of("b", "a"));
    }

    @Override
    public void run() {
        runs++;
        int thisRun = runs;
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(CommunicationModel.P2P));
        Tid bt = b.tid();
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(CommunicationModel.P2P, bt, new V(1)));
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
