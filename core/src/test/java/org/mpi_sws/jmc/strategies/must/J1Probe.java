package org.mpi_sws.jmc.strategies.must;

import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.lang.reflect.ReflectPermission;
import java.security.Permission;
import java.util.List;

/**
 * J1's constructor- and accessor-access failures, which need a SecurityManager that refuses
 * {@code setAccessible} on a constructor (or on a method) while everything else is allowed. Run
 * by {@link J1MapTidsTest#constructorAccessFailure_underSecurityManager} in a fresh JVM with
 * {@code -Djava.security.manager=allow}. Each fixture class is first seen under the mode it is
 * tested in, so the per-class cache is cold at the first call.
 *
 * <p>Derived (old code): under "ctor", a changed record fails at {@code c.setAccessible(true)}
 * after all its accessors ran (SecurityException, caught: non-strict returns the record, strict
 * throws the IllegalStateException); an unchanged record never looks the constructor up, so
 * nothing fails. Under "accessor", the first accessor's {@code setAccessible} fails before any
 * accessor runs. The decision is re-made on every value: when the policy changes between two
 * calls (allow to refuse, refuse to allow) or inside a walk (in the first accessor), the outcome
 * follows the policy in force at each {@code setAccessible}, cold or warm cache alike.
 */
@SuppressWarnings("removal")
public final class J1Probe {

    private J1Probe() {}

    /** The refusal mode: "" (none), "ctor" or "accessor". */
    static volatile String deny = "";

    /** Refuses suppressAccessChecks when the caller is Constructor.setAccessible / Method.setAccessible. */
    static final class DenySm extends SecurityManager {
        @Override
        public void checkPermission(Permission p) {
            String d = deny;
            if (d.equals("declared")) {
                // Codex round 02: refuse accessDeclaredMembers when mapTids (either version) asks for a
                // record's components; suppressAccessChecks stays allowed
                if (p instanceof RuntimePermission && "accessDeclaredMembers".equals(p.getName())) {
                    StackTraceElement[] st = new Throwable().getStackTrace();
                    for (int i = 0; i + 1 < st.length; i++) {
                        if (st[i].getClassName().equals("java.lang.Class")
                                && st[i].getMethodName().equals("getRecordComponents")) {
                            String caller = st[i + 1].getClassName();
                            if (caller.startsWith(MustStrategy.class.getName())
                                    || caller.equals(J1MapTidsTest.Ref.class.getName())) {
                                throw new SecurityException("refused by the test: declared members");
                            }
                            return;
                        }
                    }
                }
                return;
            }
            if (d.isEmpty() || !(p instanceof ReflectPermission) || !"suppressAccessChecks".equals(p.getName())) {
                return;
            }
            String cls = d.equals("ctor") ? "java.lang.reflect.Constructor" : "java.lang.reflect.Method";
            // only when mapTids (either version) calls setAccessible itself, never for the JDK's own
            StackTraceElement[] st = new Throwable().getStackTrace();
            for (int i = 0; i + 1 < st.length; i++) {
                if (st[i].getClassName().equals(cls) && st[i].getMethodName().equals("setAccessible")) {
                    String caller = st[i + 1].getClassName();
                    if (caller.startsWith(MustStrategy.class.getName()) || caller.equals(J1MapTidsTest.Ref.class.getName())) {
                        throw new SecurityException("refused by the test: " + d + " access");
                    }
                    return;
                }
            }
        }

        @Override
        public void checkPermission(Permission p, Object context) {
            checkPermission(p);
        }
    }

    /** First seen under "ctor". */
    public record CtorA(Tid t, int n) implements MpMessage {
        public CtorA {
            J1MapTidsTest.CTOR.incrementAndGet();
        }

        public Tid t() {
            J1MapTidsTest.ACC.incrementAndGet();
            return t;
        }

        public int n() {
            J1MapTidsTest.ACC.incrementAndGet();
            return n;
        }
    }

    /** First seen under "ctor", unchanged only. */
    public record CtorU(int n) implements MpMessage {
        public int n() {
            J1MapTidsTest.ACC.incrementAndGet();
            return n;
        }
    }

    /** First seen under "accessor". */
    public record AccA(Tid t, int n) implements MpMessage {
        public Tid t() {
            J1MapTidsTest.ACC.incrementAndGet();
            return t;
        }

        public int n() {
            J1MapTidsTest.ACC.incrementAndGet();
            return n;
        }
    }

    /** Seen first with no refusal, then under "ctor". */
    public record Toggle1(Tid t) implements MpMessage {}

    /** Seen first under "ctor", then with no refusal. */
    public record Toggle2(Tid t) implements MpMessage {}

    /** Seen first with no refusal, then under "accessor". */
    public record Toggle3(Tid t) implements MpMessage {}

    /** Seen first under "accessor", then with no refusal. */
    public record Toggle4(Tid t) implements MpMessage {}

