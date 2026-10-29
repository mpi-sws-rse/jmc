package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/** Reviewer M2: main spawns a, awaits a, spawns b; a and b each do one non-blocking receive; main has no event. Counts [0,1]. */
public final class SpawnAfterJoin extends J1Fixture {
    /** Creates the fixture. */
    public SpawnAfterJoin() {
        super("SpawnAfterJoin", CommunicationModel.P2P, List.of("a", "b"));
    }

    private static MpProcess.Handle nb(String role) {
        return MpProcess.spawn(role, () -> Channel.tryRecv(CommunicationModel.P2P));
    }

    @Override
    public void run() {
        MpProcess.Handle a = nb("a");
        a.await();
        MpProcess.Handle b = nb("b");
        b.await();
    }
}
