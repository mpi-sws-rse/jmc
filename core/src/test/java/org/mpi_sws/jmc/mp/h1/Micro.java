package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * TraceForge {@code tests/microbenchmarks.rs}, FIFO arms: {@code ns_nr} (the untagged half of
 * {@code ns_nr_tag}: the receiver's untagged receive ignores the senders' tags, so they are
 * dropped), {@code ns_rn_mk}, {@code wakeup_stress}. ({@code ns_r} is {@code litmus.rs ns_r_wb}.)
 *
 * <p>Arguments: {@code <ns_nr|ns_rn_mk|wakeup_stress>,n}.
 */
public final class Micro implements Runnable {

    /** {@code Msg::Val(u32)}. */
    record Val(int v) implements MpMessage {}

    /** {@code Msg::Val(ThreadId, u32)}. */
    record TVal(Tid t, int v) implements MpMessage {}

    /** {@code Msg::Start}. */
    record Start() implements MpMessage {}

    /** {@code Msg::Done}. */
    record Done() implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;
    private final int n;

    /**
     * @param args program name and {@code n}
     */
    public Micro(String... args) {
        this.name = args[0].trim();
        this.n = Integer.parseInt(args[1].trim());
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        Tid mainId = MpProcess.self();
        switch (name) {
            case "ns_nr" -> {
                Tid r = sp.spawn(() -> {
                    for (int i = 0; i < n; i++) {
                        Channel.recv(M);
                    }
                });
                for (int i = 0; i < n; i++) {
                    final int fi = i;
                    sp.spawn(() -> Channel.send(M, r, new Val(fi)));
                }
            }
            case "ns_rn_mk" -> {
                for (int i = 0; i < n; i++) {
                    final int fi = i;
                    sp.spawn(() -> Channel.send(M, mainId, new TVal(MpProcess.self(), fi)));
                }
                for (int i = 0; i < n; i++) {
                    Channel.recv(M);
                }
            }
            case "wakeup_stress" -> {
                Tid waiter = sp.spawn(() -> {
                    for (int i = 0; i < n; i++) {
                        Channel.recv(M);
                    }
                    Channel.send(M, mainId, new Done());
                });
                for (int i = 0; i < n; i++) {
                    final int fi = i;
                    sp.spawn(() -> Channel.send(M, waiter, new TVal(MpProcess.self(), fi)));
                }
                Channel.send(M, mainId, new Start());
                Channel.recv(M);
            }
            default -> throw new IllegalArgumentException("unknown microbenchmark " + name);
        }
        sp.awaitAll();
    }
}
