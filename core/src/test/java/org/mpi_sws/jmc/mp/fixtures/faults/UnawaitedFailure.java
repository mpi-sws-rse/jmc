package org.mpi_sws.jmc.mp.fixtures.faults;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * A process that fails and is never awaited. Main spawns {@code b} and {@code a} and awaits only
 * {@code b}; {@code a} sends {@code V(1)} to {@code b} and then fails an assertion with the text
 * {@code "a's own assertion"}; {@code b} receives the message. One execution, model {@code p2p},
 * exportable (roles {@code b}, {@code a}, both visible).
 */
public final class UnawaitedFailure implements MpProgram {

    /** The message. */
    @Tag("V")
    public record V(int x) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    /** Creates the fixture. */
    public UnawaitedFailure() {
    }

    @Override
    public String programName() {
        return "unawaited-failure";
    }

    @Override
    public String size() {
        return "";
    }

    @Override
    public CommunicationModel model() {
        return M;
    }

    @Override
    public List<String> rolesInSpawnOrder() {
        return List.of("b", "a");
    }

    @Override
    public void run() {
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(M, V.class));
        MpProcess.spawn("a", () -> {
            Channel.send(M, b.tid(), new V(1));
            throw new AssertionError("a's own assertion");
        });
        b.await();
    }
}
