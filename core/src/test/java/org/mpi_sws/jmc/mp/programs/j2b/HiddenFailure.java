package org.mpi_sws.jmc.mp.programs.j2b;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2b F4, in the summary format's shape: {@code a} blocks on an empty mailbox, {@code f} fails; main
 * awaits {@code a} first, so (F4) main blocks and never awaits {@code f}. Derived: the iteration fails
 * with "failed and was never awaited" under {@code must} and the recorder; nothing is exported.
 */
public final class HiddenFailure extends J1Fixture {

    /** Creates the fixture. */
    public HiddenFailure() {
        super("hidden-failure", CommunicationModel.P2P, List.of("a", "f"));
    }

    @Override
    public void run() {
        MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.recv(CommunicationModel.P2P));
        MpProcess.Handle f = MpProcess.spawn("f", () -> {
            throw new IllegalStateException("f fails");
        });
        a.await();
        f.await();
    }
}
