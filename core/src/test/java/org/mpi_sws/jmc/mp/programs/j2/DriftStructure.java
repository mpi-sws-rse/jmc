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

/**
 * C4: S+S+R where s2 sends only on even runs (static counter): the event structure changes between
 * the explored and the replayed run, so the replay must drift.
 */
public final class DriftStructure extends J1Fixture {
    private static int counter;

    /** Creates the fixture. */
    public DriftStructure() {
        super("drift-structure", CommunicationModel.P2P, List.of("r", "s1", "s2"));
    }

    @Override
    public void run() {
        boolean even = counter++ % 2 == 0;
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        MpProcess.Handle s2 = MpProcess.spawn("s2", () -> {
            if (even) {
                Channel.send(CommunicationModel.P2P, rt, new V(2));
            }
        });
        r.await();
        s1.await();
        s2.await();
    }
}
