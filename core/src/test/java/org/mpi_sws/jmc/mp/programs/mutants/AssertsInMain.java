package org.mpi_sws.jmc.mp.programs.mutants;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.programs.MustSection13.Msg;

/**
 * Tester fixture (J0 round 01): main awaits both processes, then fails an assertion in every
 * iteration ({@code throw new AssertionError}); the run must fail.
 */
public final class AssertsInMain implements Runnable {
    /** Creates the fixture. */
    public AssertsInMain() {}

    @Override
    public void run() {
        MpProcess.Handle t2 = MpProcess.spawn("t2", () -> Channel.recv(CommunicationModel.P2P));
        MpProcess.Handle t1 = MpProcess.spawn("t1",
                () -> Channel.send(CommunicationModel.P2P, t2.tid(), new Msg(1)));
        t1.await();
        t2.await();
        throw new AssertionError("main: always fails");
    }
}
