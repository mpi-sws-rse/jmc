package org.mpi_sws.jmc.mp.programs.j4;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TraceForge's A2 key-value store (conformance `grid.rs` `a2_spec`/`a2_pb`/`a2_pb_br`/`a2_sh`;
 * criteria `plan/traceForge/criteria/P5-APPS.md` "A2 — key-value store"), ported to {@code api.mp}.
 *
 * <p>Client {@code c_i}'s op {@code j} is {@code Put(c=i, k, v=10i+j)} if {@code i+j} is even, else
 * {@code Get(c=i, k)}, with {@code k = (j/2 + i/2) mod 2}; after each request the client blocks on
 * its reply ({@code Ack}, {@code Val(v)} or {@code None}). <b>Spec</b>: one invisible {@code store}
 * serving in arrival order. <b>pb</b>: {@code primary} forwards a put to {@code backup}, blocks on
 * the ack, replies; gets are served by the primary. <b>pb-br</b>: clients send gets to the backup
 * directly; the backup replies to clients and acks puts to the primary. <b>sh</b>: a {@code router}
 * forwards each request to shard {@code key mod s}; shards reply directly. The faults are the Rust
 * kinds: early-ack on pb-br (and the two unobservable controls), stale-get and silent-put on pb,
 * misroute on sh.
 *
 * <p>TraceForge's typed channels become type-test receives on one mailbox: under {@code P2P} a
 * guarded receive reads each sender's earliest unread <em>matching</em> send, which is per-sender
 * FIFO per message type — the same relation as a channel per type. Spawn order as in Rust: the
 * servers, then the clients; main is silent; all threads are constructed first so that every body
 * knows every identity.
 */
public final class A2Store implements MpProgram {

    /** {@code Req::{Put, Get}}. */
    public sealed interface Req extends MpMessage permits Put, Get {}

    /** {@code Put { c, k, v }}. */
    @Tag("Put")
    public record Put(int c, int k, int v) implements Req {}

    /** {@code Get { c, k }}. */
    @Tag("Get")
    public record Get(int c, int k) implements Req {}

    /** {@code Rep::{Ack, Val(v), None}}; the unit variants are tagged records with no fields. */
    public sealed interface Rep extends MpMessage permits Ack, Val, None {}

    /** {@code Ack}. */
    @Tag("Ack")
    public record Ack() implements Rep {}

    /** {@code Val(v)}. */
    @Tag("Val")
    public record Val(int v) implements Rep {}

    /** {@code None}. */
    @Tag("None")
    public record None() implements Rep {}

    /** {@code Internal::{Fwd(Req), AckPut}} — between the invisible primary and backup only. */
    public sealed interface Internal extends MpMessage permits Fwd, AckPut {}

    /** {@code Fwd(Req)}. */
    @Tag("Fwd")
    public record Fwd(Req r) implements Internal {}

    /** {@code AckPut}. */
    @Tag("AckPut")
    public record AckPut() implements Internal {}

    /** The Rust rows: Spec; {@code A2Pb::*}; {@code A2Br::*}; {@code a2_sh(.., misroute)}. */
    public enum Kind {
        SPEC("spec"),
        PB("pb"),
        PB_EARLY_ACK("control-early-ack-primary"),
        PB_STALE_GET("stale-get"),
        PB_SILENT_PUT("silent-put"),
        BR("pb-br"),
        BR_EARLY_ACK("early-ack"),
        BR_FWD_EARLY_ACK("control-early-ack-fwd"),
        SH("sh"),
        SH_MISROUTE("misroute");

        final String label;

        Kind(String label) {
            this.label = label;
        }

        static Kind byLabel(String label) {
            for (Kind k : values()) {
                if (k.label.equals(label.trim())) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown A2 kind " + label + "; one of spec, pb, pb-br, sh,"
                    + " early-ack, stale-get, silent-put, misroute, control-early-ack-primary,"
                    + " control-early-ack-fwd");
        }

        boolean pb() {
            return this == PB || this == PB_EARLY_ACK || this == PB_STALE_GET || this == PB_SILENT_PUT;
        }

        boolean br() {
            return this == BR || this == BR_EARLY_ACK || this == BR_FWD_EARLY_ACK;
        }

        boolean sh() {
            return this == SH || this == SH_MISROUTE;
        }
    }

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final int clientCount;
    private final int opsPerClient;
    private final int shardCount;
    private final Kind kind;

    /** The launcher default: pb at (2, 1). */
    public A2Store() {
        this(2, 1, 1, Kind.PB);
    }

