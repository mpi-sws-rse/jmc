package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Must Ex. 4.3 / §2.4 S+S+R-BR under p2p (tasks 2..6 = T1..T5):
 * {@code T1: send(T1,0); recv() | T2: send(T4,1) | T3: send(T4,2) | T4: recv() | T5: send(T1,42)}.
 * Derived: T4 reads 1 or 2, T1 reads 0 or 42 → 4 complete graphs, none blocked; graph D (T1 reads 42)
 * is reached by a backward revisit from T5's send, admitted from exactly one of the two T4 choices.
 */
public final class SSRBR extends J1Fixture {
    /** Creates the fixture. */
    public SSRBR() {
        super("s+s+r-br", CommunicationModel.P2P, List.of("t1", "t2", "t3", "t4", "t5"));
    }

    @Override
    public void run() {
        Tid t1 = Tid.of(2);
        Tid t4 = Tid.of(5);
        List<MpProcess.Handle> hs = new ArrayList<>();
        hs.add(MpProcess.spawn("t1", () -> {
            Channel.send(CommunicationModel.P2P, MpProcess.self(), new V(0));
            Channel.recv(CommunicationModel.P2P);
        }));
        hs.add(MpProcess.spawn("t2", () -> Channel.send(CommunicationModel.P2P, t4, new V(1))));
        hs.add(MpProcess.spawn("t3", () -> Channel.send(CommunicationModel.P2P, t4, new V(2))));
        hs.add(MpProcess.spawn("t4", () -> Channel.recv(CommunicationModel.P2P)));
        hs.add(MpProcess.spawn("t5", () -> Channel.send(CommunicationModel.P2P, t1, new V(42))));
        if (hs.get(0).tid().raw() != 2 || hs.get(3).tid().raw() != 5) {
            throw new IllegalStateException("task ids");
        }
        for (MpProcess.Handle h : hs) {
            h.await();
        }
    }
}
