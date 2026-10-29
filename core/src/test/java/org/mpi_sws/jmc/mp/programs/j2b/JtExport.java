package org.mpi_sws.jmc.mp.programs.j2b;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;
import org.mpi_sws.jmc.mp.programs.seq.JmcThreadProcess;

import java.util.List;

/**
 * J2b-F4 regression in export mode: the summary format's shape (main spawns two processes, awaits both,
 * no message event of its own), but the processes are {@code JmcThread}s, not {@code MpThread}s.
 * Derived: under {@code must} the first START (task 2) records "task 2 was not created by MpThread;
 * the Must-API spawns only MpThreads" and the iteration fails; nothing is exported.
 */
public final class JtExport extends J1Fixture {

    /** Creates the fixture. */
    public JtExport() {
        super("jt-export", CommunicationModel.P2P, List.of("a", "b"));
    }

    @Override
    public void run() {
        JmcThreadProcess.Handle a = JmcThreadProcess.spawn("a", () -> Channel.tryRecv(CommunicationModel.P2P));
        Tid at = a.tid();
        JmcThreadProcess.Handle b =
                JmcThreadProcess.spawn("b", () -> Channel.send(CommunicationModel.P2P, at, new V(1)));
        a.await();
        b.await();
    }
}
