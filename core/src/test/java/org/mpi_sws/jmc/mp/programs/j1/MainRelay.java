package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.List;

/**
 * C2(d): main relays. {@code a: send(main, V(1)) | b: recv() | main: spawn a, b; m := recv(); send(b, m); await a, b}
 * under p2p. Main is invisible; b's receive is porf-after a's send only through main's events.
 */
public final class MainRelay extends J1Fixture {
    /** Creates the fixture. */
    public MainRelay() {
        super("main-relay", CommunicationModel.P2P, List.of("a", "b"));
    }

    @Override
    public void run() {
        Tid main = MpProcess.self();
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(CommunicationModel.P2P, main, new V(1)));
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(CommunicationModel.P2P));
        MpMessage m = Channel.recv(CommunicationModel.P2P);
        Channel.send(CommunicationModel.P2P, b.tid(), m);
        a.await();
        b.await();
    }
}
