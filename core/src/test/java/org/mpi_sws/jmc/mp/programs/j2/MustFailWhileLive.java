package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * Leftover-thread probe (J2 round 02): {@code r: recv(); recv() | f: assert false | s1: send(r, V1)};
 * main awaits f first, so the failure is rethrown to main while r (parked on its second receive,
 * which nobody serves) and possibly s1 are still live. Look for strategy logs after {@code FAILED:}.
 */
public final class MustFailWhileLive extends J1Fixture {
    /** Creates the fixture. */
    public MustFailWhileLive() {
        super("must-fail-while-live", CommunicationModel.P2P, List.of("r", "f", "s1"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> {
            Channel.recv(CommunicationModel.P2P);
            Channel.recv(CommunicationModel.P2P);
        });
        Tid rt = r.tid();
        MpProcess.Handle f = MpProcess.spawn("f", () -> {
            throw new AssertionError("f: fails at once");
        });
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(1)));
        f.await();
        s1.await();
        r.await();
    }
}
