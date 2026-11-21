package org.mpi_sws.jmc.mp.fixtures.mutants;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;

/**
 * Fixture: main spawns a process that waits for a message nobody sends and
 * returns without awaiting it; {@code mp-random} refuses this (abandoned process), so the checker
 * fails.
 */
public final class AbandonsProcess implements Runnable {
    /** Creates the fixture. */
    public AbandonsProcess() {}

    @Override
    public void run() {
        MpProcess.spawn("t1", () -> Channel.recv(CommunicationModel.P2P));
    }
}
