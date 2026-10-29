package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2 round 03, M1 variant: p sends V(1) to q (q never receives), then chooses among two values; with
 * {@code early}, p skips the choice from the second run on, so it finishes after consuming one of its
 * two guide events. Derived: early → replay drift after one graph; not early → 2 graphs.
 */
public final class R3SendThenNondet extends J1Fixture {
    private final boolean early;
    private int runs;

    /** The early-return variant. */
    public R3SendThenNondet() {
        this(true);
    }

    /**
     * The variant.
     *
     * @param early whether p skips the nondet from the second run on
     */
    public R3SendThenNondet(boolean early) {
        super("r3-send-then-nondet", CommunicationModel.P2P, List.of("q", "p"));
        this.early = early;
    }

    @Override
    public void run() {
        boolean skip = early && runs++ > 0;
        MpProcess.Handle q = MpProcess.spawn("q", () -> { });
        Tid qt = q.tid();
        MpProcess.Handle p = MpProcess.spawn("p", () -> {
            Channel.send(CommunicationModel.P2P, qt, new V(1));
            if (skip) {
                return;
            }
            Nondet.choose(List.of(1, 2));
        });
        q.await();
        p.await();
    }
}
