package org.mpi_sws.jmc.mp.programs.seq;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.MustSection13;

/** Tester fixture (J1 round 04, event sequence): Must §1.3 over MpProcess. */
public final class SeqMpOk implements Runnable {
    /** Creates the fixture. */
    public SeqMpOk() {}

    @Override
    public void run() {
        MpProcess.Handle t3 = MpProcess.spawn("t3", () -> {
            Channel.recv(CommunicationModel.P2P);
            Channel.recv(CommunicationModel.P2P);
        });
        Tid t3Tid = t3.tid();
        MpProcess.Handle t2 = MpProcess.spawn("t2", () -> {
            Channel.recv(CommunicationModel.P2P);
            Channel.send(CommunicationModel.P2P, t3Tid, new MustSection13.Msg(3));
            if (t3Tid != null) {

            }
        });
        Tid t2Tid = t2.tid();
        MpProcess.Handle t1 = MpProcess.spawn("t1", () -> {
            Channel.send(CommunicationModel.P2P, t3Tid, new MustSection13.Msg(1));
            Channel.send(CommunicationModel.P2P, t2Tid, new MustSection13.Msg(2));
        });
        t1.await();
        t2.await();
        t3.await();
    }
}
