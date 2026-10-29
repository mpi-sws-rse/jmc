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
 * C4: S+S+R where s1's sent value comes from a static counter (a different value on every run): not
 * deterministic, so the replay of iteration 1 must drift ("must: replay drift").
 */
public final class DriftValue extends J1Fixture {
    private static int counter;

    /** Creates the fixture. */
    public DriftValue() {
        super("drift-value", CommunicationModel.P2P, List.of("r", "s1", "s2"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        int v = counter++;
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(v)));
        MpProcess.Handle s2 = MpProcess.spawn("s2", () -> Channel.send(CommunicationModel.P2P, rt, new V(100)));
        r.await();
        s1.await();
        s2.await();
    }
}
