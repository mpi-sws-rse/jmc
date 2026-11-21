package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * TraceForge {@code tests/litmus_channels.rs}, the tests without {@code select}. Every channel in
 * these tests has exactly one receiving thread, so the channel is that thread's mailbox; where a
 * thread is sent to on two channels ({@code smoke}) each channel has its own record class. A
 * channel whose {@code with_comm} is {@code NoOrder} uses asyn, {@code LocalOrder} (the default)
 * p2p. Where Rust sends on a channel before spawning its receiver, the receiver's id is minted
 * first ({@link H1Support.Spawner#create}) and the thread started at the Rust spawn.
 *
 * <p>Arguments: {@code <test>[,<fifo|bag>][,r]}.
 */
public final class LitmusChannels implements Runnable {

    /** A payload on the main channel. */
    record I(int v) implements MpMessage {}

    /** A payload on smoke's second channel ({@code "bar"}). */
    record Bar(int v) implements MpMessage {}

    private final String name;
    private final CommunicationModel m;
    private final int r;

    /**
     * @param args test name, model where the test has two arms, {@code r} for stress_max
     */
    public LitmusChannels(String... args) {
        this.name = args[0].trim();
        // blocking_rev's channel is built with_comm(NoOrder) whatever the config says.
        CommunicationModel dflt = this.name.equals("blocking_rev") ? CommunicationModel.ASYN : CommunicationModel.P2P;
        this.m = args.length > 1 ? H1Support.model(args[1]) : dflt;
        this.r = args.length > 2 ? Integer.parseInt(args[2].trim()) : 0;
    }

    private H1Support.Spawner sp;

    private void s(Tid to, MpMessage v) {
        Channel.send(m, to, v);
    }

    @Override
    public void run() {
        sp = new H1Support.Spawner();
        Tid me = MpProcess.self();
        switch (name) {
            case "smoke" -> {
                s(me, new I(42));
                s(me, new Bar(17));
                Channel.recv(m, I.class);
            }
            case "equal_channels" -> {
                s(me, new I(1));
                Channel.tryRecv(m);
            }
            case "forward_revisit" -> {
                MpThread t = sp.create(() -> Channel.tryRecv(m));
                Tid c = Tid.of(t.getTaskId());
                s(c, new I(1));
                s(c, new I(2));
                t.start();
            }
            case "backward_revisit" -> {
                sp.spawn(() -> s(me, new I(1)));
                Channel.tryRecv(m);
            }
            case "no_prefix_rev_cb" -> {
                Channel.tryRecv(m);
                sp.spawn(() -> s(me, new I(1)));
            }
            case "no_prefix_rev_ej" -> {
                MpThread p = new MpThread(() -> Channel.tryRecv(m));
                p.start();
                try {
                    p.join1();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                s(Tid.of(p.getTaskId()), new I(1));
            }
            case "maximality1" -> {
                sp.spawn(() -> s(me, new I(1)));
                Channel.tryRecv(m);
                Channel.tryRecv(m);
            }
            case "maximality2" -> {
                sp.spawn(() -> {
                    s(me, new I(1));
                    s(me, new I(2));
                });
                Channel.tryRecv(m);
                Channel.tryRecv(m);
            }
            case "stress_max" -> {
                int sends = m == CommunicationModel.ASYN ? 2 : r;
                sp.spawn(() -> {
                    for (int i = 0; i < sends; i++) {
                        s(me, new I(1));
                    }
                });
                for (int i = 0; i < r; i++) {
                    Channel.tryRecv(m);
                }
            }
            case "block" -> Channel.recv(m);
            case "rrb_s" -> {
                sp.spawn(() -> s(me, new I(1)));
                Channel.tryRecv(m);
                Channel.recv(m);
            }
            case "blocking_rev" -> {
                sp.spawn(() -> {
                    s(me, new I(1));
                    s(me, new I(2));
                    s(me, new I(3));
                });
                Channel.recv(m);
                Channel.recv(m);
            }
            case "legacy_channel" -> {
                Tid h = sp.spawn(() -> Channel.tryRecv(m));
                if (Nondet.chooseBoolean()) {
                    s(h, new I(42));
                } else {
                    s(h, new I(17));
                }
            }
            default -> throw new IllegalArgumentException("unknown litmus_channels test " + name);
        }
        sp.awaitAll();
    }
}
