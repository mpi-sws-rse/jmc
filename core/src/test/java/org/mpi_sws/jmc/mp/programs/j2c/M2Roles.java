package org.mpi_sws.jmc.mp.programs.j2c;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2c round 03 (review M1 and M2) export fixtures.
 * <ul>
 *   <li>{@code named_main} / {@code named_tilde}: declares {@code [a]}; spawns a and a second process
 *       named {@code "main"} / {@code "~x"}: refused as undeclared;</li>
 *   <li>{@code await_blocked_finished} / {@code await_blocked_recv}: declares {@code [p]}; a daemon
 *       (finishing / blocked on a receive) and p, blocked on a receive; main awaits p and blocks.
 *       Exported: one summary, p blocked with an empty word.</li>
 * </ul>
 */
public final class M2Roles extends J1Fixture {

    private final String variant;

    /** The {@code named_main} variant. */
    public M2Roles() {
        this("named_main");
    }

    /**
     * A variant.
     *
     * @param variant named_main, named_tilde, await_blocked_finished or await_blocked_recv
     */
    public M2Roles(String variant) {
        super("m2-roles", CommunicationModel.P2P, variant.startsWith("await") ? List.of("p") : List.of("a"));
        this.variant = variant;
    }

    @Override
    public void run() {
        if (variant.startsWith("await")) {
            if (variant.endsWith("finished")) {
                MpProcess.spawnDaemon(() -> { });
            } else {
                MpProcess.spawnDaemon(() -> Channel.recv(CommunicationModel.P2P));
            }
            MpProcess.spawn("p", () -> Channel.recv(CommunicationModel.P2P)).await();
            return;
        }
        MpProcess.Handle a = MpProcess.spawn("a", () -> { });
        MpProcess.Handle x = MpProcess.spawn(variant.equals("named_main") ? "main" : "~x", () -> { });
        a.await();
        x.await();
    }
}