    /** When set, the first accessor of a Flip record switches {@link #deny} to this mode. */
    static volatile String flip;

    static Tid flipping(Tid t) {
        J1MapTidsTest.ACC.incrementAndGet();
        if (flip != null) {
            deny = flip;
            flip = null;
        }
        return t;
    }

    static int counted(int n) {
        J1MapTidsTest.ACC.incrementAndGet();
        return n;
    }

    static Tid countedTid(Tid t) {
        J1MapTidsTest.ACC.incrementAndGet();
        return t;
    }

    /** Flip records, one class per case so that each starts cold. */
    public record FlipA1(Tid t, int n, Tid u) implements MpMessage {
        public Tid t() {
            return flipping(t);
        }

        public int n() {
            return counted(n);
        }

        public Tid u() {
            return countedTid(u);
        }
    }

    /** See {@link FlipA1}. */
    public record FlipA2(Tid t, int n, Tid u) implements MpMessage {
        public Tid t() {
            return flipping(t);
        }

        public int n() {
            return counted(n);
        }

        public Tid u() {
            return countedTid(u);
        }
    }

    /** See {@link FlipA1}. */
    public record FlipC1(Tid t, int n, Tid u) implements MpMessage {
        public FlipC1 {
            J1MapTidsTest.CTOR.incrementAndGet();
        }

        public Tid t() {
            return flipping(t);
        }

        public int n() {
            return counted(n);
        }

        public Tid u() {
            return countedTid(u);
        }
    }

    /** See {@link FlipA1}. */
    public record FlipC2(Tid t, int n, Tid u) implements MpMessage {
        public FlipC2 {
            J1MapTidsTest.CTOR.incrementAndGet();
        }

        public Tid t() {
            return flipping(t);
        }

        public int n() {
            return counted(n);
        }

        public Tid u() {
            return countedTid(u);
        }
    }

    static int failed;

