package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/**
 * F1 export: {@code r: recv() | s: send(r, V(1)); assert fails in run 3} under p2p; main is silent
 * (it only counts runs). One instance per launcher run.
 */
public final class AssertsInProcessAt3 extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public AssertsInProcessAt3() {
        super("asserts-in-process-at-3", CommunicationModel.P2P, List.of("r", "s"));
    }

    @Override
    public void run() {
        runs++;
        int thisRun = runs;
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        MpProcess.Handle s = MpProcess.spawn("s", () -> {
            Channel.send(CommunicationModel.P2P, rt, new V(1));
            if (thisRun == 3) {
                throw new AssertionError("s: fails in run 3");
            }
        });
        r.await();
        s.await();
    }

    @Override
    public String toString() {
        return "runs: " + runs;
    }
}
