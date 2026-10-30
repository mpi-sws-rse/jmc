package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * J8 normalisation-failure fixture (reconstruction failure, MustStrategy.mapTids catch, counting
 * mode): {@code Who}'s compact constructor refuses a Tid without a role. The program builds it from
 * {@code Handle.tid()}, which carries the spawn role "r"; the strategy's normalised copy
 * {@code Tid.of(stableId)} has the empty role, so the reflective constructor throws and the
 * sequential strategy keeps the original (runtime-numbered) payload. s sends Plain at nondet
 * index 0 (iteration 0, the root) and Who at index 1 (the one stack entry, item 0 at K = 1).
 */
public final class ReconstructionFailure implements Runnable {
    record Who(Tid t) implements MpMessage {
        Who {
            if (t.role().isEmpty()) {
                throw new IllegalArgumentException("Who needs a role-carrying Tid");
            }
        }
    }

    record Plain(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(M));
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(M, r.tid(),
                Nondet.chooseIndex(2) == 0 ? new Plain(0) : new Who(r.tid())));
        r.await();
        s.await();
    }
}
