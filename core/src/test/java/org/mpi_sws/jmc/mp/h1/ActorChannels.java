package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Channels and shared variables built from actors, from TraceForge (FIFO, default config):
 * <ul>
 *   <li>{@code mpsc} = {@code tests/mpsctest.rs simple}: a daemon channel manager
 *       ({@link MpProcess#spawnDaemon}, J2c, as Rust's {@code Builder::spawn_daemon}); the receiver
 *       asks with {@code DeQ} (RECV_TAG → class {@link DeQ}), the sender offers with {@code InQ}
 *       (SEND_TAG → {@link InQ}); the manager pairs them. The replies are received with the sender
 *       predicate {@code tid == manager}; only the manager ever sends to those two threads, so the
 *       predicate is vacuous and the receive unguarded.</li>
 *   <li>{@code bounded_channel,k} = {@code tests/channel.rs channel_s_r}: a blocking channel actor
 *       of capacity {@code k}; a producer writes 16, 18, 20, a consumer reads two; main joins both,
 *       <em>then</em> sends {@code Terminate} (join-edge).</li>
 *   <li>{@code locked_bank} = {@code tests/sharedmem.rs locked_bank_test}: a lock actor (CAS) and
 *       a register actor; two clients each lock, read, write ±10, unlock; a failed CAS is
 *       {@code assume!(false)} (encoded); main joins the clients, <em>then</em> reads the register,
 *       asserts 100 and terminates both actors (join-edge).</li>
 * </ul>
 *
 * <p>Arguments: {@code <mpsc|bounded_channel,k|locked_bank>[,tfjoin]}; {@code tfjoin} replaces
 * {@code join1} by {@link H1Support#joinTf} at Rust's joins.
 */
public final class ActorChannels implements Runnable {

    // mpsc
    /** {@code ChannelMsgRequest::InQ(ThreadId, T)}, sent with SEND_TAG. */
    record InQ(Tid sender, int m) implements MpMessage {}

    /** {@code ChannelMsgRequest::DeQ(ThreadId)}, sent with RECV_TAG. */
    record DeQ(Tid receiver) implements MpMessage {}

    /** {@code ChannelMsgResponse::InQOk}. */
    record InQOk() implements MpMessage {}

    /** {@code ChannelMsgResponse::DeQOk(T)}. */
    record DeQOk(int v) implements MpMessage {}

    // bounded channel
    /** {@code BlockingChannelMsgRequest::InQ(ThreadId, T)}. */
    record BInQ(Tid tid, int e) implements MpMessage {}

    /** {@code BlockingChannelMsgRequest::DeQ(ThreadId)}. */
    record BDeQ(Tid tid) implements MpMessage {}

    /** {@code BlockingChannelMsgRequest::Terminate} / {@code ShVarMsg::Terminate}. */
    record Terminate() implements MpMessage {}

    /** {@code BlockingChannelMsgResponse::InQOk(ChannelId)}. */
    record BInQOk(Tid ch) implements MpMessage {}

    /** {@code BlockingChannelMsgResponse::DeQOk(ChannelId, T)}. */
    record BDeQOk(Tid ch, int e) implements MpMessage {}

    // shared variables
    /** {@code ShVarMsg::Read(ThreadId)}. */
    record Read(Tid tid) implements MpMessage {}

    /** {@code ShVarMsg::Write(ThreadId, i32)}. */
    record Write(Tid tid, int v) implements MpMessage {}

    /** {@code ShVarMsg::CAS(ThreadId, bool, bool)}. */
    record Cas(Tid tid, boolean old, boolean nw) implements MpMessage {}

    /** {@code ShVarMsg::RdOk(i32)}. */
    record RdOk(int v) implements MpMessage {}

    /** {@code ShVarMsg::WrOk}. */
    record WrOk() implements MpMessage {}

    /** {@code ShVarMsg::CASResponse(bool)}. */
    record CasResponse(boolean ok) implements MpMessage {}

    /** {@code LockedClientMsg::Init(ThreadId, ThreadId)}. */
    record ClientInit(Tid lock, Tid shvar) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;
    private final int k;
    /** {@code tfjoin}: Rust's joins use {@link H1Support#joinTf} instead of {@code join1}. */
    private final boolean tfJoin;

    /**
     * @param args model name, then {@code k} for the bounded channel
     */
    public ActorChannels(String... args) {
        this.name = args[0].trim();
        int kk = 0;
        boolean tf = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].trim().equals("tfjoin")) {
                tf = true;
            } else {
                kk = Integer.parseInt(args[i].trim());
            }
        }
        this.k = kk;
        this.tfJoin = tf;
    }

    /** Rust's {@code JoinHandle::join}: {@code join1}, or the TF-join encoding under {@code tfjoin}. */
    private void rjoin(H1Support.Joinable j) {
        if (tfJoin) {
            H1Support.joinTf(j, M);
        } else {
            join(j.thread);
        }
    }

    @Override
    public void run() {
        switch (name) {
            case "mpsc" -> mpsc();
            case "bounded_channel" -> boundedChannel();
            case "locked_bank" -> lockedBank();
            default -> throw new IllegalArgumentException("unknown model " + name);
        }
    }

    private static void join(MpThread t) {
        try {
            t.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ mpsc

    private void mpsc() {
        H1Support.Spawner sp = new H1Support.Spawner();
        Tid mgr = MpProcess.spawnDaemon(() -> {
            while (true) {
                DeQ fromReceiver = Channel.recv(M, DeQ.class);
                InQ fromSender = Channel.recv(M, InQ.class);
                Channel.send(M, fromSender.sender(), new InQOk());
                Channel.send(M, fromReceiver.receiver(), new DeQOk(fromSender.m()));
            }
        }).tid();
        sp.spawn(() -> {
            Channel.send(M, mgr, new DeQ(MpProcess.self()));
            MpMessage r = Channel.recv(M);
            if (!(r instanceof DeQOk d) || d.v() != 1) {
                throw new AssertionError("assert_eq!(r.recv().unwrap(), 1): " + r);
            }
        });
        Channel.send(M, mgr, new InQ(MpProcess.self(), 1));
        MpMessage r = Channel.recv(M);
        if (!(r instanceof InQOk)) {
            throw new IllegalStateException("Sender::send: Unexpected channel response " + r);
        }
        sp.awaitAll();
    }

    // ------------------------------------------------------------------ bounded channel

    private void runChannel() {
        Tid self = MpProcess.self();
        Deque<Integer> content = new ArrayDeque<>();
        Deque<Tid> blockedReaders = new ArrayDeque<>();
        Deque<BInQ> blockedWriters = new ArrayDeque<>();
        while (true) {
            MpMessage m = Channel.recv(M);
            if (m instanceof BInQ in) {
                if (content.size() < k) {
                    content.addLast(in.e());
                    Channel.send(M, in.tid(), new BInQOk(self));
                    while (!blockedReaders.isEmpty() && !content.isEmpty()) {
                        Tid tid = blockedReaders.pollFirst();
                        int e = content.pollFirst();
                        Channel.send(M, tid, new BDeQOk(self, e));
                    }
                } else {
                    blockedWriters.addLast(in);
                }
            } else if (m instanceof BDeQ dq) {
                if (!content.isEmpty()) {
                    int e = content.pollFirst();
                    Channel.send(M, dq.tid(), new BDeQOk(self, e));
                    while (!blockedWriters.isEmpty() && content.size() < k) {
                        BInQ w = blockedWriters.pollFirst();
                        content.addLast(w.e());
                        Channel.send(M, w.tid(), new BInQOk(self));
                    }
                } else {
                    blockedReaders.addLast(dq.tid());
                }
            } else if (m instanceof Terminate) {
                break;
            }
        }
    }

    private static void wrChannel(Tid c, Tid who, int what) {
        Channel.send(M, c, new BInQ(who, what));
        MpMessage r = Channel.recv(M);
        if (r instanceof BInQOk ok && ok.ch().equals(c)) {
            return;
        }
        H1Support.assume(false, M);
    }

    private static int rdChannel(Tid c, Tid who) {
        Channel.send(M, c, new BDeQ(who));
        MpMessage r = Channel.recv(M);
        if (r instanceof BDeQOk ok && ok.ch().equals(c)) {
            return ok.e();
        }
        H1Support.assume(false, M);
        throw new IllegalStateException("Assume false");
    }

    private void boundedChannel() {
        H1Support.Joinable c = new H1Support.Joinable(this::runChannel).start();
        Tid cid = c.tid;
        H1Support.Joinable producer = new H1Support.Joinable(() -> {
            Tid me = MpProcess.self();
            wrChannel(cid, me, 16);
            wrChannel(cid, me, 18);
            wrChannel(cid, me, 20);
        }).start();
        H1Support.Joinable consumer = new H1Support.Joinable(() -> {
            Tid me = MpProcess.self();
            if (rdChannel(cid, me) != 16) {
                throw new AssertionError("assert_eq!(v, 16)");
            }
            if (rdChannel(cid, me) != 18) {
                throw new AssertionError("assert_eq!(v, 18)");
            }
        }).start();
        rjoin(producer);
        rjoin(consumer);
        Channel.send(M, cid, new Terminate());
        rjoin(c);
    }

    // ------------------------------------------------------------------ locked bank

    private static void register(int init) {
        int register = init;
        while (true) {
            MpMessage m = Channel.recv(M);
            if (m instanceof Terminate) {
                break;
            } else if (m instanceof Read r) {
                Channel.send(M, r.tid(), new RdOk(register));
            } else if (m instanceof Write w) {
                register = w.v();
                Channel.send(M, w.tid(), new WrOk());
            } else {
                throw new IllegalStateException("panic!() " + m);
            }
        }
    }

    private static void newLock(boolean init) {
        boolean lock = init;
        while (true) {
            MpMessage m = Channel.recv(M);
            if (m instanceof Terminate) {
                break;
            } else if (m instanceof Cas c) {
                boolean success = false;
                if (lock == c.old()) {
                    lock = c.nw();
                    success = true;
                }
                Channel.send(M, c.tid(), new CasResponse(success));
            } else {
                throw new IllegalStateException("panic!() " + m);
            }
        }
    }

    private static int read(Tid v) {
        Channel.send(M, v, new Read(MpProcess.self()));
        MpMessage m = Channel.recv(M);
        if (m instanceof RdOk r) {
            return r.v();
        }
        H1Support.assume(false, M);
        throw new IllegalStateException("panic!()");
    }

    private static void write(Tid v, int e) {
        Channel.send(M, v, new Write(MpProcess.self(), e));
        MpMessage m = Channel.recv(M);
        if (m instanceof WrOk) {
            return;
        }
        H1Support.assume(false, M);
        throw new IllegalStateException("panic!()");
    }

    private static void lock(Tid v) {
        while (true) {
            Channel.send(M, v, new Cas(MpProcess.self(), false, true));
            MpMessage m = Channel.recv(M);
            boolean e;
            if (m instanceof CasResponse c) {
                e = c.ok();
            } else {
                H1Support.assume(false, M);
                throw new IllegalStateException("panic!()");
            }
            if (e) {
                break;
            } else {
                H1Support.assume(false, M);
                throw new IllegalStateException("panic!()");
            }
        }
    }

    private static void unlock(Tid v) {
        Channel.send(M, v, new Cas(MpProcess.self(), true, false));
    }

    private static void clientWithLock(boolean deposit) {
        ClientInit init = (ClientInit) Channel.recv(M);
        lock(init.lock());
        int balance = read(init.shvar());
        write(init.shvar(), deposit ? balance + 10 : balance - 10);
        unlock(init.lock());
    }

    private void lockedBank() {
        H1Support.Joinable l = new H1Support.Joinable(() -> newLock(false)).start();
        Tid lh = l.tid;
        H1Support.Joinable r = new H1Support.Joinable(() -> register(100)).start();
        Tid rh = r.tid;
        H1Support.Joinable c1 = new H1Support.Joinable(() -> clientWithLock(true)).start();
        H1Support.Joinable c2 = new H1Support.Joinable(() -> clientWithLock(false)).start();
        Channel.send(M, c1.tid, new ClientInit(lh, rh));
        Channel.send(M, c2.tid, new ClientInit(lh, rh));
        rjoin(c1);
        rjoin(c2);
        int balance = read(rh);
        if (balance != 100) {
            throw new AssertionError("assert_eq!(balance, 100): " + balance);
        }
        Channel.send(M, lh, new Terminate());
        Channel.send(M, rh, new Terminate());
        rjoin(l);
        rjoin(r);
    }
}
