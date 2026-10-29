package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/** C3 in a run: {@code a: send(b, Who(self, main)) | b: recv()} under p2p; Tids encode as role names. */
public final class SendsTid extends J1Fixture {
    /**
     * A payload carrying two process identities.
     *
     * @param me the sender
     * @param boss main
     */
    @Tag("Who")
    public record Who(Tid me, Tid boss) implements MpMessage {}

    /** Creates the fixture. */
    public SendsTid() {
        super("sends-tid", CommunicationModel.P2P, List.of("b", "a"));
    }

    @Override
    public void run() {
        Tid main = MpProcess.self();
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(CommunicationModel.P2P));
        Tid bt = b.tid();
        MpProcess.Handle a = MpProcess.spawn("a",
                () -> Channel.send(CommunicationModel.P2P, bt, new Who(MpProcess.self(), main)));
        a.await();
        b.await();
    }
}
