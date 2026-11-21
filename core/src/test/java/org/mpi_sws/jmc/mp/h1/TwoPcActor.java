package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TraceForge {@code tests/two_pc_actor.rs} ({@code two_pc_verify}, FIFO): two-phase commit written
 * against a generic actor loop, two requests. {@code ActorMsg<S, T>} is {@code Init(S)} ({@link
 * CInit}/{@link PInit}), {@code Terminate} and {@code Msg(T)} ({@link AMsg} wrapping the inner
 * message record). The actor's first message must be {@code Init}, otherwise
 * {@code assume!(false)} (encoded). Main sends {@code Init}s, the two requests and {@code Terminate}
 * to the coordinator, <em>joins the coordinator</em>, then sends {@code Terminate} to the
 * participants and joins them: main's later sends are join-ordered after the coordinator in
 * TraceForge (H1 plan: join-edge).
 *
 * <p>Arguments: {@code N} (participants; the test uses 2).
 */
public final class TwoPcActor implements Runnable {

    /** {@code ActorMsg::Init(Vec<ThreadId>)} to the coordinator. */
    record CInit(List<Tid> ps) implements MpMessage {}

    /** {@code ActorMsg::Init(())} to a participant. */
    record PInit() implements MpMessage {}

    /** {@code ActorMsg::Terminate}. */
    record Terminate() implements MpMessage {}

    /** {@code ActorMsg::Msg(T)}. */
    record AMsg(MpMessage m) implements MpMessage {}

    /** {@code CoordinatorMsg::Request(u32)}. */
    record Request(int id) implements MpMessage {}

    /** {@code CoordinatorMsg::Yes(u32)}. */
    record Yes(int id) implements MpMessage {}

    /** {@code CoordinatorMsg::No(u32)}. */
    record No(int id) implements MpMessage {}

    /** {@code ParticipantMsg::Prepare(ThreadId, u32)}. */
    record Prepare(Tid c, int id) implements MpMessage {}

    /** {@code ParticipantMsg::Commit(u32)}. */
    record Commit(int id) implements MpMessage {}

    /** {@code ParticipantMsg::Abort(u32)}. */
    record Abort(int id) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final int n;
    /** {@code tfjoin}: Rust's joins use {@link H1Support#joinTf} instead of {@code join1}. */
    private final boolean tfJoin;

    /**
     * @param args the number of participants, then optionally {@code tfjoin}
     */
    public TwoPcActor(String... args) {
        this.n = Integer.parseInt(args[0].trim());
        this.tfJoin = args.length > 1 && args[1].trim().equals("tfjoin");
    }

    /** The actor trait: {@code start}, {@code handle} ({@code stop} is empty in both actors). */
    private interface Actor {
        void start(MpMessage init);

        void handle(MpMessage m);
    }

    /** {@code actor(a)}. */
    private static void actor(Actor a) {
        boolean started = false;
        MpMessage first = Channel.recv(M);
        if (first instanceof CInit || first instanceof PInit) {
            a.start(first);
            started = true;
        } else {
            H1Support.assume(false, M);
        }
        while (true) {
            MpMessage msg = Channel.recv(M);
            if (msg instanceof CInit || msg instanceof PInit) {
                throw new IllegalStateException("Restarting actor?");
            } else if (msg instanceof AMsg am) {
                if (started) {
                    a.handle(am.m());
                } else {
                    throw new IllegalStateException("Message to actor that is not started");
                }
            } else if (msg instanceof Terminate) {
                break;
            } else {
                throw new IllegalStateException("unexpected " + msg);
            }
        }
    }

    private static final class Coordinator implements Actor {
        private List<Tid> participants = new ArrayList<>();
        private final Map<Integer, int[]> replyMap = new HashMap<>();

        @Override
        public void start(MpMessage init) {
            participants = ((CInit) init).ps();
        }

