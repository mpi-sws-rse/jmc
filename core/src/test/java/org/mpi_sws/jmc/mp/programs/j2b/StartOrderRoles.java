package org.mpi_sws.jmc.mp.programs.j2b;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2b round 03: export roles follow main's START order (the format's spawn order), not the creation
 * order. main creates the client first and the server second, then starts the server first: roles
 * {@code ["srv", "cli"]}. srv sends V(1) to cli, cli receives it. Derived summary:
 * {@code cli: [{r:V(1)}], srv: [{s:V(1)}]}, counts {@code [1, 0]}.
 */
public final class StartOrderRoles extends J1Fixture {

    /** Creates the fixture. */
    public StartOrderRoles() {
        super("start-order-roles", CommunicationModel.P2P, List.of("srv", "cli"));
    }

    @Override
    public void run() {
        MpThread cli = new MpThread(() -> Channel.recv(CommunicationModel.P2P), "cli");
        Tid ct = Tid.of(cli.getTaskId());
        MpThread srv = new MpThread(() -> Channel.send(CommunicationModel.P2P, ct, new V(1)), "srv");
        srv.start();
        cli.start();
        try {
            srv.join1();
            cli.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
