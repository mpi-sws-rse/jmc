package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * J2 round 02: N senders, s_i sends V(i) to r; r receives N times, unguarded. The receiver is spawned
 * first (task 2) or last (task N + 2, addressed by its predictable id); model p2p or asyn. Derived:
 * N! graphs in every variant (distinct senders, so neither p2p nor asyn orders them).
 */
public final class NSRNVariant extends J1Fixture {
    private final int n;
    private final boolean receiverLast;
    private final CommunicationModel model;

    /**
     * The variant.
     *
     * @param n senders
     * @param receiverLast whether r is spawned after the senders
     * @param model the model
     */
    public NSRNVariant(int n, boolean receiverLast, CommunicationModel model) {
        super("ns+rn-variant", model, roles(n, receiverLast));
        this.n = n;
        this.receiverLast = receiverLast;
        this.model = model;
    }

    static List<String> roles(int n, boolean receiverLast) {
        List<String> r = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            r.add("s" + i);
        }
        r.add(receiverLast ? r.size() : 0, "r");
        return r;
    }

    @Override
    public void run() {
        Tid rt = Tid.of(receiverLast ? n + 2L : 2L);
        List<MpProcess.Handle> hs = new ArrayList<>();
        for (String role : roles(n, receiverLast)) {
            if (role.equals("r")) {
                hs.add(MpProcess.spawn("r", () -> {
                    for (int k = 0; k < n; k++) {
                        Channel.recv(model);
                    }
                }));
            } else {
                int v = Integer.parseInt(role.substring(1));
                hs.add(MpProcess.spawn(role, () -> Channel.send(model, rt, new V(v))));
            }
        }
        long rid = hs.get(receiverLast ? n : 0).tid().raw();
        if (rid != rt.raw()) {
            throw new IllegalStateException("receiver id " + rid + " != " + rt);
        }
        for (MpProcess.Handle h : hs) {
            h.await();
        }
    }
}