    /**
     * The launcher's {@code A2Store:<kind>,<k>,<q>[,<s>]} form.
     *
     * @param args the kind's label (the grid row name), {@code k}, {@code q}, and {@code s} for sh
     */
    public A2Store(String... args) {
        this(intArg(args, 1), intArg(args, 2), args.length > 3 ? intArg(args, 3) : 1, Kind.byLabel(kindArg(args)));
    }

    /**
     * A row at a size.
     *
     * @param clientCount {@code k}
     * @param opsPerClient {@code q}
     * @param shardCount {@code s}; {@code 1} unless the kind is sh or misroute
     * @param kind the row
     */
    public A2Store(int clientCount, int opsPerClient, int shardCount, Kind kind) {
        if (clientCount < 1 || opsPerClient < 1) {
            throw new IllegalArgumentException("k and q must be at least 1");
        }
        if (kind.sh() ? shardCount < 1 : shardCount != 1) {
            throw new IllegalArgumentException("s is a knob of sh only");
        }
        if (kind == Kind.SH_MISROUTE && shardCount < 2) {
            throw new IllegalArgumentException("misroute needs s >= 2 (at s = 1 it routes as sh; the grid runs it at s = 2)");
        }
        this.clientCount = clientCount;
        this.opsPerClient = opsPerClient;
        this.shardCount = shardCount;
        this.kind = kind;
    }

