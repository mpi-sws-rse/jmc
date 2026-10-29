package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.MustSection13;

import java.util.List;

/**
 * Mutated fixture for the C1 golden test: MustSection13 with t1's two sends swapped
 * ({@code t1: send(t2, 2); send(t3, 1)}). Its summaries differ from the golden lines (t1's word, and
 * t3 reading 1 now has both of t1's sends before it).
 */
public final class MustSection13SendsSwapped extends J1Fixture {
    /** Creates the fixture. */
    public MustSection13SendsSwapped() {
        super("must-1.3", CommunicationModel.P2P, List.of("t3", "t2", "t1"));
    }

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
        });
        Tid t2Tid = t2.tid();
        MpProcess.Handle t1 = MpProcess.spawn("t1", () -> {
            Channel.send(CommunicationModel.P2P, t2Tid, new MustSection13.Msg(2));
            Channel.send(CommunicationModel.P2P, t3Tid, new MustSection13.Msg(1));
        });
        t1.await();
        t2.await();
        t3.await();
    }
}
