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
 * C3: s1 chooses x among {@code k} values (1..k) and sends V(x) to r; optionally s2 sends V(9);
 * r receives once; p2p. Derived: k graphs alone; with s2, 2k (r reads s1's — k ways — or s2's, with
 * s1's unread send carrying one of k values).
 */
public final class NondetK extends J1Fixture {
    private final int k;
    private final boolean withS2;

    /** k = 3 with s2. */
    public NondetK() {
        this(3, true);
    }

    /**
     * The variant.
     *
     * @param k option count
     * @param withS2 whether s2 sends
     */
    public NondetK(int k, boolean withS2) {
        super("nondet-k", CommunicationModel.P2P, List.of("r", "s1", "s2"));
        this.k = k;
        this.withS2 = withS2;
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
        Tid rt = r.tid();
        List<Integer> opts = new ArrayList<>();
        for (int i = 1; i <= k; i++) {
            opts.add(i);
        }
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> {
            int x = Nondet.choose(opts);
            Channel.send(CommunicationModel.P2P, rt, new V(x));
        });
        MpProcess.Handle s2 = MpProcess.spawn("s2", () -> {
            if (withS2) {
                Channel.send(CommunicationModel.P2P, rt, new V(9));
            }
        });
        r.await();
        s1.await();
        s2.await();
    }
}