    private static String kindArg(String[] args) {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("A2Store takes <kind>,<k>,<q>[,<s>]");
        }
        return args[0];
    }

    private static int intArg(String[] args, int at) {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("A2Store takes <kind>,<k>,<q>[,<s>]");
        }
        return Integer.parseInt(args[at].trim());
    }

    /** Client {@code i}'s op {@code j} (the encoding of P5-APPS). */
    static Req op(int i, int j) {
        int key = (j / 2 + i / 2) % 2;
        return (i + j) % 2 == 0 ? new Put(i, key, 10 * i + j) : new Get(i, key);
    }

    @Override
    public String programName() {
        return "a2-" + kind.label + "-" + size();
    }

    @Override
    public String size() {
        return "k" + clientCount + "q" + opsPerClient + (kind.sh() ? "s" + shardCount : "");
    }

    @Override
    public CommunicationModel model() {
        return M;
    }

    private List<String> serverRoles() {
        List<String> r = new ArrayList<>();
        if (kind == Kind.SPEC) {
            r.add("store");
        } else if (kind.sh()) {
            r.add("router");
            for (int x = 0; x < shardCount; x++) {
                r.add("shard" + x);
            }
        } else {
            r.add("primary");
            r.add("backup");
        }
        return r;
    }

    @Override
    public List<String> rolesInSpawnOrder() {
        List<String> roles = serverRoles();
        for (int i = 0; i < clientCount; i++) {
            roles.add("c" + i);
        }
        return roles;
    }

    @Override
    public List<String> visibleRoles() {
        List<String> all = rolesInSpawnOrder();
        return all.subList(all.size() - clientCount, all.size());
    }

    @Override
    public void run() {
        Tid[] clients = new Tid[clientCount];
        Tid[] servers = new Tid[serverRoles().size()];
        List<MpThread> threads = new ArrayList<>();
        if (kind == Kind.SPEC) {
            threads.add(new MpThread(() -> store(clients), "store"));
        } else if (kind.sh()) {
            threads.add(new MpThread(() -> router(servers), "router"));
            for (int x = 0; x < shardCount; x++) {
                final int shard = x;
                threads.add(new MpThread(() -> shard(shard, clients), "shard" + shard));
            }
        } else if (kind.pb()) {
            threads.add(new MpThread(() -> pbPrimary(servers, clients), "primary"));
            threads.add(new MpThread(() -> pbBackup(servers), "backup"));
        } else {
            threads.add(new MpThread(() -> brPrimary(servers, clients), "primary"));
            threads.add(new MpThread(() -> brBackup(servers, clients), "backup"));
        }
        for (int x = 0; x < servers.length; x++) {
            servers[x] = Tid.of(threads.get(x).getTaskId());
        }
        for (int i = 0; i < clientCount; i++) {
            final int me = i;
            MpThread t = new MpThread(() -> client(me, servers), "c" + i);
            clients[i] = Tid.of(t.getTaskId());
            threads.add(t);
        }
        for (MpThread t : threads) {
            t.start();
        }
        try {
            for (MpThread t : threads) {
                t.join1();
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Where client {@code i} sends request {@code r}: the Rust {@code route} closure. */
    private Tid route(Req r, Tid[] servers) {
        if (kind.br() && !(r instanceof Put) && kind != Kind.BR_FWD_EARLY_ACK) {
            return servers[1];
        }
        return servers[0];
    }

    private void client(int i, Tid[] servers) {
        for (int j = 0; j < opsPerClient; j++) {
            Req r = op(i, j);
            Channel.send(M, route(r, servers), r);
            Channel.recv(M, Rep.class);
        }
    }

    private static Rep lookup(Map<Integer, Integer> map, int key) {
        Integer v = map.get(key);
        return v == null ? new None() : new Val(v);
    }

    private void store(Tid[] clients) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int n = 0; n < clientCount * opsPerClient; n++) {
            Req r = Channel.recv(M, Req.class);
            if (r instanceof Put p) {
                map.put(p.k(), p.v());
                Channel.send(M, clients[p.c()], new Ack());
            } else {
                Get g = (Get) r;
                Channel.send(M, clients[g.c()], lookup(map, g.k()));
            }
        }
    }

    private int putCount() {
        int n = 0;
        for (int i = 0; i < clientCount; i++) {
            for (int j = 0; j < opsPerClient; j++) {
                if (op(i, j) instanceof Put) {
                    n++;
                }
            }
        }
        return n;
    }

    private void pbPrimary(Tid[] servers, Tid[] clients) {
        Map<Integer, Integer> map = new HashMap<>();
        boolean firstPut = true;
        for (int n = 0; n < clientCount * opsPerClient; n++) {
            Req r = Channel.recv(M, Req.class);
            if (r instanceof Put p) {
                if (kind != Kind.PB_STALE_GET) {
                    map.put(p.k(), p.v());
                }
                Channel.send(M, servers[1], new Fwd(p));
                if (kind == Kind.PB_EARLY_ACK) {
                    Channel.send(M, clients[p.c()], new Ack());
                    Channel.recv(M, Internal.class);
                } else {
                    Channel.recv(M, Internal.class);
                    boolean silent = kind == Kind.PB_SILENT_PUT && firstPut;
                    if (!silent) {
                        Channel.send(M, clients[p.c()], new Ack());
                    }
                }
                firstPut = false;
            } else {
                Get g = (Get) r;
                Channel.send(M, clients[g.c()], lookup(map, g.k()));
            }
        }
    }

    private void pbBackup(Tid[] servers) {
        Map<Integer, Integer> map = new HashMap<>();
        int puts = putCount();
        for (int n = 0; n < puts; n++) {
            Internal m = Channel.recv(M, Internal.class);
            if (m instanceof Fwd f && f.r() instanceof Put p) {
                map.put(p.k(), p.v());
            }
            Channel.send(M, servers[0], new AckPut());
        }
    }

    private void brPrimary(Tid[] servers, Tid[] clients) {
        int n = kind == Kind.BR_FWD_EARLY_ACK ? clientCount * opsPerClient : putCount();
        for (int x = 0; x < n; x++) {
            Req r = Channel.recv(M, Req.class);
            if (r instanceof Put p) {
                Channel.send(M, servers[1], p);
                if (kind == Kind.BR) {
                    Channel.recv(M, Internal.class);
                    Channel.send(M, clients[p.c()], new Ack());
                } else {
                    Channel.send(M, clients[p.c()], new Ack());
                    Channel.recv(M, Internal.class);
                }
            } else {
                Channel.send(M, servers[1], r);
            }
        }
    }

    private void brBackup(Tid[] servers, Tid[] clients) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int n = 0; n < clientCount * opsPerClient; n++) {
            Req r = Channel.recv(M, Req.class);
            if (r instanceof Put p) {
                map.put(p.k(), p.v());
                Channel.send(M, servers[0], new AckPut());
            } else {
                Get g = (Get) r;
                Channel.send(M, clients[g.c()], lookup(map, g.k()));
            }
        }
    }

    private int shardOf(Req r) {
        int key = r instanceof Put p ? p.k() : ((Get) r).k();
        return kind == Kind.SH_MISROUTE && !(r instanceof Put) ? (key + 1) % shardCount : key % shardCount;
    }

    private void router(Tid[] servers) {
        for (int n = 0; n < clientCount * opsPerClient; n++) {
            Req r = Channel.recv(M, Req.class);
            Channel.send(M, servers[1 + shardOf(r)], r);
        }
    }

    private void shard(int x, Tid[] clients) {
        Map<Integer, Integer> map = new HashMap<>();
        int n = 0;
        for (int i = 0; i < clientCount; i++) {
            for (int j = 0; j < opsPerClient; j++) {
                if (shardOf(op(i, j)) == x) {
                    n++;
                }
            }
        }
        for (int m = 0; m < n; m++) {
            Req r = Channel.recv(M, Req.class);
            if (r instanceof Put p) {
                map.put(p.k(), p.v());
                Channel.send(M, clients[p.c()], new Ack());
            } else {
                Get g = (Get) r;
                Channel.send(M, clients[g.c()], lookup(map, g.k()));
            }
        }
    }
}
