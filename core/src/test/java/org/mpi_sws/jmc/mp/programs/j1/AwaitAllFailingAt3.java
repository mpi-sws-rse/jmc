package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** M3 (J1 round 05): control of UnawaitedFailing: both awaited; a fails an assertion in run 3 only. */
public final class AwaitAllFailingAt3 extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public AwaitAllFailingAt3() {
        super("AwaitAllFailingAt3", CommunicationModel.P2P, List.of("a", "b"));
    }

    @Override
    public void run() {
        runs++;
        int thisRun = runs;
        MpProcess.Handle a = MpProcess.spawn("a", () -> {
            Channel.tryRecv(CommunicationModel.P2P);
            if (thisRun == 3) {
                throw new AssertionError("a: fails in run 3");
            }
        });
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.tryRecv(CommunicationModel.P2P));
        b.await();
        a.await();
    }

    @Override
    public String toString() {
        return "runs: " + runs;
    }
}
