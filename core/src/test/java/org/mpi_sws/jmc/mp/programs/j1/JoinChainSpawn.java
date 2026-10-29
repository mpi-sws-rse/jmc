package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** M2 chain: main spawns a, b; awaits a, awaits b; spawns c. c is after both: counts [0,0,0,0,1,1]. */
public final class JoinChainSpawn extends J1Fixture {
    /** Creates the fixture. */
    public JoinChainSpawn() {
        super("JoinChainSpawn", CommunicationModel.P2P, List.of("a", "b", "c"));
    }

    private static MpProcess.Handle nb(String role) {
        return MpProcess.spawn(role, () -> Channel.tryRecv(CommunicationModel.P2P));
    }

    @Override
    public void run() {
        MpProcess.Handle a = nb("a");
        MpProcess.Handle b = nb("b");
        a.await();
        b.await();
        MpProcess.Handle c = nb("c");
        c.await();
    }
}
