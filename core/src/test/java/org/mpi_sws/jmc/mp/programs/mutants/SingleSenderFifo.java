package org.mpi_sws.jmc.mp.programs.mutants;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.MustSection13.Msg;

/** Tester mutant of MustSection13 (J0 round 01): T1 sends 1 and 3 to T3 itself, T2 only receives (p2p FIFO: only 1-then-3). */
public final class SingleSenderFifo implements Runnable {
    private int firstOne;
    private int firstThree;

    /** Creates the mutant. */
    public SingleSenderFifo() {}

    private void tally(MpMessage a) {
        if (((Msg) a).v() == 1) {
            firstOne++;
        } else {
            firstThree++;
        }
    }

    @Override
    public void run() {
        CommunicationModel m = CommunicationModel.P2P;
        MpProcess.Handle t3 = MpProcess.spawn("t3", () -> { MpMessage a = Channel.recv(m); Channel.recv(m); tally(a); });
        Tid t3Tid = t3.tid();
        MpProcess.Handle t2 = MpProcess.spawn("t2", () -> { Channel.recv(m); });
        Tid t2Tid = t2.tid();
        MpProcess.Handle t1 = MpProcess.spawn("t1", () -> { Channel.send(m, t3Tid, new Msg(1)); Channel.send(m, t3Tid, new Msg(3)); Channel.send(m, t2Tid, new Msg(2)); });
        t1.await();
        t2.await();
        t3.await();
    }

    @Override
    public String toString() {
        return "t3 read 1 first: " + firstOne + "; 3 first: " + firstThree;
    }
}
