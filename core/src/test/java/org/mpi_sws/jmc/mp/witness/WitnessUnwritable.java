package org.mpi_sws.jmc.mp.witness;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * WITNESS tester fixture (P2P) whose summaries encode but whose executions cannot be written as a
 * witness: "c" sends A(x), x a nondet of 2, to the visible "srv"; the invisible "x" sends to the
 * invisible "z" a value the encoder refuses — argument {@code untagged}: a record without
 * {@code @Tag}; argument {@code daemon}: D(the daemon's Tid), a payload naming a daemon (the program
 * then also spawns a daemon that does nothing). The summary encodes only srv's events.
 */
public final class WitnessUnwritable implements MpProgram {
    @Tag("A") record A(int v) implements MpMessage {}
    record Untagged(int v) implements MpMessage {}
    @Tag("D") record D(Tid who) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;
    private final boolean daemon;

    public WitnessUnwritable(String... args) {
        this.daemon = args[0].trim().equals("daemon");
    }

    @Override
    public void run() {
        Tid d = daemon ? MpProcess.spawnDaemon(() -> {}).tid() : null;
        MpProcess.Handle srv = MpProcess.spawn("srv", () -> Channel.recv(M, A.class));
        MpProcess.Handle z = MpProcess.spawn("z", () -> Channel.recv(M));
        MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(M, srv.tid(), new A(Nondet.chooseIndex(2))));
        MpProcess.Handle x = MpProcess.spawn("x",
                () -> Channel.send(M, z.tid(), daemon ? new D(d) : new Untagged(1)));
        srv.await();
        z.await();
        c.await();
        x.await();
    }

    @Override public String programName() { return "witness-unwritable"; }
    @Override public String size() { return daemon ? "daemon" : "untagged"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("srv", "z", "c", "x"); }
    @Override public List<String> visibleRoles() { return List.of("srv"); }
}
