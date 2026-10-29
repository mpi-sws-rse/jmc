package org.mpi_sws.jmc.mp.programs.j2b;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tester fixtures for J2b round 01 (plan/jmc-must/log/dev/J2b/round-01.prompt.md): programs outside
 * the summary format's shape (main communicates, spawns after joins, processes spawn and await), run
 * under the counting {@code must} constructor, {@code mp-random}, or the launcher's counting mode
 * ({@code J2bPrograms:<name>}). The derived counts are in the test class and the report.
 */
public final class J2bPrograms implements Runnable {

    /** A payload. */
    public record I(int v) implements MpMessage {}

    /** A payload naming a task. */
    public record Req(Tid from) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;
    private int runs;

    /** The launcher's no-argument form: {@code f3_spawn_edge}. */
    public J2bPrograms() {
        this("f3_spawn_edge");
    }

    /**
     * A named program.
     *
     * @param args the program's name
     */
    public J2bPrograms(String... args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("J2bPrograms takes <name>");
        }
        this.name = args[0].trim();
    }

    /** Construct-and-start with a registry for a final await of everything (as H1Support.Spawner). */
    static final class Spawner {
        private final List<MpThread> all = new ArrayList<>();

        Tid spawn(Runnable body) {
            MpThread t = create(body);
            t.start();
            return Tid.of(t.getTaskId());
        }

        MpThread create(Runnable body) {
            MpThread t = new MpThread(body);
            synchronized (all) {
                all.add(t);
            }
            return t;
        }

        void awaitAll() {
            for (int i = 0; ; i++) {
                MpThread t;
                synchronized (all) {
                    if (i >= all.size()) {
                        return;
                    }
                    t = all.get(i);
                }
                join(t);
            }
        }
    }

    static void join(MpThread t) {
        try {
            t.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void run() {
        Spawner sp = new Spawner();
        Tid me = MpProcess.self();
        boolean even = runs++ % 2 == 0;
        switch (name) {
            // ---------------------------------------------------------------- F3
            case "f3_spawn_edge" -> {
                Channel.tryRecv(M);
                sp.spawn(() -> Channel.send(M, me, new I(1)));
            }
            case "f3_join_edge" -> {
                MpThread a = sp.create(() -> Channel.tryRecv(M));
                a.start();
                join(a);
                Channel.send(M, Tid.of(a.getTaskId()), new I(1));
            }
            case "f3_eventless_parent" -> {
                // main spawns A; joins A; then (no event of its own) spawns P, whose send reaches A
                MpThread a = sp.create(() -> Channel.tryRecv(M));
                a.start();
                join(a);
                Tid at = Tid.of(a.getTaskId());
                sp.spawn(() -> Channel.send(M, at, new I(1)));
            }
            case "f3_join_spawn_chain" -> {
                // main spawns A; joins A; spawns B; B (eventless) spawns C; C sends to A
                MpThread a = sp.create(() -> Channel.tryRecv(M));
                a.start();
                join(a);
                Tid at = Tid.of(a.getTaskId());
                sp.spawn(() -> {
                    MpThread c = new MpThread(() -> Channel.send(M, at, new I(1)));
                    c.start();
                    join(c);
                });
            }
            case "f3_no_false_block_spawn_order" -> {
                // A spawned before S: the spawn edge into S carries nothing of A
                Tid at = sp.spawn(() -> Channel.tryRecv(M));
                sp.spawn(() -> Channel.send(M, at, new I(1)));
            }
            case "f3_no_false_block_unrelated_join" -> {
                // main joins B (whose send goes to D), then spawns S, which sends to A
                Tid dt = sp.spawn(() -> { });
                Tid at = sp.spawn(() -> Channel.tryRecv(M));
                MpThread b = sp.create(() -> Channel.send(M, dt, new I(9)));
                b.start();
                join(b);
                sp.spawn(() -> Channel.send(M, at, new I(1)));
            }
            // ---------------------------------------------------------------- F2
            case "f2_nested_spawn" -> {
                sp.spawn(() -> sp.spawn(() -> Channel.send(M, me, new I(1))));
                Optional<MpMessage> x = Channel.tryRecv(M);
                if (x.isEmpty()) {
                    sp.spawn(() -> { });
                }
            }
            case "f2_conditional_spawn" -> {
                sp.spawn(() -> Channel.send(M, me, new I(1)));
                Optional<MpMessage> x = Channel.tryRecv(M);
                if (x.isEmpty()) {
                    sp.spawn(() -> { });
                }
                sp.spawn(() -> Channel.send(M, me, new I(2)));
                Channel.tryRecv(M);
            }
            case "f2_tid_payload" -> {
                // f2_nested_spawn, the nested process sending its own (runtime-renumbered) tid
                sp.spawn(() -> sp.spawn(() -> Channel.send(M, me, new Req(MpProcess.self()))));
                Optional<MpMessage> x = Channel.tryRecv(M);
                if (x.isEmpty()) {
                    sp.spawn(() -> { });
                }
            }
            case "f2_send_to_unstarted" -> {
                MpThread t = sp.create(() -> Channel.recv(M));
                Tid tt = Tid.of(t.getTaskId());
                Channel.send(M, tt, new I(1));
                sp.spawn(() -> Channel.send(M, tt, new I(2)));
                t.start();
            }
            case "f2_nondeterministic_spawn" -> {
                Tid rt = sp.spawn(() -> Channel.recv(M));
                sp.spawn(() -> Channel.send(M, rt, new I(1)));
                if (even) {
                    sp.spawn(() -> Channel.send(M, rt, new I(2)));
                }
            }
            case "f2_guard_on_tid_start_order" -> {
                // R created first (runtime 2), T2 second (runtime 3); T2 started first (name m/0)
                Tid[] t2 = new Tid[1];
                MpThread r = sp.create(() -> Channel.recv(M,
                        (MpMessage m) -> m instanceof Req q && q.from().equals(t2[0])));
                Tid rt = Tid.of(r.getTaskId());
                MpThread t = sp.create(() -> Channel.send(M, rt, new Req(MpProcess.self())));
                t2[0] = Tid.of(t.getTaskId());
                t.start();
                r.start();
            }
            case "f2_guard_on_placeholder_payload" -> {
                // main names T in a payload before T starts; T's guard checks the payload names T (the
                // guard captures T's tid: a guard runs in the strategy, where MpProcess.self() has no task)
                Tid[] self = new Tid[1];
                MpThread t = sp.create(() -> Channel.recv(M, (MpMessage m) -> m instanceof Req q
                        && q.from().equals(self[0])));
                Tid tt = Tid.of(t.getTaskId());
                self[0] = tt;
                Channel.send(M, tt, new Req(tt));
                t.start();
            }
            // ---------------------------------------------------------------- round 02: J2b-F1
            case "f1_later_iteration_guard" -> {
                // A creates H and G and starts G first (stable != runtime). G: its tid to H, then tryRecv
                // with the guard "payload names me" (me captured at G's start), then I(1) to main; H
                // answers G with Req(G). main's tryRecv reads ⊥ (then spawns E, which shifts the runtime
                // ids of A's children in the iterations where main parks at x) or G's I(1). In the
                // iteration that replays x = I(1), G's receive keeps its iteration-0 guard while H's send
                // is free: the guard is evaluated there under changed ids (the candidate is then refused
                // by the maximality condition, since G's I(1), read by x, would be deleted).
                sp.spawn(() -> {
                    Tid[] h = new Tid[1];
                    MpThread g = sp.create(() -> {
                        Tid gme = MpProcess.self();
                        Channel.send(M, h[0], new Req(gme));
                        Channel.tryRecv(M, (MpMessage m) -> m instanceof Req q && q.from().equals(gme));
                        Channel.send(M, me, new I(1));
                    });
                    MpThread hh = sp.create(() -> {
                        Req q = (Req) Channel.recv(M);
                        Channel.send(M, q.from(), new Req(q.from()));
                    });
                    h[0] = Tid.of(hh.getTaskId());
                    g.start();
                    hh.start();
                });
                Optional<MpMessage> x = Channel.tryRecv(M);
                if (x.isEmpty()) {
                    sp.spawn(() -> { });
                }
            }
            case "f1_payload_never_started" -> {
                // T is created and never started (not registered for the final await)
                MpThread t = new MpThread(() -> { });
                Tid tt = Tid.of(t.getTaskId());
                Tid rt = sp.spawn(() -> Channel.recv(M));
                sp.spawn(() -> Channel.send(M, rt, new Req(tt)));
                sp.spawn(() -> Channel.send(M, rt, new I(2)));
            }
            case "f1_guard_on_unstarted_payload" -> {
                // R's guard accepts only a Req naming T; T is created and never started, so S1's payload
                // stays a placeholder whenever R's guard sees it
                MpThread t = new MpThread(() -> { });
                Tid tt = Tid.of(t.getTaskId());
                Tid rt = sp.spawn(() -> Channel.recv(M, (MpMessage m) -> m instanceof Req q && q.from().equals(tt)));
                sp.spawn(() -> Channel.send(M, rt, new Req(tt)));
            }
            case "f1_payload_unstarted_then_started", "f1_deferred_drift" -> {
                // S1's payload names T, which main starts only after S1 has sent; in the drift variant
                // main starts an extra U before T on odd runs, so T's creation path differs
                Tid rt = sp.spawn(() -> Channel.recv(M));
                MpThread t = sp.create(() -> { });
                Tid tt = Tid.of(t.getTaskId());
                MpThread s1 = sp.create(() -> Channel.send(M, rt, new Req(tt)));
                s1.start();
                sp.spawn(() -> Channel.send(M, rt, new I(2)));
                join(s1);
                if (name.equals("f1_deferred_drift") && !even) {
                    sp.spawn(() -> { });
                }
                t.start();
            }
            // ---------------------------------------------------------------- round 03: Codex M1-M3, R1
            case "m1_eventful_join_chain" -> {
                // C: send(C, I); await(R) / R: tryRecv() / main: await(C); send(R, I); all started first
                MpThread r = sp.create(() -> Channel.tryRecv(M));
                Tid rt = Tid.of(r.getTaskId());
                MpThread[] c = new MpThread[1];
                c[0] = sp.create(() -> {
                    Channel.send(M, MpProcess.self(), new I(0));
                    join(r);
                });
                r.start();
                c[0].start();
                join(c[0]);
                Channel.send(M, rt, new I(1));
            }
            case "m2_guard_unstarted_forward", "m2_guard_unstarted_backward" -> {
                // T created, never started; S1 and S2 send Req(T) to R, whose blocking receive accepts only
                // Req(T); N makes an independent binary choice. Forward: R created last (both sends precede
                // its receive). Backward: R between S1 and S2 (R reads S1, S2's send revisits it).
                MpThread t = new MpThread(() -> { });
                Tid tt = Tid.of(t.getTaskId());
                Tid[] rt = new Tid[1];
                boolean backward = name.endsWith("backward");
                MpThread s1 = sp.create(() -> Channel.send(M, rt[0], new Req(tt)));
                MpThread r = null;
                if (backward) {
                    r = sp.create(() -> Channel.recv(M, (MpMessage m) -> m instanceof Req q && q.from().equals(tt)));
                    rt[0] = Tid.of(r.getTaskId());
                }
                MpThread s2 = sp.create(() -> Channel.send(M, rt[0], new Req(tt)));
                MpThread n = sp.create(() -> Nondet.chooseBoolean());
                if (!backward) {
                    r = sp.create(() -> Channel.recv(M, (MpMessage m) -> m instanceof Req q && q.from().equals(tt)));
                    rt[0] = Tid.of(r.getTaskId());
                }
                s1.start();
                if (backward) {
                    r.start();
                }
                s2.start();
                n.start();
                if (!backward) {
                    r.start();
                }
            }
            case "m3_dst_before_start", "m3_dst_identity_changed" -> {
                // create T; send(T, I); nondet(2); start T; await T (T receives the I). Negative control: on
                // odd runs main creates U before T, so T's creation path differs between guide and replay.
                if (name.equals("m3_dst_identity_changed") && !even) {
                    new MpThread(() -> { });
                }
                MpThread t = sp.create(() -> Channel.recv(M));
                Channel.send(M, Tid.of(t.getTaskId()), new I(1));
                Nondet.chooseBoolean();
                t.start();
            }
            case "m3_payload_before_start", "m3_payload_identity_changed" -> {
                // R receives; create T; send(R, Req(T)); nondet(2); start T. Negative control as above.
                Tid rt = sp.spawn(() -> Channel.recv(M));
                if (name.equals("m3_payload_identity_changed") && !even) {
                    new MpThread(() -> { });
                }
                MpThread t = sp.create(() -> { });
                Channel.send(M, rt, new Req(Tid.of(t.getTaskId())));
                Nondet.chooseBoolean();
                t.start();
            }
            case "r1_identity_changed_before_t", "r1_identity_changed_after_t" -> {
                // R receives once; S1's payload names T; S2 sends I(2). On odd runs main creates an unstarted
                // U before T (T's creation path changes: detectable) or after T (it does not: not drift).
                Tid rt = sp.spawn(() -> Channel.recv(M));
                Tid[] th = new Tid[1];
                MpThread s1 = sp.create(() -> Channel.send(M, rt, new Req(th[0])));
                sp.spawn(() -> Channel.send(M, rt, new I(2)));
                boolean before = name.endsWith("before_t");
                if (before && !even) {
                    new MpThread(() -> { });
                }
                MpThread t = sp.create(() -> { });
                th[0] = Tid.of(t.getTaskId());
                if (!before && !even) {
                    new MpThread(() -> { });
                }
                s1.start();
                t.start();
            }
            case "r1_guard_on_tid_shifted" -> {
                // A's tryRecv x reads ⊥ (then A creates an unstarted E, A's child) or S0's message; main awaits
                // A, then creates R and T2. E shifts R's and T2's runtime ids, not their names (main's
                // creation indices), so stable != runtime in the iteration where x reads S0. T2 sends
                // Req(self); R's guard accepts only Req(T2's runtime tid).
                MpThread a = sp.create(() -> {
                    if (Channel.tryRecv(M).isEmpty()) {
                        new MpThread(() -> { });
                    }
                });
                Tid at = Tid.of(a.getTaskId());
                a.start();
                sp.spawn(() -> Channel.send(M, at, new I(0)));
                join(a);
                Tid[] t2 = new Tid[1];
                MpThread r = sp.create(() -> Channel.recv(M,
                        (MpMessage m) -> m instanceof Req q && q.from().equals(t2[0])));
                Tid rt = Tid.of(r.getTaskId());
                MpThread t = sp.create(() -> Channel.send(M, rt, new Req(MpProcess.self())));
                t2[0] = Tid.of(t.getTaskId());
                r.start();
                t.start();
            }
            // ---------------------------------------------------------------- round 02: owner's Q1
            case "q1_caught_failure" -> {
                MpThread f = sp.create(() -> {
                    throw new IllegalStateException("f fails");
                });
                f.start();
                try {
                    f.join1();
                } catch (Throwable caught) {
                    // the program swallows what the await raises
                }
                return;
            }
            case "q1_unawaited_main_runs_on" -> {
                sp.spawn(() -> {
                    throw new IllegalStateException("f fails");
                });
                sp.spawn(() -> Channel.send(M, me, new I(1)));
                Channel.recv(M);
                Channel.send(M, me, new I(2));
                throw new AssertionError("main ran past its next event");
            }
            case "q1_unawaited_no_assert" -> {
                // mp-random variant: whether F fails before main's send depends on the schedule, so no
                // assertion; the failure must be the iteration's error in every schedule
                sp.spawn(() -> {
                    throw new IllegalStateException("f fails");
                });
                sp.spawn(() -> Channel.send(M, me, new I(1)));
                Channel.recv(M);
                Channel.send(M, me, new I(2));
            }
            case "q1_parked_main_resumed" -> {
                sp.spawn(() -> {
                    throw new IllegalStateException("f fails");
                });
                sp.spawn(() -> Channel.send(M, me, new I(1)));
                Channel.recv(M);
                throw new AssertionError("main ran past its receive");
            }
            case "q1_assertion_wins" -> {
                sp.spawn(() -> {
                    throw new IllegalStateException("f fails");
                });
                throw new AssertionError("main asserts");
            }
            // ---------------------------------------------------------------- F4
            case "f4_join_blocked" -> {
                MpThread a = sp.create(() -> Channel.recv(M));
                a.start();
                join(a);
                throw new AssertionError("main ran past the join of a blocked thread");
            }
            case "f34_server" -> {
                MpThread c = sp.create(() -> {
                    MpMessage x = Channel.recv(M);
                    if (x instanceof Req r) {
                        Channel.send(M, r.from(), new I(0));
                    }
                });
                c.start();
                Tid cid = Tid.of(c.getTaskId());
                MpThread p = sp.create(() -> {
                    Channel.send(M, cid, new Req(MpProcess.self()));
                    Channel.recv(M);
                });
                p.start();
                join(p);
                Channel.send(M, cid, new I(-1));
                join(c);
            }
            case "f4_transitive" -> {
                MpThread a = sp.create(() -> Channel.recv(M));
                a.start();
                MpThread b = sp.create(() -> {
                    join(a);
                    throw new AssertionError("process ran past the await of a blocked process");
                });
                b.start();
                join(a);
                join(b);
                throw new AssertionError("main ran past the await of a blocked process");
            }
            case "f4_hidden_failure" -> {
                MpThread a = sp.create(() -> Channel.recv(M));
                a.start();
                MpThread f = sp.create(() -> {
                    throw new IllegalStateException("f fails");
                });
                f.start();
                join(a);
                join(f);
            }
            default -> throw new IllegalArgumentException("unknown program " + name);
        }
        sp.awaitAll();
    }
}
