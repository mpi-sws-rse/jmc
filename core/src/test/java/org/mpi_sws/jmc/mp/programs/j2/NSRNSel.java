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
 * Litmus NS+RN-SEL (state.md §3.4; Must Ex. 2.7): N senders, s_i sends V(i) to r; r receives N times,
 * the k-th receive accepting only V(k) (selective receive; a value guard, fine for a count test); p2p.
 * Derived: 1 graph. Unguarded (mutant): N! graphs.
 */
public final class NSRNSel extends J1Fixture {
    private final int n;
    private final boolean guarded;

    /** N = 3, guarded. */
    public NSRNSel() {
        this(3, true);
    }

    /**
     * The variant.
     *
     * @param n senders
     * @param guarded whether receives are selective
     */
    public NSRNSel(int n, boolean guarded) {
        super("ns+rn-sel", CommunicationModel.P2P, NSR.roles(n));
        this.n = n;
        this.guarded = guarded;
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> {
            for (int k = 1; k <= n; k++) {
                int want = k;
                if (guarded) {
                    Channel.recv(CommunicationModel.P2P, (MpMessage m) -> m instanceof V v && v.x() == want);
                } else {
                    Channel.recv(CommunicationModel.P2P);
                }
            }
        });
        Tid rt = r.tid();
        List<MpProcess.Handle> hs = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            int v = i;
            hs.add(MpProcess.spawn("s" + i, () -> Channel.send(CommunicationModel.P2P, rt, new V(v))));
        }
        r.await();
        for (MpProcess.Handle h : hs) {
            h.await();
        }
    }
}
