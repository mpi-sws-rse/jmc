package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;

import java.util.Set;

/**
 * J8 test 7 fixture (counting mode): a value outside §2.2 (rev 7/8). s sends {@code Plain(0)} at nondet
 * index 0 (iteration 0, the root) and, at index 1 (the one stack entry, item 0 at K = 1), a record whose
 * field is outside the contract: argument {@code set} ({@code Set.of(1)}), {@code double} (1.5) or
 * {@code array} ({@code int[]{1}}).
 */
public final class OutsidePayload implements Runnable {
    record Plain(int v) implements MpMessage {}

    record Bag(Object x) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;
    private final String kind;

    public OutsidePayload(String... args) {
        this.kind = args[0].trim();
    }

    private Object outside() {
        return switch (kind) {
            case "set" -> Set.of(1);
            case "double" -> 1.5;
            case "array" -> new int[] {1};
            default -> throw new IllegalArgumentException("kind set|double|array, not " + kind);
        };
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(M));
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(M, r.tid(),
                Nondet.chooseIndex(2) == 0 ? new Plain(0) : new Bag(outside())));
        r.await();
        s.await();
    }
}
