package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * Small programs from TraceForge {@code tests/coverage.rs} ({@code cover_basic_1},
 * {@code cover_basic_2}; {@code cover!} is not an event and is dropped) and {@code tests/nondet.rs}
 * ({@code test_nondet}: two choices over {@code 4..6} in main).
 *
 * <p>Arguments: {@code <cover_basic_1|cover_basic_2|test_nondet>}.
 */
public final class SmallTests implements Runnable {

    /** An integer payload. */
    record I(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;

    /**
     * @param args test name
     */
    public SmallTests(String... args) {
        this.name = args[0].trim();
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (name) {
            case "cover_basic_1" -> {
                Tid h = sp.spawn(() -> Channel.recv(M));
                if (Nondet.chooseBoolean()) {
                    Channel.send(M, h, new I(42));
                } else {
                    Channel.send(M, h, new I(11));
                }
            }
            case "cover_basic_2" -> {
                Tid h = sp.spawn(() -> Channel.tryRecv(M));
                int v = Nondet.chooseIndex(4);
                if (v == 0) {
                    Channel.send(M, h, new I(42));
                } else if (v == 1) {
                    Channel.send(M, h, new I(17));
                } else {
                    Channel.send(M, h, new I(33));
                }
            }
            case "test_nondet" -> {
                int n1 = 4 + Nondet.chooseIndex(2);
                if (n1 < 4 || n1 >= 6) {
                    throw new AssertionError("n1");
                }
                Nondet.chooseIndex(2);
            }
            default -> throw new IllegalArgumentException("unknown test " + name);
        }
        sp.awaitAll();
    }
}
