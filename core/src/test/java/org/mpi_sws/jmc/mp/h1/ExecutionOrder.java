package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TraceForge {@code tests/execution_order.rs}: the four {@code test_out_of_order_*_recv*} tests
 * (a message must not be delivered before its send executed; Rust's futures {@code Mutex<bool>} is
 * plain memory, not a TraceForge event — here an {@link AtomicBoolean}, created per iteration, and
 * no JMC event since H1 runs without the agent) and {@code two_receives_two_sends} (tags 1 and 2 →
 * record classes {@code Tg1}, {@code Tg2}).
 *
 * <p>Arguments: {@code <test>}.
 */
public final class ExecutionOrder implements Runnable {

    /** An integer payload. */
    record I(int v) implements MpMessage {}

    /** A string sent with tag 1. */
    record Tg1(String v) implements MpMessage {}

    /** A string sent with tag 2. */
    record Tg2(String v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;

    /**
     * @param args test name
     */
    public ExecutionOrder(String... args) {
        this.name = args[0].trim();
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (name) {
            case "test_out_of_order_blocking_recv" -> outOfOrder(sp, true, false);
            case "test_out_of_order_blocking_recv2" -> outOfOrder(sp, true, true);
            case "test_out_of_order_nonblocking_recv" -> outOfOrder(sp, false, false);
            case "test_out_of_order_nonblocking_recv2" -> outOfOrder(sp, false, true);
            case "two_receives_two_sends" -> {
                Tid tid1 = sp.spawn(() -> {
                    Channel.recv(M, Tg1.class);
                    Channel.recv(M, Tg2.class);
                });
                sp.spawn(() -> {
                    Channel.send(M, tid1, new Tg2("value2"));
                    Channel.send(M, tid1, new Tg1("value1"));
                });
            }
            default -> throw new IllegalArgumentException("unknown execution_order test " + name);
        }
        sp.awaitAll();
    }

    private void outOfOrder(H1Support.Spawner sp, boolean blocking, boolean nondetFirst) {
        AtomicBoolean sent = new AtomicBoolean(false);
        Tid mainTid = MpProcess.self();
        sp.spawn(() -> {
            sent.set(true);
            Channel.send(M, mainTid, new I(1));
        });
        if (nondetFirst) {
            Nondet.chooseBoolean();
        }
        if (blocking) {
            Channel.recv(M);
            if (!sent.get()) {
                throw new IllegalStateException("Received a message that was not yet sent.");
            }
        } else {
            Optional<MpMessage> received = Channel.tryRecv(M);
            if (received.isPresent() && !sent.get()) {
                throw new IllegalStateException("Received a message that was not yet sent.");
            }
        }
        if (!nondetFirst) {
            Nondet.chooseBoolean();
        }
    }
}
