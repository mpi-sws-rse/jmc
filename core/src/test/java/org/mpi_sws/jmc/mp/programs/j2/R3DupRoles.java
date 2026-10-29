package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2 round 03, M4: roles {@code ["r","hidden","hidden"]} with only {@code r} visible; each hidden
 * process sends one message to r, which receives twice. Derived: the exporting constructor refuses
 * the roles ("roles are not distinct") before any run.
 */
public final class R3DupRoles extends J1Fixture {

    /** Creates the fixture. */
    public R3DupRoles() {
        super("r3-dup-roles", CommunicationModel.P2P, List.of("r", "hidden", "hidden"));
    }

    @Override
    public List<String> visibleRoles() {
        return List.of("r");
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> {
            Channel.recv(CommunicationModel.P2P);
            Channel.recv(CommunicationModel.P2P);
        });
        Tid rt = r.tid();
        MpProcess.Handle h1 = MpProcess.spawn("hidden", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        MpProcess.Handle h2 = MpProcess.spawn("hidden", () -> Channel.send(CommunicationModel.P2P, rt, new V(2)));
        r.await();
        h1.await();
        h2.await();
    }
}
