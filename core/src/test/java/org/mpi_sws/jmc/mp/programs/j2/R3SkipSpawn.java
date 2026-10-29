package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2 round 03, M1 (end-of-iteration check): main spawns p, which chooses among two values, in the
 * first run only; from the second run on p never exists, so its guide is never consumed and no
 * FINISH_EVENT of p is raised. Derived: replay drift at the end of the second iteration, 1 graph.
 */
public final class R3SkipSpawn extends J1Fixture {
    private int runs;

    /** Creates the fixture. */
    public R3SkipSpawn() {
        super("r3-skip-spawn", CommunicationModel.P2P, List.of("p"));
    }

    @Override
    public void run() {
        if (runs++ > 0) {
            return;
        }
        MpProcess.Handle p = MpProcess.spawn("p", () -> Nondet.choose(List.of(1, 2)));
        p.await();
    }
}
