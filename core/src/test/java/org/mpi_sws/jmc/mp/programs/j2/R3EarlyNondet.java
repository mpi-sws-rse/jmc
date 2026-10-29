package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2 round 03, M1: one process p chooses among two values; with {@code early}, p returns before the
 * choice from the second run on (an instance counter), so the replay of the second graph never raises
 * the guided nondet. No receiver waits for anything. Derived: early → the run fails with replay drift
 * after one graph; not early → 2 graphs.
 */
public final class R3EarlyNondet extends J1Fixture {
    private final boolean early;
    private int runs;

    /** The early-return variant (the launcher's no-argument form). */
    public R3EarlyNondet() {
        this(true);
    }

    /**
     * The variant.
     *
     * @param early whether p skips the nondet from the second run on
     */
    public R3EarlyNondet(boolean early) {
        super("r3-early-nondet", CommunicationModel.P2P, List.of("p"));
        this.early = early;
    }

    @Override
    public void run() {
        boolean skip = early && runs++ > 0;
        MpProcess.Handle p = MpProcess.spawn("p", () -> {
            if (skip) {
                return;
            }
            Nondet.choose(List.of(1, 2));
        });
        p.await();
    }
}
