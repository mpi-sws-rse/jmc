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
 * Litmus NS+R (state.md §3.4; Must Ex. 2.9): N senders each send V(i) to r; r receives once; p2p.
 * Distinct complete graphs: N (r reads one of N sends). The receiver is spawned first (task 2).
 */
public class NSR extends J1Fixture {
    private final int n;

    /** NS+R with N = 4 (the launcher's no-argument form). */
    public NSR() {
        this(4);
    }

    /**
     * NS+R with N senders.
     *
     * @param n the number of senders
     */
    public NSR(int n) {
        super("ns+r", CommunicationModel.P2P, roles(n));
        this.n = n;
    }

    static List<String> roles(int n) {
        List<String> r = new ArrayList<>(List.of("r"));
        for (int i = 1; i <= n; i++) {
            r.add("s" + i);
        }
        return r;
    }

    @Override
    public String size() {
        return "N=" + n;
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(CommunicationModel.P2P));
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
