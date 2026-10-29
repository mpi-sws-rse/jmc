package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** Mutated SpawnAfterJoin: b is spawned before main awaits a. Counts [0,0]. */
public final class SpawnBeforeJoin extends J1Fixture {
    /** Creates the fixture. */
    public SpawnBeforeJoin() {
        super("SpawnBeforeJoin", CommunicationModel.P2P, List.of("a", "b"));
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
    }
}
