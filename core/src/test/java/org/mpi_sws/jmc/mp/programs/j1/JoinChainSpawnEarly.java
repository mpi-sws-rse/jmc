package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** Mutated JoinChainSpawn: c is spawned after awaiting a but before awaiting b. Counts [0,0,0,0,1,0]. */
public final class JoinChainSpawnEarly extends J1Fixture {
    /** Creates the fixture. */
    public JoinChainSpawnEarly() {
        super("JoinChainSpawnEarly", CommunicationModel.P2P, List.of("a", "b", "c"));
    }

    private static MpProcess.Handle nb(String role) {
        return MpProcess.spawn(role, () -> Channel.tryRecv(CommunicationModel.P2P));
    }

    @Override
    public void run() {
        MpProcess.Handle a = nb("a");
        MpProcess.Handle b = nb("b");
        a.await();
        MpProcess.Handle c = nb("c");
        b.await();
        c.await();
    }
}
