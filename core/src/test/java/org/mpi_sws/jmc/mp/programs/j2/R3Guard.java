package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;
import java.util.function.Predicate;

/**
 * J2 round 03, M2: receives with type-test guards that accept {@code V} in the first run and, with
 * {@code switching}, only {@code W} from the second run on (an instance counter); p2p.
 *
 * <ul>
 *   <li>{@code nb}: s sends V(1) then the token V(0) to r; r first receives the token (blocking, guard
 *       {@code x == 0}), then does a non-blocking receive (guard {@code V x == 1}, or W). First run: r
 *       parks, s sends both, r reads the token, then reads ⊥ and the alternative "r reads V(1)" is pushed
 *       (forward). Replay of it: V(1) is raised before r's non-blocking receive (the token orders it),
 *       so the committed V(1) is checked at once against the W guard.</li>
 *   <li>{@code parked}: r is spawned first and does a blocking receive; s1, s2 send V(1), V(2). First
 *       run: r parks, both sends, r re-issued reads V(1), "r reads V(2)" is pushed. Replay of it: r's
 *       receive is raised first and parks for s2's send; the W guard is checked when V(2) arrives.</li>
 * </ul>
 * Derived: switching → replay drift after one graph; constant → 2 graphs.
 */
public final class R3Guard extends J1Fixture {

    /**
     * A second payload type; encoded {@code {"t":"W","f":[x]}}.
     *
     * @param x the integer carried
     */
    @Tag("W")
    public record W(int x) implements MpMessage {}

    private final boolean parkedPath;
    private final boolean switching;
    private int runs;

    /** The non-blocking, switching variant. */
    public R3Guard() {
        this(false, true);
    }

    /**
     * The variant.
     *
     * @param parkedPath the {@code parked} shape (else {@code nb})
     * @param switching whether the guard accepts only W from the second run on
     */
    public R3Guard(boolean parkedPath, boolean switching) {
        super(parkedPath ? "r3-guard-parked" : "r3-guard-nb", CommunicationModel.P2P,
                parkedPath ? List.of("r", "s1", "s2") : List.of("r", "s"));
        this.parkedPath = parkedPath;
        this.switching = switching;
    }

    @Override
    public void run() {
        boolean onlyW = switching && runs++ > 0;
        Predicate<MpMessage> guard = onlyW ? (MpMessage m) -> m instanceof W : (MpMessage m) -> m instanceof V;
        if (parkedPath) {
            MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P, guard));
            Tid rt = r.tid();
            MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
            MpProcess.Handle s2 = MpProcess.spawn("s2", () -> Channel.send(CommunicationModel.P2P, rt, new V(2)));
            r.await();
            s1.await();
            s2.await();
            return;
        }
        Predicate<MpMessage> nbGuard = onlyW ? (MpMessage m) -> m instanceof W
                : (MpMessage m) -> m instanceof V v && v.x() == 1;
        MpProcess.Handle r = MpProcess.spawn("r", () -> {
            Channel.recv(CommunicationModel.P2P, (MpMessage m) -> m instanceof V v && v.x() == 0);
            Channel.tryRecv(CommunicationModel.P2P, nbGuard);
        });
        Tid rt = r.tid();
        MpProcess.Handle s = MpProcess.spawn("s", () -> {
            Channel.send(CommunicationModel.P2P, rt, new V(1));
            Channel.send(CommunicationModel.P2P, rt, new V(0));
        });
        r.await();
        s.await();
    }
}
