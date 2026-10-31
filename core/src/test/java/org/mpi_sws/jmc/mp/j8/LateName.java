package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * J8 late-name fixture (counting mode): main spawns a and b, each sends V to main; main receives one
 * value and spawns c (creation path m/2) only if it came from b. Iteration 0 reads a's V (least
 * option), so the name m/2 first appears in the item that re-points main's receive to b.
 */
public final class LateName implements Runnable {
    record V(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        Tid main = MpProcess.self();
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(M, main, new V(1)));
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(M, main, new V(2)));
        V got = Channel.recv(M, V.class);
        MpProcess.Handle c = got.v() == 2 ? MpProcess.spawn("c", () -> { }) : null;
        a.await();
        b.await();
        if (c != null) {
            c.await();
        }
    }
}
