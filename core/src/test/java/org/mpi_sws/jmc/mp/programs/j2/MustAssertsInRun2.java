package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.ArrayList;
import java.util.List;

/** C5 aborted: r receives from s; s fails an assertion in run 2 (the last of the two S+S+R graphs); main silent; p2p. */
public final class MustAssertsInRun2 extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public MustAssertsInRun2() {
        super("must-asserts-in-run-2", CommunicationModel.P2P, List.of("r", "s1", "s2"));
    }

    @Override
    public void run() {
        runs++;
        int thisRun = runs;
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        MpProcess.Handle s2 = MpProcess.spawn("s2", () -> {
            Channel.send(CommunicationModel.P2P, rt, new V(2));
            if (thisRun == 2) {
                throw new AssertionError("s2: fails in run 2");
            }
        });
        r.await();
        s1.await();
        s2.await();
    }
}
