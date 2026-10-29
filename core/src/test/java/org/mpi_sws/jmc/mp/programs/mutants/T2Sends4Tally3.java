package org.mpi_sws.jmc.mp.programs.mutants;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.MustSection13.Msg;

/** Tester mutant of MustSection13 (J0 round 01): T2 sends 4 instead of 3; three-counter tally as in MustSection13 after round 01 (F6). */
public final class T2Sends4Tally3 implements Runnable {
    private int firstOne;
    private int firstThree;
    private int firstOther;

    /** Creates the mutant. */
    public T2Sends4Tally3() {}

    private void tally(MpMessage a) {
        int v = ((Msg) a).v();
        if (v == 1) {
            firstOne++;
        } else if (v == 3) {
            firstThree++;
        } else {
            firstOther++;
        }
    }

    @Override
    public void run() {
        CommunicationModel m = CommunicationModel.P2P;
        MpProcess.Handle t3 = MpProcess.spawn("t3", () -> { MpMessage a = Channel.recv(m); Channel.recv(m); tally(a); });
        Tid t3Tid = t3.tid();
        MpProcess.Handle t2 = MpProcess.spawn("t2", () -> { Channel.recv(m); Channel.send(m, t3Tid, new Msg(4)); });
        Tid t2Tid = t2.tid();
        MpProcess.Handle t1 = MpProcess.spawn("t1", () -> { Channel.send(m, t3Tid, new Msg(1)); Channel.send(m, t2Tid, new Msg(2)); });
        t1.await();
        t2.await();
        t3.await();
    }

    @Override
    public String toString() {
        return "t3 read 1 first: " + firstOne + "; 3 first: " + firstThree + "; other first: " + firstOther;
    }
}