    /** A {@code record name(Object o)} compiled now and loaded by a loader whose parent is the bootstrap loader. */
    static Object independentRecord(String name) throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir")), "j1-indep-");
        java.nio.file.Path src = dir.resolve(name + ".java");
        java.nio.file.Files.writeString(src, "public record " + name + "(Object o) {}\n");
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", dir.toString(),
                src.toString());
        expect(rc == 0, "javac " + name);
        java.net.URLClassLoader l = new java.net.URLClassLoader(new java.net.URL[] {dir.toUri().toURL()}, null);
        Class<?> c = l.loadClass(name);
        expect(c.getClassLoader() == l && c.isRecord(), "independent record class");
        return c.getConstructor(Object.class).newInstance(List.of(Tid.of(4)));
    }

    static void check(String name, Runnable r) {
        try {
            r.run();
            System.out.println("PASS " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL " + name + ": " + t);
        }
    }

    static void expect(boolean c, String what) {
        if (!c) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) throws Exception {
        // load what the cases use before any refusal
        J1MapTidsTest.run(true, Tid.of(1), J1MapTidsTest.SHIFT, false);
        J1MapTidsTest.run(false, Tid.of(1), J1MapTidsTest.SHIFT, false);
        // Codex round 02: records from an independent class loader (parent: the bootstrap loader), so
        // that getRecordComponents checks accessDeclaredMembers for mapTids' caller class. "warm" is
        // mapped before the SecurityManager exists (the cache is warm), "cold" only after.
        Object indepWarm = independentRecord("IndepWarm");
        Object indepCold = independentRecord("IndepCold");
        J1MapTidsTest.same("indep-warm-up", indepWarm, J1MapTidsTest.SHIFT, false);
        J1MapTidsTest.same("indep-warm-up", indepWarm, J1MapTidsTest.SHIFT, true);
        System.setSecurityManager(new DenySm());
        System.out.println("SM " + (System.getSecurityManager() != null));

        // Derived (old code): getRecordComponents runs outside mapTids' try, so its SecurityException
        // escapes in both modes, unwrapped, before any accessor; the cache must not hide it.
        deny = "declared";
        for (boolean strict : new boolean[] {false, true}) {
            for (Object in : new Object[] {indepWarm, indepCold}) {
                check("declared-members " + in.getClass().getSimpleName() + " strict=" + strict, () -> {
                    List<J1MapTidsTest.Outcome> o = J1MapTidsTest.same("declared-" + in.getClass().getSimpleName(), in,
                            J1MapTidsTest.SHIFT, strict);
                    expect(o.get(0).thrown().equals("java.lang.SecurityException: refused by the test: declared members"),
                            "the SecurityException escapes: " + o.get(0).thrown());
                    expect(o.get(0).accessorCalls() == 0, "no accessor ran");
                });
            }
        }
        deny = "";
        check("declared-members lifted", () -> J1MapTidsTest.same("declared-lifted", indepWarm,
                J1MapTidsTest.SHIFT, true));

        deny = "ctor";
        for (boolean strict : new boolean[] {false, true}) {
            check("ctor-changed strict=" + strict, () -> {
                List<J1MapTidsTest.Outcome> o = J1MapTidsTest.same("ctorA", new CtorA(Tid.of(2), 3),
                        J1MapTidsTest.SHIFT, strict);
                expect(o.get(0).accessorCalls() == 2, "both accessors ran");
                expect(o.get(0).ctorCalls() == 0, "no construction");
                expect(strict ? o.get(0).thrown().contains("java.lang.SecurityException: refused by the test: ctor")
                        : o.get(0).result().startsWith("="), o.get(0).toString());
            });
            check("ctor-unchanged strict=" + strict, () -> {
                List<J1MapTidsTest.Outcome> o = J1MapTidsTest.same("ctorU", new CtorU(4), J1MapTidsTest.SHIFT,
                        strict);
                expect(o.get(0).thrown().equals("none"), "unchanged: no constructor lookup, nothing thrown");
                expect(o.get(0).result().startsWith("="), "the same instance");
            });
            check("ctor-nested strict=" + strict, () -> J1MapTidsTest.same("ctor-in-list",
                    List.of(Tid.of(5), new CtorA(Tid.of(6), 0), new CtorU(1)), J1MapTidsTest.SHIFT, strict));
        }
        deny = "accessor";
        for (boolean strict : new boolean[] {false, true}) {
            check("accessor strict=" + strict, () -> {
                List<J1MapTidsTest.Outcome> o = J1MapTidsTest.same("accA", new AccA(Tid.of(2), 3),
                        J1MapTidsTest.SHIFT, strict);
                expect(o.get(0).accessorCalls() == 0, "no accessor ran");
                expect(strict ? o.get(0).thrown().contains("refused by the test: accessor")
                        : o.get(0).result().startsWith("="), o.get(0).toString());
            });
        }

        // The access decision changes between calls: the reference re-decides on every value, and
        // so must the implementation (a cached decision would answer the earlier policy).
        String[][] toggles = {{"", "ctor"}, {"ctor", ""}, {"", "accessor"}, {"accessor", ""}};
        Object[] toggleInputs = {new Toggle1(Tid.of(1)), new Toggle2(Tid.of(1)), new Toggle3(Tid.of(1)),
            new Toggle4(Tid.of(1))};
        for (int k = 0; k < toggles.length; k++) {
            String[] tg = toggles[k];
            Object in = toggleInputs[k];
            check("toggle " + (tg[0].isEmpty() ? "allow" : tg[0]) + "->" + (tg[1].isEmpty() ? "allow" : tg[1]), () -> {
                for (String policy : tg) {
                    deny = policy;
                    for (boolean strict : new boolean[] {false, true}) {
                        J1MapTidsTest.same("toggle-" + in.getClass().getSimpleName() + "-" + policy, in,
                                J1MapTidsTest.SHIFT, strict);
                    }
                }
            });
        }
        // The policy changes during the walk, in the first accessor (cold cache, then warm): the
        // reference opens each accessor just before calling it, so the change takes effect at the
        // second accessor (flip to "accessor": 1 accessor call; flip to "ctor": every accessor runs,
        // the constructor cannot be opened).
        for (String flipTo : new String[] {"accessor", "ctor"}) {
            for (boolean strict : new boolean[] {false, true}) {
                Object in = flipTo.equals("accessor") ? (strict ? new FlipA1(Tid.of(1), 2, Tid.of(3))
                        : new FlipA2(Tid.of(1), 2, Tid.of(3))) : (strict ? new FlipC1(Tid.of(1), 2, Tid.of(3))
                        : new FlipC2(Tid.of(1), 2, Tid.of(3)));
                check("flip-in-walk to " + flipTo + " strict=" + strict, () -> {
                    J1MapTidsTest.Outcome[] o = new J1MapTidsTest.Outcome[4];
                    boolean[] underTest = {true, false, true, false};
                    for (int i = 0; i < 4; i++) {
                        deny = "";
                        flip = flipTo;
                        o[i] = J1MapTidsTest.run(underTest[i], in, J1MapTidsTest.SHIFT, strict);
                    }
                    deny = "";
                    flip = null;
                    System.out.println("CASE flip " + flipTo + " strict=" + strict + "\n  cold " + o[0]
                            + "\n  ref  " + o[1] + "\n  warm " + o[2]);
                    expect(o[1].equals(o[3]), "reference deterministic");
                    expect(o[0].equals(o[1]), "cold equals the reference: " + o[0] + " vs " + o[1]);
                    expect(o[2].equals(o[1]), "warm equals the reference: " + o[2] + " vs " + o[1]);
                    expect(o[1].accessorCalls() == (flipTo.equals("accessor") ? 1 : 3), "derived accessor calls");
                    expect(o[1].ctorCalls() == 0, "no construction");
                });
            }
        }
        deny = "";
        System.out.println("DONE failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
