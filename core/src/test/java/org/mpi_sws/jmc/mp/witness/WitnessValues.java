package org.mpi_sws.jmc.mp.witness;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;
import java.util.Set;

/**
 * WITNESS tester fixture (P2P), one execution: "c" sends P(main's Tid) to main (never received), then
 * W(In([3, 1], {"b", "a"}), srv's Tid, true, RED) to "srv", which receives it. Visible: srv and c.
 */
public final class WitnessValues implements MpProgram {
    enum Color { RED, BLUE }

    @Tag("P") record P(Tid who) implements MpMessage {}
    @Tag("In") record In(List<Integer> xs, Set<String> names) {}
    @Tag("W") record W(In in, Tid to, boolean flag, Color color) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        Tid main = MpProcess.self();
        MpProcess.Handle srv = MpProcess.spawn("srv", () -> Channel.recv(M, W.class));
        MpProcess.Handle c = MpProcess.spawn("c", () -> {
            Channel.send(M, main, new P(main));
            Channel.send(M, srv.tid(), new W(new In(List.of(3, 1), Set.of("b", "a")), srv.tid(), true, Color.RED));
        });
        srv.await();
        c.await();
    }

    @Override public String programName() { return "witness-values"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("srv", "c"); }
}
