package org.mpi_sws.jmc.mp.programs.mutants;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.programs.MustSection13.Msg;

/** Tester mutant of MustSection13 (J0 round 01): main performs T1's two sends itself (main visible). */
public final class MainIsT1 implements Runnable {
    private int firstOne;
    private int firstThree;

    /** Creates the mutant. */
    public MainIsT1() {}

    @Override
    public void run() {
        CommunicationModel m = CommunicationModel.P2P;
        MpProcess.Handle t3 = MpProcess.spawn("t3", () -> {
            MpMessage a = Channel.recv(m);
            Channel.recv(m);
            if (((Msg) a).v() == 1) {
                firstOne++;
            } else {
                firstThree++;
            }
        });
        MpProcess.Handle t2 = MpProcess.spawn("t2", () -> {
            Channel.recv(m);
            Channel.send(m, t3.tid(), new Msg(3));
        });
        Channel.send(m, t3.tid(), new Msg(1));
        Channel.send(m, t2.tid(), new Msg(2));
        t2.await();
        t3.await();
    }

    @Override
    public String toString() {
        return "t3 read 1 first: " + firstOne + "; 3 first: " + firstThree;
    }
}
