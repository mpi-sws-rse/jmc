package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/** C2(c): {@code r: recv() | s: x := choose{5, 9}; send(r, V(x))} under p2p. */
public final class NondetSend extends J1Fixture {
    /** Creates the fixture. */
    public NondetSend() {
        super("nondet-send", CommunicationModel.P2P, List.of("r", "s"));
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        MpProcess.Handle s = MpProcess.spawn("s", () -> {
            int x = Nondet.choose(List.of(5, 9));
            Channel.send(CommunicationModel.P2P, rt, new V(x));
        });
        r.await();
        s.await();
    }
}
