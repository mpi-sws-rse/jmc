package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/**
 * C4: {@code r: recv() | s: send(r, V(1))} under p2p; main awaits both and then fails an assertion
 * in its third run (one instance per launcher run, so the counter spans iterations).
 */
public final class AssertsInMainAt3 extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public AssertsInMainAt3() {
        super("asserts-at-3", CommunicationModel.P2P, List.of("r", "s"));
    }

    @Override
    public void run() {
        runs++;
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        r.await();
        s.await();
        if (runs == 3) {
            throw new AssertionError("main: fails in run 3");
        }
    }

    @Override
    public String toString() {
        return "runs: " + runs;
    }
}
