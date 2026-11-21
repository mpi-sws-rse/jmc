package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;

/**
 * J8 revision 8 M1 run-level fixture (counting mode): s sends {@code Str("ok")} at nondet index 0
 * (iteration 0, the root) and {@code Str} holding the lone char U+D800 (an unpaired high surrogate) at index 1 (the one
 * stack entry, item 0 at K = 1) to r.
 */
public final class SurrogatePayload implements Runnable {
    record Str(String s) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(M, Str.class));
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(M, r.tid(),
                new Str(Nondet.chooseIndex(2) == 0 ? "ok" : String.valueOf((char) 0xD800))));
        r.await();
        s.await();
    }
}
