package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/**
 * Mutated copy of {@link MainSendsAfterJoin}: main sends to b before awaiting a, so no join edge
 * leads from a's receive to b's; counts {@code [0,0]}.
 */
public final class MainSendsBeforeJoin extends J1Fixture {
    /** Creates the fixture. */
    public MainSendsBeforeJoin() {
        super("main-sends-after-join", CommunicationModel.P2P, List.of("b", "a"));
    }

    @Override
    public void run() {
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(CommunicationModel.P2P));
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.tryRecv(CommunicationModel.P2P));
        Channel.send(CommunicationModel.P2P, b.tid(), new V(2));
        a.await();
        b.await();
    }
}
