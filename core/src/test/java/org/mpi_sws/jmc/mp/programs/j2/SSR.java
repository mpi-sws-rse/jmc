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
 * Litmus S+S+R (Must Ex. 2.3, 4.1, 4.2): s1 sends V(1), s2 sends V(2) to r; r receives
 * {@code receives} times; p2p. The receiver's spawn position is a parameter: 0 = first (Ex. 4.1),
 * 1 = between the senders (Ex. 4.2), 2 = last. Its task id is then 2 + position; senders spawned
 * before it address it by that id ({@code Tid.of}) — task ids are minted in spawn order.
 * Derived counts: 1 receive → 2; 2 receives → 2 (orders 1,2 and 2,1); 1 sender only → 1.
 */
public class SSR extends J1Fixture {
    private final int position;
    private final int receives;
    private final boolean secondSender;

    /** Receiver last, one receive. */
    public SSR() {
        this(2, 1, true);
    }

    /**
     * The variant.
     *
     * @param position receiver spawn position (0, 1, 2)
     * @param receives receives by r
     * @param secondSender whether s2 sends at all
     */
    public SSR(int position, int receives, boolean secondSender) {
        super("s+s+r", CommunicationModel.P2P, order(position));
        this.position = position;
        this.receives = receives;
        this.secondSender = secondSender;
    }

    static List<String> order(int position) {
        List<String> o = new ArrayList<>(List.of("s1", "s2"));
        o.add(position, "r");
        return o;
    }

    @Override
    public void run() {
        Tid rt = Tid.of(2L + position);
        List<MpProcess.Handle> hs = new ArrayList<>();
        for (String role : order(position)) {
            switch (role) {
                case "r" -> hs.add(MpProcess.spawn("r", () -> {
                    for (int i = 0; i < receives; i++) {
                        Channel.recv(CommunicationModel.P2P);
                    }
                }));
                case "s1" -> hs.add(MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.P2P, rt, new V(1))));
                default -> hs.add(MpProcess.spawn("s2", () -> {
                    if (secondSender) {
                        Channel.send(CommunicationModel.P2P, rt, new V(2));
                    }
                }));
            }
        }
        if (hs.get(position).tid().raw() != rt.raw()) {
            throw new IllegalStateException("receiver id " + hs.get(position).tid() + " != " + rt);
        }
        for (MpProcess.Handle h : hs) {
            h.await();
        }
    }
}
