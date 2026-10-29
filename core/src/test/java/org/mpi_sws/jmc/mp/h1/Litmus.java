package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TraceForge {@code tests/litmus.rs}, the FIFO and Bag tests, each program ported statement by
 * statement (the H1 inventory, plan/jmc-must/log/H1.plan.md §1c; MO and Mailbox tests excluded).
 * Thread mailboxes are unguarded receives; a Rust tag {@code k} is carried by its own record class
 * ({@code Tg1}, {@code Tg2}) and a tag predicate {@code tag == Some(k)} becomes the type test for
 * that class; an untagged send uses the plain class. {@code assume!} is {@link H1Support#assume}.
 *
 * <p>Arguments: {@code <test name>[,n]}; {@code n} for {@code r_ns_assm} and {@code ns_r_wb}.
 */
public final class Litmus implements Runnable {

    /** An integer payload ({@code i32}/{@code u32}). */
    record I(int v) implements MpMessage {}

    /** A {@code ThreadId} payload. */
    record T(Tid t) implements MpMessage {}

    /** {@code Option<ThreadId>} (test_named_thread sends {@code Some(tid)}). */
    record OptT(Tid t) implements MpMessage {}

    /** An enum variant carrying a thread id ({@code Start}, {@code Init}, {@code TID}). */
    record Id(Tid t) implements MpMessage {}

    /** An enum variant carrying an integer ({@code Int}, {@code Val}), untagged. */
    record V(int v) implements MpMessage {}

    /** {@code Response::Peachy}. */
    record Peachy() implements MpMessage {}

    /** A {@code Val} sent with tag 1. */
    record Tg1(int v) implements MpMessage {}

    /** A {@code Val} sent with tag 2. */
    record Tg2(int v) implements MpMessage {}

    private final String name;
    private final int n;

    /**
     * @param args test name, then {@code n} where the test has one
     */
    public Litmus(String... args) {
        this.name = args[0].trim();
        this.n = args.length > 1 ? Integer.parseInt(args[1].trim()) : 0;
    }

    private static final CommunicationModel F = CommunicationModel.P2P;
    private static final CommunicationModel B = CommunicationModel.ASYN;

    private CommunicationModel m;
    private H1Support.Spawner sp;

    private void s(Tid to, MpMessage v) {
        Channel.send(m, to, v);
    }

    private MpMessage r() {
        return Channel.recv(m);
    }

    private Optional<MpMessage> nb() {
        return Channel.tryRecv(m);
    }

    private void assume(boolean c) {
        H1Support.assume(c, m);
    }

    @Override
    public void run() {
        sp = new H1Support.Spawner();
        m = name.equals("weird_stamp_tiebreak") || name.equals("revisit_opt") ? B : F;
        switch (name) {
            case "r_nondet" -> rNondet();
            case "r_choice_rangeinclusive" -> rChoice(5);
            case "r_choice_range" -> rChoice(4);
            case "test_named_thread" -> namedThread();
            case "r_ss" -> rSs();
            case "r_ss_assm" -> rSsAssm();
            case "r_ns_assm" -> rNsAssm();
            case "r_s_blk" -> rSBlk();
            case "r_rs_s_blk" -> rRsSBlk();
            case "rj_s" -> rjS();
            case "ssrrr_s" -> ssrrrS();
            case "srs_s_rs_nodangling" -> srsSRsNodangling();
            case "r_s_ss" -> rSSs();
            case "sr_s" -> srS();
            case "r_s_r_ss" -> rSRSs(true);
            case "r_s_r_ss_nonblocking" -> rSRSs(false);
            case "s_rsrr_sss_coplacing" -> coplacing();
            case "s_s_s_wb" -> sSSWb();
            case "ns_r_wb" -> nsRWb();
            case "ss_rr_tag", "ss_rr_tag3" -> ssRrTag(2, 1);
            case "ss_rr_tag2" -> ssRrTag(1, 2);
            case "ss_rr_tag_nb" -> ssRrTagNb();
            case "ss_rr_ss_tag" -> ssRrSsTag();
            case "nested_spawn" -> nestedSpawn();
            case "nested_spawn2" -> nestedSpawn2();
            case "nested_spawn3" -> nestedSpawn3();
            case "weird_stamp_tiebreak" -> weirdStampTiebreak();
            case "revisit_opt" -> revisitOpt();
            case "revisit_opt_2" -> revisitOpt2();
            default -> throw new IllegalArgumentException("unknown litmus test " + name);
        }
        sp.awaitAll();
    }

    private void rNondet() {
        Tid h = sp.spawn(() -> nb());
        if (Nondet.chooseBoolean()) {
            s(h, new I(42));
        } else {
            s(h, new I(17));
        }
    }

    /** {@code (0..=4).nondet()} has 5 values, {@code (0..4).nondet()} 4. */
    private void rChoice(int values) {
        Tid h = sp.spawn(() -> nb());
        int v = Nondet.chooseIndex(values);
        if (v == 0) {
            s(h, new I(42));
        } else if (v == 1) {
            s(h, new I(17));
        } else {
            s(h, new I(33));
        }
    }

    private void namedThread() {
        Tid h1 = sp.spawn(() -> nb());
        Tid h2 = sp.spawn(() -> nb());
        Tid h3 = sp.spawn(() -> {
            MpMessage x = r();
            if (x instanceof OptT o) {
                s(o.t(), new I(42));
            } else {
                throw new IllegalStateException("panic!()");
            }
        });
        s(h3, new OptT(h1));
        s(h2, new T(h1));
    }

    private void rSs() {
        Tid rh = sp.spawn(() -> nb());
        Tid sh = sp.spawn(() -> {
            Optional<MpMessage> x = nb();
            if (x.isPresent()) {
                Tid t = ((T) x.get()).t();
                s(t, new I(1));
                s(t, new I(2));
            }
        });
        s(sh, new T(rh));
    }

    private void rSsAssm() {
        Tid rh = sp.spawn(() -> {
            Optional<MpMessage> x = nb();
            assume(x.isPresent());
        });
        Tid sh = sp.spawn(() -> {
            Optional<MpMessage> x = nb();
            assume(x.isPresent());
            Tid t = ((T) x.get()).t();
            s(t, new I(1));
            s(t, new I(2));
        });
        s(sh, new T(rh));
    }

    private void rNsAssm() {
        Tid rh = sp.spawn(() -> {
            Optional<MpMessage> x = nb();
            assume(x.isPresent());
        });
        for (int i = 0; i < n; i++) {
            final int fi = i;
            Tid sh = sp.spawn(() -> {
                Optional<MpMessage> x = nb();
                assume(x.isPresent());
                s(((T) x.get()).t(), new I(fi));
            });
            s(sh, new T(rh));
        }
    }

    private void rSBlk() {
        Tid rh = sp.spawn(() -> r());
        Tid sh = sp.spawn(() -> s(((T) r()).t(), new I(1)));
        s(sh, new T(rh));
    }

    private void rRsSBlk() {
        Tid rh = sp.spawn(() -> r());
        Tid sh1 = sp.spawn(() -> {
            if (r() instanceof Id id) {
                s(id.t(), new V(42));
            }
        });
        Tid sh2 = sp.spawn(() -> {
            if (r() instanceof Id id) {
                s(id.t(), new V(42));
            }
        });
        s(sh1, new Id(rh));
        s(sh2, new Id(sh1));
    }

    private void rjS() {
        Tid me = MpProcess.self();
        int[] ret = new int[1];
        org.mpi_sws.jmc.api.mp.MpThread sh = new org.mpi_sws.jmc.api.mp.MpThread(() -> {
            Tid pid = ((T) r()).t();
            s(pid, new Peachy());
            ret[0] = 42;
        });
        sh.start();
        s(Tid.of(sh.getTaskId()), new T(me));
        try {
            sh.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        if (ret[0] != 42) {
            throw new AssertionError("join result");
        }
        if (!(r() instanceof Peachy)) {
            throw new AssertionError("Peachy");
        }
    }

    private void ssrrrS() {
        Tid h1 = sp.spawn(() -> {
            Tid self = MpProcess.self();
            s(self, new I(42));
            s(self, new I(43));
            r();
            r();
            r();
        });
        Tid h2 = sp.spawn(() -> s(((T) r()).t(), new I(17)));
        s(h2, new T(h1));
    }

    private void srsSRsNodangling() {
        Tid h1 = sp.spawn(() -> nb());
        Tid h2 = sp.spawn(() -> {
            MpMessage x = r();
            if (x instanceof Id id) {
                nb();
                s(id.t(), new V(42));
            } else {
                assume(false);
            }
        });
        Tid h3 = sp.spawn(() -> {
            MpMessage x = r();
            if (x instanceof Id id) {
                s(id.t(), new V(42));
            } else {
                throw new IllegalStateException("panic!()");
            }
        });
        s(h3, new Id(h2));
        s(h2, new Id(h1));
    }

    private void rSSs() {
        Tid rh = sp.spawn(() -> nb());
        Tid sh1 = sp.spawn(() -> {
            MpMessage x = r();
            if (x instanceof Id id) {
                s(id.t(), new V(1));
            } else {
                throw new IllegalStateException("panic!()");
            }
        });
        Tid sh2 = sp.spawn(() -> {
            MpMessage x = r();
            if (x instanceof Id id) {
                s(MpProcess.self(), new V(2));
                s(id.t(), new V(42));
            } else {
                assume(false);
            }
        });
        s(sh1, new Id(sh2));
        s(sh2, new Id(rh));
    }

    private void srS() {
        Tid srh = sp.spawn(() -> {
            Tid self = MpProcess.self();
            s(self, new I(0));
            s(self, new I(1));
            r();
        });
        Tid sh = sp.spawn(() -> s(((T) r()).t(), new I(2)));
        s(sh, new T(srh));
    }

    private void rSRSs(boolean h3Blocking) {
        Tid h1 = sp.spawn(() -> {
            Optional<MpMessage> x = nb();
            assume(x.isPresent() && ((I) x.get()).v() == 42);
        });
        Tid h2 = sp.spawn(() -> s(((T) r()).t(), new I(1)));
        Tid h3 = sp.spawn(() -> {
            if (h3Blocking) {
                r();
            } else {
                nb();
            }
        });
        Tid h4 = sp.spawn(() -> {
            Tid r3 = ((T) r()).t();
            Tid r1 = ((T) r()).t();
            s(r3, new I(2));
            s(r1, new I(42));
        });
        s(h2, new T(h3));
        s(h4, new T(h3));
        s(h4, new T(h1));
    }

    private void coplacing() {
        Tid h1 = sp.spawn(() -> {
            s(MpProcess.self(), new V(0));
            r();
            r();
            r();
        });
        Tid h2 = sp.spawn(() -> {
            MpMessage x = r();
            if (!(x instanceof Id)) {
                assume(false);
            }
            s(((Id) x).t(), new V(42));
        });
        Tid h3 = sp.spawn(() -> {
            MpMessage x = r();
            if (!(x instanceof Id)) {
                assume(false);
            }
            Tid t1 = ((Id) x).t();
            s(t1, new V(1));
            s(t1, new V(2));
        });
        s(h2, new Id(h1));
        s(h3, new Id(h1));
    }

    private void sSSWb() {
        Tid h1 = sp.spawn(() -> s(MpProcess.self(), new V(1)));
        Tid h2 = sp.spawn(() -> {
            MpMessage x = r();
            if (!(x instanceof Id)) {
                assume(false);
            }
            s(((Id) x).t(), new V(2));
        });
        Tid h3 = sp.spawn(() -> {
            MpMessage x = r();
            if (!(x instanceof Id)) {
                assume(false);
            }
            s(((Id) x).t(), new V(42));
        });
        s(h2, new Id(h1));
        s(h3, new Id(h1));
    }

    private void nsRWb() {
        List<Tid> ns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ns.add(sp.spawn(() -> {
                MpMessage x = r();
                if (!(x instanceof Id)) {
                    assume(false);
                }
                s(((Id) x).t(), new V(n));
            }));
        }
        Tid hr = sp.spawn(() -> r());
        for (int i = 0; i < n; i++) {
            s(ns.get(i), new Id(hr));
        }
    }

    private static Class<? extends MpMessage> tag(int k) {
        return k == 1 ? Tg1.class : Tg2.class;
    }

    private static int val(MpMessage x) {
        return x instanceof Tg1 t ? t.v() : ((Tg2) x).v();
    }

    private void ssRrTag(int first, int second) {
        Tid hs = sp.spawn(() -> {
            Tid tr = ((Id) r()).t();
            s(tr, new Tg1(1));
            s(tr, new Tg2(2));
        });
        Tid hr = sp.spawn(() -> {
            int v1 = val(Channel.recv(m, tag(first)));
            int v2 = val(Channel.recv(m, tag(second)));
            if (!(v1 == first && v2 == second)) {
                throw new AssertionError("ss_rr_tag values " + v1 + ", " + v2);
            }
        });
        s(hs, new Id(hr));
    }

    private void ssRrTagNb() {
        Tid hs = sp.spawn(() -> {
            Tid tr = ((Id) r()).t();
            s(tr, new Tg1(1));
            s(tr, new Tg2(2));
        });
        Tid hr = sp.spawn(() -> {
            int v1 = Channel.tryRecv(m, Tg1.class).map(Tg1::v).orElse(100);
            int v2 = Channel.tryRecv(m, Tg2.class).map(Tg2::v).orElse(200);
            if (!((v1 == 100 || v1 == 1) && (v2 == 200 || v2 == 2))) {
                throw new AssertionError("ss_rr_tag_nb values " + v1 + ", " + v2);
            }
        });
        s(hs, new Id(hr));
    }

    private void ssRrSsTag() {
        Tid hprev = sp.spawn(() -> {
            Tid tr = ((Id) r()).t();
            s(tr, new Tg2(2));
            s(tr, new Tg1(1));
        });
        Tid hr = sp.spawn(() -> {
            Channel.recv(m, Tg2.class);
            Channel.recv(m, Tg1.class);
        });
        Tid hs = sp.spawn(() -> {
            Tid tr = ((Id) r()).t();
            s(tr, new Tg1(1));
            s(tr, new V(2));
        });
        s(hprev, new Id(hr));
        s(hs, new Id(hr));
    }

    private void nestedSpawn() {
        Tid t1 = sp.spawn(() -> {
            int x = ((I) r()).v();
            boolean zero = false;
            if (x == 0) {
                zero = true;
                r();
            }
            sp.spawn(() -> { });
            if (zero) {
                r();
            }
        });
        sp.spawn(() -> s(t1, new I(0)));
        sp.spawn(() -> s(t1, new I(1)));
    }

    private void nestedSpawn2() {
        Tid me = MpProcess.self();
        sp.spawn(() -> sp.spawn(() -> { }));
        Tid t2 = sp.spawn(() -> s(((T) r()).t(), new I(1)));
        s(t2, new T(me));
        s(me, new I(0));
        r();
    }

    private void nestedSpawn3() {
        Tid me = MpProcess.self();
        Tid t1 = sp.spawn(() -> {
            Tid mtid = ((T) r()).t();
            Tid t2 = sp.spawn(() -> s(((T) r()).t(), new I(1)));
            s(t2, new T(mtid));
        });
        s(t1, new T(me));
        s(me, new I(0));
        int x = ((I) r()).v();
        if (x == 0) {
            sp.spawn(() -> { });
        }
    }

    private void weirdStampTiebreak() {
        Tid me = MpProcess.self();
        s(me, new I(0));
        Tid t2 = sp.spawn(() -> {
            s(me, new I(2));
            Optional<MpMessage> b = nb();
            if (b.isPresent() && ((I) b.get()).v() == 1) {
                s(me, new I(3));
            }
        });
        sp.spawn(() -> {
            s(me, new I(1));
            s(t2, new I(1));
        });
        assume(((I) r()).v() == 3);
    }

    private void revisitOpt() {
        Tid t1 = sp.spawn(() -> {
            nb();
            nb();
        });
        sp.spawn(() -> s(t1, new I(1)));
        sp.spawn(() -> s(t1, new I(2)));
    }

    private void revisitOpt2() {
        Tid t1 = sp.spawn(() -> {
            Channel.tryRecv(m, Tg2.class);
            r();
        });
        sp.spawn(() -> {
            s(t1, new Tg1(1));
            s(t1, new Tg2(2));
        });
    }
}
