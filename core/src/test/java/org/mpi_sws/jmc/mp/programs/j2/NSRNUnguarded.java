package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.mp.events.CommunicationModel;

/** NS+RN with N = 3 and unguarded receives (three distinct senders, three receives; p2p): 3! = 6 graphs. */
public final class NSRNUnguarded extends org.mpi_sws.jmc.mp.programs.j1.J1Fixture {
    private final NSRNSel inner = new NSRNSel(3, false);

    /** Creates the fixture. */
    public NSRNUnguarded() {
        super("ns+rn-unguarded", CommunicationModel.P2P, NSR.roles(3));
    }

    @Override
    public void run() {
        inner.run();
    }
}
