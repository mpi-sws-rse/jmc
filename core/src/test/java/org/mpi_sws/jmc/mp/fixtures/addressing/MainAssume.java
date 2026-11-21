package org.mpi_sws.jmc.mp.fixtures.addressing;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.fixtures.export.J1Fixture;

import java.util.List;

/**
 * Fixture: main's failed assume blocks main (sequential, export and a split boundary around it): roles
 * {@code s1}, {@code s2}, {@code s3}, {@code r};
 * main creates r first ({@code MpProcess.create}, m/0), spawns s1, s2, s3 (m/1..m/3), then starts r; each
 * {@code sk} sends {@code V(k)} to r, which receives once and records the value in a field (program state, not a
 * modeled event). r runs first and parks; its receive is re-issued with three options once the senders are done.
 * <ul>
 *   <li>{@code late}: main awaits all four, then {@code MpProcess.assume(seen == 1)}. Derived: 3 executions
 *       (r reads V(1), V(2) or V(3)); 1 complete, 2 blocked (main's Block); 3 summaries, every process done.</li>
 *   <li>{@code early}: main calls {@code MpProcess.assume(false)} right after the spawns, before any await.
 *       Derived: main parks, the processes run to their ends, main is blocked: 3 executions, all blocked; no
 *       process is awaited, which the export accepts for this termination as for {@code mainBlockedOnAwait}.</li>
 * </ul>
 * Main performs no send or receive, so both are valid export shapes. With {@code --split} and {@code --k 1} the
 * root's first iteration leaves two stack entries (r's other two options), so the boundary is crossed after it.
 *
 * <p>Arguments ({@code MainAssume:<variant>}): {@code late} (the default) or {@code early}.
 */
public final class MainAssume extends J1Fixture {

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final boolean early;
    private volatile int seen;

    /** The {@code late} variant. */
    public MainAssume() {
        this("late");
    }

    /**
     * @param args the variant: {@code late} or {@code early}
     */
    public MainAssume(String... args) {
        super("p4-main-assume", M, List.of("s1", "s2", "s3", "r"));
        String v = args.length == 0 ? "late" : args[0].trim();
        if (!List.of("late", "early").contains(v)) {
            throw new IllegalArgumentException("unknown variant " + v);
        }
        this.early = v.equals("early");
    }

    @Override
    public void run() {
        seen = 0;
        MpProcess.Pending r = MpProcess.create("r");
        Tid rt = r.tid();
        MpProcess.Handle h1 = MpProcess.spawn("s1", () -> Channel.send(M, rt, new V(1)));
        MpProcess.Handle h2 = MpProcess.spawn("s2", () -> Channel.send(M, rt, new V(2)));
        MpProcess.Handle h3 = MpProcess.spawn("s3", () -> Channel.send(M, rt, new V(3)));
        MpProcess.Handle hr = r.start(() -> seen = Channel.recv(M, V.class).x());
        if (early) {
            MpProcess.assume(false);
            throw new AssertionError("a statement after main's failed assume ran");
        }
        h1.await();
        h2.await();
        h3.await();
        hr.await();
        MpProcess.assume(seen == 1);
    }
}
