package org.mpi_sws.jmc.mp.programs.mutants;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.programs.MustSection13.Msg;

/**
 * Tester fixture (J0 round 01): t2 receives t1's message and fails an assertion on it in every
 * iteration ({@code throw new AssertionError}); the run must fail.
 */
public final class AssertsInProcess implements Runnable {
    /** Creates the fixture. */
    public AssertsInProcess() {}

    @Override
    public void run() {
        MpProcess.Handle t2 = MpProcess.spawn("t2", () -> {
            MpMessage a = Channel.recv(CommunicationModel.P2P);
            if (((Msg) a).v() == 1) {
                throw new AssertionError("t2 received 1");
            }
        });
        MpProcess.Handle t1 = MpProcess.spawn("t1",
                () -> Channel.send(CommunicationModel.P2P, t2.tid(), new Msg(1)));
        t1.await();
        t2.await();
    }
}
