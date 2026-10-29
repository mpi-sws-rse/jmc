package org.mpi_sws.jmc.mp.programs.mutants;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.MustSection13.Msg;

/** Tester mutant of MustSection13 (J0 round 01): only the sender-only role renamed: T1 instead of t1 (reviewer m1). */
public final class T1Misnamed implements Runnable {
    private int firstOne;
    private int firstThree;

    /** Creates the mutant. */
    public T1Misnamed() {}

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
        MpProcess.Handle t2 = MpProcess.spawn("t2", () -> { Channel.recv(m); Channel.send(m, t3Tid, new Msg(3)); });
        Tid t2Tid = t2.tid();
        MpProcess.Handle t1 = MpProcess.spawn("T1", () -> { Channel.send(m, t3Tid, new Msg(1)); Channel.send(m, t2Tid, new Msg(2)); });
        t1.await();
        t2.await();
        t3.await();
    }

    @Override
    public String toString() {
        return "t3 read 1 first: " + firstOne + "; 3 first: " + firstThree;
    }
}
