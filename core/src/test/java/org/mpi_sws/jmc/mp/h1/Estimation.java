package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * The programs of TraceForge {@code tests/estimation.rs} that use message passing only, whose
 * tests ask TraceForge for an <em>estimate</em> (and, for {@code a_nstepsb}, also {@code verify});
 * H1 compares the exact counts. Default config: FIFO. {@code srs_rss_rr} fixed; the others take
 * {@code n} ({@code n_test} in Rust).
 *
 * <p>Arguments: {@code <srs_rss_rr|sr_ncopies|ns_nseqr|a_nstepsb>[,n]}.
 */
public final class Estimation implements Runnable {

    /** A {@code u32} payload. */
    record I(int v) implements MpMessage {}

    /** {@code (ThreadId, u32)}. */
    record TI(Tid t, int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;
    private final int n;

    /**
     * @param args program name, then {@code n}
     */
    public Estimation(String... args) {
        this.name = args[0].trim();
        this.n = args.length > 1 ? Integer.parseInt(args[1].trim()) : 0;
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (name) {
            case "srs_rss_rr" -> {
                Tid t1 = sp.spawn(() -> {
                    Channel.recv(M);
                    Channel.recv(M);
                });
                Tid t2 = sp.spawn(() -> {
                    TI m = (TI) Channel.recv(M);
                    Channel.send(M, m.t(), new I(2));
                    Channel.send(M, t1, new I(3));
                });
                sp.spawn(() -> {
                    Channel.send(M, t2, new TI(MpProcess.self(), 1));
                    Channel.recv(M);
                    Channel.send(M, t1, new I(4));
                });
            }
            case "sr_ncopies" -> {
                for (int i = 0; i < n; i++) {
                    Tid tr = sp.spawn(() -> Channel.recv(M));
                    sp.spawn(() -> Channel.send(M, tr, new I(1)));
                }
            }
            case "ns_nseqr" -> {
                Tid receiver = sp.spawn(() -> {
                    for (int i = 0; i < n; i++) {
                        Channel.recv(M);
                    }
                });
                for (int i = 0; i < n; i++) {
                    sp.spawn(() -> Channel.send(M, receiver, new I(1)));
                }
            }
            case "a_nstepsb" -> {
                Tid sink = sp.spawn(() -> {
                    Channel.recv(M);
                    Channel.recv(M);
                });
                Tid receiver = sp.spawn(() -> {
                    for (int i = 0; i < n; i++) {
                        Channel.recv(M);
                    }
                });
                sp.spawn(() -> {
                    for (int i = 0; i < n; i++) {
                        Channel.send(M, receiver, new I(0));
                    }
                    Channel.send(M, sink, new I(1));
                });
                sp.spawn(() -> Channel.send(M, sink, new I(2)));
            }
            default -> throw new IllegalArgumentException("unknown estimation program " + name);
        }
        sp.awaitAll();
    }
}
