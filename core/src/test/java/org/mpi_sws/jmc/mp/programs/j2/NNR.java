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

/** Litmus NNR (Must Ex. 2.5): N processes each do one non-blocking receive, no sends; asyn. 1 graph. */
public final class NNR extends J1Fixture {
    private final int n;

    /** N = 3. */
    public NNR() {
        this(3);
    }

    /**
     * NNR with N processes.
     *
     * @param n the number of processes
     */
    public NNR(int n) {
        super("nnr", CommunicationModel.ASYN, rolesOf(n));
        this.n = n;
    }

    static List<String> rolesOf(int n) {
        List<String> r = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            r.add("p" + i);
        }
        return r;
    }

    @Override
    public void run() {
        List<MpProcess.Handle> hs = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            hs.add(MpProcess.spawn("p" + i, () -> Channel.tryRecv(CommunicationModel.ASYN)));
        }
        for (MpProcess.Handle h : hs) {
            h.await();
        }
    }
}
