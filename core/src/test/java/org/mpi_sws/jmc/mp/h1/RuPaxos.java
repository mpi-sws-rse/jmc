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
 * TraceForge {@code tests/rupaxos.rs} ({@code rupaxos_ok}, FIFO): the Paxos-like protocol of
 * doi:10.1145/3428278 in its non-buggy, non-decomposed configuration ({@code RUPAXOS_BUGGY},
 * {@code RUPAXOS_HELP_BUG_MANIFEST}, {@code RUPAXOS_DECOMPOSE} unset), 3 nodes,
 * {@code R = RUPAXOS_NUM_ROUNDS} rounds (default 1; TraceForge asserts 1299 complete executions at
 * R = 1). Every receive is unguarded; {@code send_or_fail} chooses by {@code nondet()} between the
 * message and {@code Fail}; both {@code assume!}s are encoded. Logs are strings, built with the
 * node's id ({@code Tid}'s text instead of {@code ThreadId}'s; only prefix relations among logs are
 * ever checked). Main joins the nodes and checks that the outputs are prefix-compatible; with
 * {@code tfjoin} the joins are {@link H1Support#joinTf} (H1-F4).
 *
 * <p>Arguments: {@code R[,tfjoin]}.
 */
public final class RuPaxos implements Runnable {

    /** {@code Message::Init(Vec<ThreadId>)}. */
    record Init(List<Tid> nodes) implements MpMessage {}

    /** {@code Message::Prepare(Prepare { phase, sender })}. */
    record Prepare(long phase, Tid sender) implements MpMessage {}

    /** {@code Message::Ack(Ack { phase, last, log })}. */
    record Ack(long phase, long last, String log) implements MpMessage {}

    /** {@code Message::Propose(Propose { phase, log })}. */
    record Propose(long phase, String log) implements MpMessage {}

    /** {@code Message::Promise(Promise { phase, log })}. */
    record Promise(long phase, String log) implements MpMessage {}

    /** {@code Message::Fail}. */
    record Fail() implements MpMessage {}

    private enum Round { ACK, PROPOSE, PROMISE }

    private static final CommunicationModel M = CommunicationModel.P2P;

    /** Fields of Rust's {@code State}. */
    private static final class State {
        long last = 1;
        long phase = 1;
        Tid leader;
        String log = "";
        Round step;
        List<Tid> nodes;
        final List<String> output = new ArrayList<>();
    }

    private final int rounds;
    private final boolean tfJoin;

    /**
     * @param args rounds, then optionally {@code tfjoin}
     */
    public RuPaxos(String... args) {
        this.rounds = Integer.parseInt(args[0].trim());
        this.tfJoin = args.length > 1 && args[1].trim().equals("tfjoin");
    }

    private static MpMessage receive() {
        return Channel.recv(M);
    }

    private static boolean sendOrFail(Tid tid, MpMessage msg) {
        boolean res = Nondet.chooseBoolean();
        Channel.send(M, tid, res ? msg : new Fail());
        return res;
    }

    private static void broadcast(State s, MpMessage msg) {
        for (Tid id : s.nodes) {
            Channel.send(M, id, msg);
        }
    }

    private static Tid getLeader(List<Tid> nodes, long phase) {
        if (phase == 1) {
            return nodes.get(0);
        } else if (phase == 2 || phase == 3) {
            return nodes.get(1);
        } else if (phase == 4) {
            return nodes.get(2);
        }
        throw new IllegalStateException("panic!() in get_leader, phase " + phase);
    }

    private static void prepare(State s) {
        Tid me = MpProcess.self();
        if (getLeader(s.nodes, s.phase).equals(me)) {
            broadcast(s, new Prepare(s.phase + 1, me));
        }
        MpMessage m = receive();
        if (m instanceof Prepare prep && prep.phase() >= s.phase) {
            s.phase = prep.phase();
            s.leader = prep.sender();
            s.step = Round.ACK;
        }
    }

    private static void ack(State s) {
        if (s.step != Round.ACK) {
            return;
        }
        boolean res = sendOrFail(s.leader, new Ack(s.phase, s.last, s.log));
        if (!res) {
            return;
        }
        Tid me = MpProcess.self();
        if (me.equals(s.leader)) {
            int numAcks = 0;
            String log = "";
            long lastSeen = 0;
            for (int i = 0; i < s.nodes.size(); i++) {
                MpMessage m = receive();
                if (m instanceof Ack a && a.phase() == s.phase) {
                    numAcks++;
                    if (a.last() > lastSeen) {
                        lastSeen = a.last();
                        log = a.log();
                    }
                }
            }
            if (s.phase == 2 || s.phase == 4) {
                H1Support.assume(numAcks > s.nodes.size() / 2, M);
            }
            if (numAcks > s.nodes.size() / 2) {
                s.log = log + me;
                s.step = Round.PROPOSE;
            }
        } else {
            s.step = Round.PROPOSE;
        }
    }

    private static void propose(State s) {
        if (s.step != Round.PROPOSE) {
            return;
        }
        if (MpProcess.self().equals(s.leader)) {
            broadcast(s, new Propose(s.phase, s.log));
        }
        MpMessage m = receive();
        if (m instanceof Propose p) {
            s.log = p.log();
            s.step = Round.PROMISE;
            s.last = s.phase;
        }
    }

    private static void promise(State s) {
        if (s.step != Round.PROMISE) {
            return;
        }
        broadcast(s, new Promise(s.phase, s.log));
        int numProms = 0;
        for (int i = 0; i < s.nodes.size(); i++) {
            if (receive() instanceof Promise) {
                numProms++;
            }
        }
        if (numProms > s.nodes.size() / 2) {
            s.output.add(s.log);
        }
    }

    private List<String> fsm() {
        MpMessage m = receive();
        State s = new State();
        if (m instanceof Init init) {
            s.nodes = init.nodes();
        } else {
            H1Support.assume(false, M);
            throw new IllegalStateException("panic!()");
        }
        for (int i = 0; i < rounds; i++) {
            prepare(s);
            ack(s);
            propose(s);
            promise(s);
        }
        return s.output;
    }

    @Override
    public void run() {
        int n = 3;
        @SuppressWarnings("unchecked")
        List<String>[] results = new List[n];
        List<H1Support.Joinable> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int fi = i;
            nodes.add(new H1Support.Joinable(() -> results[fi] = fsm()).start());
        }
        List<Tid> ids = new ArrayList<>();
        for (H1Support.Joinable h : nodes) {
            ids.add(h.tid);
        }
        Init init = new Init(List.copyOf(ids));
        for (H1Support.Joinable h : nodes) {
            Channel.send(M, h.tid, init);
        }
        List<List<String>> outs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (tfJoin) {
                H1Support.joinTf(nodes.get(i), M);
            } else {
                try {
                    nodes.get(i).thread.join1();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            List<String> res = results[i] == null ? List.of() : results[i];
            if (!res.isEmpty()) {
                outs.add(res);
            }
        }
        for (List<String> out1 : outs) {
            for (List<String> out2 : outs) {
                boolean all = out1.stream().allMatch(log1 ->
                        out2.stream().anyMatch(log2 -> log1.startsWith(log2) || log2.startsWith(log1)));
                if (!all) {
                    throw new AssertionError("rupaxos outputs not prefix-compatible: " + outs);
                }
            }
        }
    }
}
