package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.List;

/**
 * TraceForge's two-phase commit, both test files (FIFO): {@code tests/two_pc.rs}
 * ({@code two_pc_serial}: main spawns the coordinator, then the participants, and sends the
 * coordinator {@code Init(participant ids)}) and {@code tests/two_pc_demo.rs}
 * ({@code two_pc_serial}: participants first, then the coordinator, which gets the ids by
 * closure; main is silent). Participants vote by {@code nondet()}; TraceForge asserts
 * {@code 2^N * N!} executions and no blocked ones. Every receive is an unguarded mailbox receive,
 * as in Rust.
 *
 * <p>Arguments: {@code <two_pc|two_pc_demo>,N}.
 */
public final class TwoPc implements Runnable {

    /** {@code CoordinatorMsg::Init(Vec<ThreadId>)}. */
    record Init(List<Tid> ids) implements MpMessage {}

    /** {@code CoordinatorMsg::Yes}. */
    record Yes() implements MpMessage {}

    /** {@code CoordinatorMsg::No}. */
    record No() implements MpMessage {}

    /** {@code ParticipantMsg::Prepare(ThreadId)}. */
    record Prepare(Tid c) implements MpMessage {}

    /** {@code ParticipantMsg::Commit}. */
    record Commit() implements MpMessage {}

    /** {@code ParticipantMsg::Abort}. */
    record Abort() implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final boolean demo;
    private final int n;

    /**
     * @param args variant and number of participants
     */
    public TwoPc(String... args) {
        this.demo = switch (args[0].trim()) {
            case "two_pc" -> false;
            case "two_pc_demo" -> true;
            default -> throw new IllegalArgumentException("TwoPc takes <two_pc|two_pc_demo>,N");
        };
        this.n = Integer.parseInt(args[1].trim());
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        if (!demo) {
            Tid c = sp.spawn(() -> coordinator(null));
            List<Tid> ps = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ps.add(sp.spawn(this::participant));
            }
            Channel.send(M, c, new Init(List.copyOf(ps)));
        } else {
            List<Tid> ps = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ps.add(sp.spawn(this::participant));
            }
            List<Tid> pids = List.copyOf(ps);
            sp.spawn(() -> coordinator(pids));
        }
        sp.awaitAll();
    }

    /** {@code coordinator()} (two_pc.rs, {@code given == null}) or {@code coordinator(ps)} (demo). */
    private void coordinator(List<Tid> given) {
        List<Tid> ps;
        if (given == null) {
            MpMessage m = Channel.recv(M);
            if (!(m instanceof Init init)) {
                throw new IllegalStateException("panic!()");
            }
            ps = init.ids();
        } else {
            ps = given;
        }
        Tid self = MpProcess.self();
        for (Tid id : ps) {
            Channel.send(M, id, new Prepare(self));
        }
        int numYes = 0;
        for (int i = 0; i < ps.size(); i++) {
            MpMessage r = Channel.recv(M);
            if (r instanceof Yes) {
                numYes++;
            } else if (!(r instanceof No)) {
                throw new IllegalStateException("panic!()");
            }
        }
        if (numYes == ps.size()) {
            for (Tid id : ps) {
                Channel.send(M, id, new Commit());
            }
        } else {
            for (Tid id : ps) {
                Channel.send(M, id, new Abort());
            }
        }
    }

    private void participant() {
        MpMessage m = Channel.recv(M);
        if (!(m instanceof Prepare p)) {
            throw new IllegalStateException("panic!()");
        }
        boolean response = Nondet.chooseBoolean();
        if (response) {
            Channel.send(M, p.c(), new Yes());
        } else {
            Channel.send(M, p.c(), new No());
        }
        MpMessage action = Channel.recv(M);
        if (action instanceof Commit) {
            if (!response) {
                throw new AssertionError("assert!(response)");
            }
        } else if (!(action instanceof Abort)) {
            throw new IllegalStateException("panic!()");
        }
    }
}