        private void commit(int id) {
            for (Tid t : participants) {
                Channel.send(M, t, new AMsg(new Commit(id)));
            }
        }

        private void abort(int id) {
            for (Tid t : participants) {
                Channel.send(M, t, new AMsg(new Abort(id)));
            }
        }

        @Override
        public void handle(MpMessage msg) {
            if (msg instanceof Request r) {
                if (replyMap.containsKey(r.id())) {
                    throw new AssertionError("assert!(!reply_map.contains_key)");
                }
                replyMap.put(r.id(), new int[] {0, 0});
                Tid self = MpProcess.self();
                for (Tid id : participants) {
                    Channel.send(M, id, new AMsg(new Prepare(self, r.id())));
                }
            } else if (msg instanceof Yes y) {
                int[] ty = replyMap.get(y.id());
                if (ty == null) {
                    throw new AssertionError("assert!(reply_map.contains_key)");
                }
                int total = ty[0];
                int yes = ty[1];
                replyMap.put(y.id(), new int[] {total + 1, yes + 1});
                if (yes + 1 == participants.size()) {
                    commit(y.id());
                    replyMap.remove(y.id());
                }
            } else if (msg instanceof No no) {
                int[] ty = replyMap.get(no.id());
                if (ty == null) {
                    throw new AssertionError("assert!(reply_map.contains_key)");
                }
                int total = ty[0];
                int yes = ty[1];
                replyMap.put(no.id(), new int[] {total + 1, yes});
                if (total + 1 == participants.size()) {
                    abort(no.id());
                    replyMap.remove(no.id());
                }
            } else {
                throw new IllegalStateException("unexpected " + msg);
            }
        }
    }

    private static final class Participant implements Actor {
        private final Map<Integer, Boolean> requests = new HashMap<>();

        @Override
        public void start(MpMessage init) {
        }

        @Override
        public void handle(MpMessage msg) {
            if (msg instanceof Prepare p) {
                boolean response = Nondet.chooseBoolean();
                if (response) {
                    Channel.send(M, p.c(), new AMsg(new Yes(p.id())));
                } else {
                    Channel.send(M, p.c(), new AMsg(new No(p.id())));
                }
                requests.put(p.id(), response);
            } else if (msg instanceof Abort a) {
                if (!requests.containsKey(a.id())) {
                    throw new AssertionError("assert!(requests.contains_key)");
                }
            } else if (msg instanceof Commit c) {
                if (!requests.containsKey(c.id())) {
                    throw new AssertionError("assert!(requests.contains_key)");
                }
                if (!requests.get(c.id())) {
                    throw new AssertionError("assert!(said yes)");
                }
            } else {
                throw new IllegalStateException("unexpected " + msg);
            }
        }
    }

    @Override
    public void run() {
        H1Support.Joinable c = new H1Support.Joinable(() -> actor(new Coordinator())).start();
        Tid cid = c.tid;
        List<H1Support.Joinable> ps = new ArrayList<>();
        List<Tid> pids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            H1Support.Joinable p = new H1Support.Joinable(() -> actor(new Participant())).start();
            ps.add(p);
            pids.add(p.tid);
        }
        Channel.send(M, cid, new CInit(List.copyOf(pids)));
        for (Tid p : pids) {
            Channel.send(M, p, new PInit());
        }
        Channel.send(M, cid, new AMsg(new Request(1)));
        Channel.send(M, cid, new AMsg(new Request(2)));
        Channel.send(M, cid, new Terminate());
        join(c);
        for (Tid p : pids) {
            Channel.send(M, p, new Terminate());
        }
        for (H1Support.Joinable p : ps) {
            join(p);
        }
    }

    /** Rust's {@code JoinHandle::join}: {@code join1}, or the TF-join encoding under {@code tfjoin}. */
    private void join(H1Support.Joinable j) {
        if (tfJoin) {
            H1Support.joinTf(j, M);
            return;
        }
        try {
            j.thread.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
