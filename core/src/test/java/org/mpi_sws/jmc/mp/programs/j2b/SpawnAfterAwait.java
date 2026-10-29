package org.mpi_sws.jmc.mp.programs.j2b;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2b F3 under export: the only way a spawn edge could carry an event into a summary is a spawn
 * after main's await ({@code a} receives, main awaits it, then spawns {@code s} sending to {@code a}),
 * which the format's shape refuses. Derived: the {@code must} export fails with "spawned after main's
 * first await".
 */
public final class SpawnAfterAwait extends J1Fixture {

    /** Creates the fixture. */
    public SpawnAfterAwait() {
        super("spawn-after-await", CommunicationModel.P2P, List.of("a", "s"));
    }

    @Override
    public void run() {
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.tryRecv(CommunicationModel.P2P));
        Tid at = a.tid();
        a.await();
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(CommunicationModel.P2P, at, new V(1)));
        s.await();
    }
}
