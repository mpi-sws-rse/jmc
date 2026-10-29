package org.mpi_sws.jmc.mp.programs.j2c;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2c ruling 4: roles {@code ["b", "a"]} (spawn order); a sends V(1) to b, b receives. With daemons,
 * main spawns a daemon D1 (receives once) before b and a daemon D2 (sends to D1) between b and a. Daemons have no
 * role: derived, the exported summaries equal those of the program without daemons. Q-J2c-1
 * variant ({@code payload}): a's payload names D1, which has no role: refused.
 */
public final class DaemonRoles extends J1Fixture {

    /**
     * A payload naming a task.
     *
     * @param t the task
     */
    @org.mpi_sws.jmc.mp.summary.Tag("Req")
    public record Req(Tid t) implements org.mpi_sws.jmc.mp.events.MpMessage {}

    private final boolean withDaemons;
    private final boolean payloadNamesDaemon;

    /** With daemons. */
    public DaemonRoles() {
        this(true, false);
    }

    /**
     * The variant.
     *
     * @param withDaemons whether the daemons are spawned
     * @param payloadNamesDaemon whether a's payload names D1 (Q-J2c-1)
     */
    public DaemonRoles(boolean withDaemons, boolean payloadNamesDaemon) {
        super("daemon-roles", CommunicationModel.P2P, List.of("b", "a"));
        this.withDaemons = withDaemons || payloadNamesDaemon;
        this.payloadNamesDaemon = payloadNamesDaemon;
    }

    @Override
    public void run() {
        Tid d1 = withDaemons ? MpProcess.spawnDaemon(() -> Channel.recv(CommunicationModel.P2P)).tid() : null;
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(CommunicationModel.P2P));
        Tid bt = b.tid();
        if (withDaemons) {
            MpProcess.spawnDaemon(() -> Channel.send(CommunicationModel.P2P, d1, new V(9)));
        }
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(CommunicationModel.P2P, bt,
                payloadNamesDaemon ? new Req(d1) : new V(1)));
        a.await();
        b.await();
    }
}
