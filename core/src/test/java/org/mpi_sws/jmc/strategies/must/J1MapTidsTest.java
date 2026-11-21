package org.mpi_sws.jmc.strategies.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * JMC-SPEED-2 tester round 01 (plan/jmc-must/log/dev/SPEED2/round-01.prompt.md item 2): J1's
 * {@code MustStrategy.mapTids} (record shapes cached per class) against the reflective {@code
 * mapTids} of 3c43b2d, copied verbatim into {@link Ref}. Every fixture counts its accessor and
 * canonical-constructor calls; a case passes when result (structure, identity of every unchanged
 * part), exception (class and message, the whole cause chain) and both counts equal the
 * reference's, on a cold and on a warm cache. The access-failure cases use records of a module
 * built at test time that does not open its packages; the constructor-access cases need a
 * SecurityManager and run in {@link J1Probe} in a fresh JVM.
 *
 * <p>Derivation, for the old code (the oracle the new one must equal): a record walk calls the
 * accessors in component order, each once, recursing into each value before the next accessor;
 * the constructor is looked up and called only when some component changed; every {@code
 * ReflectiveOperationException} and {@code RuntimeException} raised in the walk, including an
 * {@code Error} or exception of an accessor or constructor (wrapped in {@code
 * InvocationTargetException} by {@code Method.invoke}/{@code Constructor.newInstance}), is caught
 * at the innermost record: non-strict returns that record unchanged, strict throws {@code
 * IllegalStateException("cannot normalise the thread ids in <class>: <ex>")}. A {@code Tid} is
 * always "changed" when {@code f} returns a new instance.
 */
class J1MapTidsTest {

    // ------------------------------------------------------------------ counters and fixtures

    /** Accessor calls of the counting fixtures. */
    static final AtomicInteger ACC = new AtomicInteger();
    /** Canonical-constructor calls of the counting fixtures. */
    static final AtomicInteger CTOR = new AtomicInteger();
    /** 1: the throwing fixtures throw a RuntimeException; 2: an Error; 0: they do not throw. */
    static volatile int boom;

    /** An Error, so the test can tell it from the JDK's. */
    static final class BoomError extends Error {
        BoomError(String m) {
            super(m);
        }
    }

    static void maybeBoom(String where) {
        if (boom == 1) {
            throw new IllegalArgumentException("runtime boom in " + where);
        }
        if (boom == 2) {
            throw new BoomError("error boom in " + where);
        }
    }

    /** Primitive components of every kind, and a Tid. */
    public record Prims(Tid t, int i, long l, boolean b, char c, double d, byte y, short s, float f)
            implements MpMessage {
        public Prims {
            CTOR.incrementAndGet();
        }

        public Tid t() {
            ACC.incrementAndGet();
            return t;
        }

        public int i() {
            ACC.incrementAndGet();
            return i;
        }

        public long l() {
            ACC.incrementAndGet();
            return l;
        }

        public boolean b() {
            ACC.incrementAndGet();
            return b;
        }

        public char c() {
            ACC.incrementAndGet();
            return c;
        }

        public double d() {
            ACC.incrementAndGet();
            return d;
        }

        public byte y() {
            ACC.incrementAndGet();
            return y;
        }

        public short s() {
            ACC.incrementAndGet();
            return s;
        }

        public float f() {
            ACC.incrementAndGet();
            return f;
        }
    }

    /** No Tid anywhere: the walk returns the same instance and never constructs. */
    public record NoTid(int a, String b, List<Object> l) implements MpMessage {
        public NoTid {
            CTOR.incrementAndGet();
        }

        public int a() {
            ACC.incrementAndGet();
            return a;
        }

        public String b() {
            ACC.incrementAndGet();
            return b;
        }

        public List<Object> l() {
            ACC.incrementAndGet();
            return l;
        }
    }

    /** The inner record of {@link Outer}. */
    public record Inner(Tid t, List<Tid> ts) {
        public Inner {
            CTOR.incrementAndGet();
        }

        public Tid t() {
            ACC.incrementAndGet();
            return t;
        }

        public List<Tid> ts() {
            ACC.incrementAndGet();
            return ts;
        }
    }

    /** Nested records: a changed inner, an unchanged inner, a string. */
    public record Outer(Inner in, NoTid same, String s) implements MpMessage {
        public Outer {
            CTOR.incrementAndGet();
        }

        public Inner in() {
            ACC.incrementAndGet();
            return in;
        }

        public NoTid same() {
            ACC.incrementAndGet();
            return same;
        }

        public String s() {
            ACC.incrementAndGet();
            return s;
        }
    }

    /** Tids inside List, Set and Optional, an empty Optional, and containers mapTids does not enter. */
    public record Colls(List<Object> l, Set<Object> s, Optional<Object> o, Optional<Object> empty,
            Map<String, Tid> m, Tid[] arr) implements MpMessage {
        public Colls {
            CTOR.incrementAndGet();
        }

        public List<Object> l() {
            ACC.incrementAndGet();
            return l;
        }

        public Set<Object> s() {
            ACC.incrementAndGet();
            return s;
        }

        public Optional<Object> o() {
            ACC.incrementAndGet();
            return o;
        }

        public Optional<Object> empty() {
            ACC.incrementAndGet();
            return empty;
        }

        public Map<String, Tid> m() {
            ACC.incrementAndGet();
            return m;
        }

        public Tid[] arr() {
            ACC.incrementAndGet();
            return arr;
        }
    }

    /** A private nested record. */
    private record Priv(Tid t, int n) implements MpMessage {
        private Priv {
            CTOR.incrementAndGet();
        }

        @Override
        public Tid t() {
            ACC.incrementAndGet();
            return t;
        }

        @Override
        public int n() {
            ACC.incrementAndGet();
            return n;
        }
    }

    /** The second accessor throws (by {@link #boom}); the first has run by then. */
    public record ThrowsInAccessor(Tid t, int x, Tid u) implements MpMessage {
        public ThrowsInAccessor {
            CTOR.incrementAndGet();
        }

        public Tid t() {
            ACC.incrementAndGet();
            return t;
        }

        public int x() {
            ACC.incrementAndGet();
            maybeBoom("ThrowsInAccessor.x");
            return x;
        }

        public Tid u() {
            ACC.incrementAndGet();
            return u;
        }
    }

    /** The canonical constructor throws (by {@link #boom}). */
    public record ThrowsInCtor(Tid t, int n) implements MpMessage {
        public ThrowsInCtor {
            CTOR.incrementAndGet();
            maybeBoom("ThrowsInCtor.<init>");
        }

        public Tid t() {
            ACC.incrementAndGet();
            return t;
        }

        public int n() {
            ACC.incrementAndGet();
            return n;
        }
    }

    /** A record holding a failing record after a counting component: the failure is inner. */
    public record Holder(Tid t, Object inner, Tid after) implements MpMessage {
        public Holder {
            CTOR.incrementAndGet();
        }

        public Tid t() {
            ACC.incrementAndGet();
            return t;
        }

        public Object inner() {
            ACC.incrementAndGet();
            return inner;
        }

        public Tid after() {
            ACC.incrementAndGet();
            return after;
        }
    }

    /** One component, as {@link OneB}: the same arity, another class (a cache keyed wrongly mixes them). */
    public record OneA(Tid a) implements MpMessage {
        public OneA {
            CTOR.incrementAndGet();
        }

        public Tid a() {
            ACC.incrementAndGet();
            return a;
        }
    }

    /** See {@link OneA}. */
    public record OneB(Object b) implements MpMessage {
        public OneB {
            CTOR.incrementAndGet();
        }

        public Object b() {
            ACC.incrementAndGet();
            return b;
        }
    }

    /** A package-private top-level-like record (nested, default access). */
    record Pkg(Optional<Tid> o, Set<Tid> s) implements MpMessage {
        Pkg {
            CTOR.incrementAndGet();
        }

        @Override
        public Optional<Tid> o() {
            ACC.incrementAndGet();
            return o;
        }

        @Override
        public Set<Tid> s() {
            ACC.incrementAndGet();
            return s;
        }
    }

    // ------------------------------------------------------------------ the reference (3c43b2d)

    /** {@code MustStrategy.mapTids} and {@code runtimeTid} as of 3c43b2d, verbatim. */
    static final class Ref {
        static Object mapTids(Object v, UnaryOperator<Tid> f, boolean strict) {
            if (v instanceof Tid t) {
                return f.apply(t);
            }
            if (v instanceof Record r) {
                RecordComponent[] cs = r.getClass().getRecordComponents();
                Object[] args = new Object[cs.length];
                Class<?>[] types = new Class<?>[cs.length];
                boolean changed = false;
                try {
                    for (int i = 0; i < cs.length; i++) {
                        cs[i].getAccessor().setAccessible(true);
                        Object x = cs[i].getAccessor().invoke(r);
                        Object y = mapTids(x, f, strict);
                        changed |= y != x;
                        args[i] = y;
                        types[i] = cs[i].getType();
                    }
                    if (!changed) {
                        return v;
                    }
                    Constructor<?> c = r.getClass().getDeclaredConstructor(types);
                    c.setAccessible(true);
                    return c.newInstance(args);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    if (strict) {
                        throw new IllegalStateException("cannot normalise the thread ids in " + r.getClass().getName()
                                + ": " + ex, ex);
                    }
                    return v;
                }
            }
            if (v instanceof List<?> l) {
                List<Object> out = new ArrayList<>(l.size());
                boolean changed = false;
                for (Object x : l) {
                    Object y = mapTids(x, f, strict);
                    changed |= y != x;
                    out.add(y);
                }
                return changed ? Collections.unmodifiableList(out) : v;
            }
            if (v instanceof Set<?> set) {
                Set<Object> out = new LinkedHashSet<>();
                boolean changed = false;
                for (Object x : set) {
                    Object y = mapTids(x, f, strict);
                    changed |= y != x;
                    out.add(y);
                }
                return changed ? Collections.unmodifiableSet(out) : v;
            }
            if (v instanceof Optional<?> o && o.isPresent()) {
                Object y = mapTids(o.get(), f, strict);
                return y != o.get() ? Optional.of(y) : v;
            }
            return v;
        }

        static Tid runtimeTid(Tid stored, Map<Long, Long> ids) {
            Long rt = ids.get(stored.raw());
            return Tid.of(rt != null ? rt : -stored.raw());
        }
    }

    // ------------------------------------------------------------------ the implementation under test

    static final Method MAP_TIDS;

    static {
        try {
            MAP_TIDS = MustStrategy.class.getDeclaredMethod("mapTids", Object.class, UnaryOperator.class,
                    boolean.class);
            MAP_TIDS.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Object mapTidsUnderTest(Object v, UnaryOperator<Tid> f, boolean strict) throws Throwable {
        try {
            return MAP_TIDS.invoke(null, v, f, strict);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    // ------------------------------------------------------------------ comparison

    /** What one call did. */
    record Outcome(String result, String thrown, int accessorCalls, int ctorCalls) {}

    /** Maps each Tid to a new instance with raw + 100 and role "m". */
    static final UnaryOperator<Tid> SHIFT = t -> Tid.of(t.raw() + 100, "m");
    /** Returns the same Tid instance: nothing changes. */
    static final UnaryOperator<Tid> SAME = t -> t;

    static Outcome run(boolean underTest, Object input, UnaryOperator<Tid> f, boolean strict) {
        ACC.set(0);
        CTOR.set(0);
        HiddenModule.resetCalls();
        Object out = null;
        Throwable thrown = null;
        try {
            out = underTest ? mapTidsUnderTest(input, f, strict) : Ref.mapTids(input, f, strict);
        } catch (Throwable t) {
            thrown = t;
        }
        int a = ACC.get() + HiddenModule.calls();
        int c = CTOR.get();
        return new Outcome(thrown == null ? describe(out, input) : "-", chain(thrown), a, c);
    }

    static String chain(Throwable t) {
        StringBuilder b = new StringBuilder();
        for (Throwable x = t; x != null; x = x.getCause()) {
            b.append(b.length() == 0 ? "" : " <- ").append(x.getClass().getName()).append(": ").append(x.getMessage());
        }
        return b.length() == 0 ? "none" : b.toString();
    }

    /**
     * The structure of {@code o} without calling any accessor (record fields are read directly),
     * with "=" in front of every part that is the very instance of the input's part at that place.
     */
    static String describe(Object o, Object in) {
        String same = o != null && o == in ? "=" : "";
        if (o == null) {
            return "null";
        }
        if (o instanceof Tid t) {
            return same + "Tid(" + t.raw() + ":" + t.role() + ")";
        }
        if (o instanceof Record) {
            StringBuilder b = new StringBuilder(same + o.getClass().getName() + "(");
            List<Field> fs = new ArrayList<>();
            for (Field fl : o.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(fl.getModifiers())) {
                    fs.add(fl);
                }
            }
            try {
                for (Field fl : fs) {
                    fl.setAccessible(true);
                    Object x = fl.get(o);
                    Object y = in != null && in.getClass() == o.getClass() ? fl.get(in) : null;
                    b.append(fl.getName()).append('=').append(describe(x, y)).append(',');
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                return same + o.getClass().getName() + "{" + o + "}";
            }
            return b.append(')').toString();
        }
        if (o instanceof List<?> || o instanceof Set<?>) {
            List<?> xs = new ArrayList<>((java.util.Collection<?>) o);
            List<?> ys = in instanceof java.util.Collection<?> c && c.size() == xs.size() ? new ArrayList<>(c) : null;
            StringBuilder b = new StringBuilder(same + o.getClass().getName() + "[");
            for (int i = 0; i < xs.size(); i++) {
                b.append(describe(xs.get(i), ys == null ? null : ys.get(i))).append(',');
            }
            return b.append(']').toString();
        }
        if (o instanceof Optional<?> op) {
            Object y = in instanceof Optional<?> oi && oi.isPresent() ? oi.get() : null;
            return same + "Optional[" + (op.isPresent() ? describe(op.get(), y) : "") + "]";
        }
        if (o instanceof Object[] arr) {
            StringBuilder b = new StringBuilder(same + o.getClass().getName() + "[");
            for (Object x : arr) {
                b.append(x).append(',');
            }
            return b.append(']').toString();
        }
        return same + o.getClass().getName() + ":" + o;
    }

    /**
     * Runs a case cold and warm against the reference, in the order under-test (cold), reference,
     * under-test (warm), reference, and requires all four to agree.
     */
    static List<Outcome> same(String name, Object input, UnaryOperator<Tid> f, boolean strict) {
        Outcome cold = run(true, input, f, strict);
        Outcome ref = run(false, input, f, strict);
        Outcome warm = run(true, input, f, strict);
        Outcome ref2 = run(false, input, f, strict);
        System.out.println("CASE " + name + " strict=" + strict + "\n  ref  " + ref + "\n  cold " + cold
                + "\n  warm " + warm);
        assertEquals(ref, ref2, name + ": the reference is deterministic");
        assertEquals(ref, cold, name + ": cold cache equals the reference");
        assertEquals(ref, warm, name + ": warm cache equals the reference");
        return List.of(ref, cold, warm);
    }

    static Tid t(long raw) {
        return Tid.of(raw, "r" + raw);
    }

    // ------------------------------------------------------------------ the hidden module

    /** Records in a named module that opens nothing: setAccessible on their members fails. */
    static final class HiddenModule {
        static Method hidden;
        static Method internal;
        static Method calls;
        static Method reset;

        static void build() throws Exception {
            if (hidden != null) {
                return;
            }
            Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "j1-module-");
            Path src = root.resolve("src/j1hidden");
            Files.createDirectories(src.resolve("j1hidden/api"));
            Files.createDirectories(src.resolve("j1hidden/internal"));
            Files.writeString(src.resolve("module-info.java"), "module j1hidden { exports j1hidden.api; }\n");
            Files.writeString(src.resolve("j1hidden/api/Factory.java"), String.join("\n",
                    "package j1hidden.api;",
                    "public final class Factory {",
                    "  public static final java.util.concurrent.atomic.AtomicInteger CALLS = new java.util.concurrent.atomic.AtomicInteger();",
                    "  public static Object hidden(int x, Object o) { return new Hidden(x, o); }",
                    "  public static Object internal(Object o) { return new j1hidden.internal.Internal(o); }",
                    "  public static int calls() { return CALLS.get(); }",
                    "  public static void reset() { CALLS.set(0); }",
                    "}",
                    "/** Package-private record in an exported, not opened, package. */",
                    "record Hidden(int x, Object o) {",
                    "  public int x() { Factory.CALLS.incrementAndGet(); return x; }",
                    "  public Object o() { Factory.CALLS.incrementAndGet(); return o; }",
                    "}", ""));
            Files.writeString(src.resolve("j1hidden/internal/Internal.java"), String.join("\n",
                    "package j1hidden.internal;",
                    "/** Public record in a package that is neither exported nor opened. */",
                    "public record Internal(Object o) {",
                    "  public Object o() { j1hidden.api.Factory.CALLS.incrementAndGet(); return o; }",
                    "}", ""));
            Path out = root.resolve("out");
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            List<String> args = new ArrayList<>(List.of("-d", out.toString(), "--module-source-path",
                    root.resolve("src").toString(), "-m", "j1hidden"));
            int rc = javac.run(null, null, null, args.toArray(new String[0]));
            assertEquals(0, rc, "javac of the hidden module");
            ModuleFinder finder = ModuleFinder.of(out);
            ModuleLayer parent = ModuleLayer.boot();
            Configuration cf = parent.configuration().resolve(finder, ModuleFinder.of(), Set.of("j1hidden"));
            ModuleLayer layer = parent.defineModulesWithOneLoader(cf, J1MapTidsTest.class.getClassLoader());
            Class<?> factory = layer.findLoader("j1hidden").loadClass("j1hidden.api.Factory");
            hidden = factory.getMethod("hidden", int.class, Object.class);
            internal = factory.getMethod("internal", Object.class);
            calls = factory.getMethod("calls");
            reset = factory.getMethod("reset");
        }

        static int calls() {
            try {
                return calls == null ? 0 : (int) calls.invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static void resetCalls() {
            try {
                if (reset != null) {
                    reset.invoke(null);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static Object hidden(int x, Object o) throws Exception {
            build();
            return hidden.invoke(null, x, o);
        }

        static Object internal(Object o) throws Exception {
            build();
            return internal.invoke(null, o);
        }
    }

    @BeforeAll
    static void setUp() throws Exception {
        HiddenModule.build();
        boom = 0;
    }

    // ------------------------------------------------------------------ cases

    @Test
    void primitiveComponents_changedAndUnchanged() {
        Prims p = new Prims(t(5), -1, Long.MAX_VALUE, true, 'x', 2.5, (byte) 7, (short) -3, 1.5f);
        for (boolean strict : new boolean[] {false, true}) {
            List<Outcome> o = same("prims-shift", p, SHIFT, strict);
            // derived: 9 accessor calls, one construction (the Tid changed)
            assertEquals(9, o.get(0).accessorCalls());
            assertEquals(1, o.get(0).ctorCalls());
            List<Outcome> s = same("prims-same", p, SAME, strict);
            assertEquals(9, s.get(0).accessorCalls());
            assertEquals(0, s.get(0).ctorCalls());
            assertTrue(s.get(0).result().startsWith("="), "unchanged: the same instance");
        }
    }

    @Test
    void unchangedObjectIdentity() throws Throwable {
        NoTid n = new NoTid(3, "x", List.of("a", 4));
        for (boolean strict : new boolean[] {false, true}) {
            List<Outcome> o = same("notid", n, SHIFT, strict);
            assertEquals(3, o.get(0).accessorCalls());
            assertEquals(0, o.get(0).ctorCalls());
            assertSame(n, mapTidsUnderTest(n, SHIFT, strict));
        }
        List<Object> l = List.of(n, "y");
        assertSame(l, mapTidsUnderTest(l, SHIFT, false));
        Set<Object> s = Set.of(n);
        assertSame(s, mapTidsUnderTest(s, SHIFT, false));
        Optional<Object> op = Optional.of(n);
        assertSame(op, mapTidsUnderTest(op, SHIFT, false));
        same("list-of-notid", l, SHIFT, false);
    }

    @Test
    void nestedRecords() {
        NoTid same = new NoTid(1, "s", List.of());
        Outer o = new Outer(new Inner(t(2), List.of(t(3), t(4))), same, "str");
        for (boolean strict : new boolean[] {false, true}) {
            List<Outcome> r = same("outer-shift", o, SHIFT, strict);
            // derived: Outer 3 + Inner 2 + NoTid 3 = 8 accessor calls; Inner and Outer rebuilt
            assertEquals(8, r.get(0).accessorCalls());
            assertEquals(2, r.get(0).ctorCalls());
            assertTrue(r.get(0).result().contains("same==" + NoTid.class.getName()), r.get(0).result());
            List<Outcome> u = same("outer-same", o, SAME, strict);
            assertEquals(8, u.get(0).accessorCalls());
            assertEquals(0, u.get(0).ctorCalls());
        }
    }

    @Test
    void tidsInListSetOptional() {
        Set<Object> set = new LinkedHashSet<>(List.of(t(9), "k", t(8)));
        Colls c = new Colls(new ArrayList<>(List.of(t(1), "a", List.of(t(2)), new NoTid(0, "z", List.of()))),
                set, Optional.of(t(7)), Optional.empty(), new HashMap<>(Map.of("k", t(6))), new Tid[] {t(5)});
        for (boolean strict : new boolean[] {false, true}) {
            List<Outcome> r = same("colls-shift", c, SHIFT, strict);
            // derived: 6 Colls accessors + 3 NoTid accessors; one construction (Colls)
            assertEquals(9, r.get(0).accessorCalls());
            assertEquals(1, r.get(0).ctorCalls());
            same("colls-same", c, SAME, strict);
        }
        Pkg pkg = new Pkg(Optional.of(t(1)), Set.of(t(2)));
        same("pkg-shift", pkg, SHIFT, false);
        same("pkg-shift", pkg, SHIFT, true);
        Pkg pkgEmpty = new Pkg(Optional.empty(), Set.of());
        same("pkg-empty", pkgEmpty, SHIFT, false);
        same("bare-list", List.of(t(1), List.of(t(2), Optional.of(t(3)))), SHIFT, false);
        same("bare-set", new LinkedHashSet<>(List.of(t(4), t(5))), SHIFT, true);
        same("bare-optional", Optional.of(Optional.of(t(6))), SHIFT, true);
        same("bare-tid", t(7), SHIFT, true);
        same("null", null, SHIFT, true);
        same("string", "s", SHIFT, true);
    }

    @Test
    void nonPublicRecords() {
        Priv p = new Priv(t(1), 4);
        same("priv-shift", p, SHIFT, false);
        same("priv-shift", p, SHIFT, true);
        same("priv-same", p, SAME, true);
        record Local(Tid t, Priv p) implements MpMessage {}
        Local l = new Local(t(2), p);
        same("local-shift", l, SHIFT, false);
        same("local-shift", l, SHIFT, true);
        same("local-same", l, SAME, false);
    }

    @Test
    void accessorsThatThrow() {
        ThrowsInAccessor r = new ThrowsInAccessor(t(1), 2, t(3));
        try {
            for (int mode : new int[] {1, 2}) {
                boom = mode;
                for (boolean strict : new boolean[] {false, true}) {
                    List<Outcome> o = same("accessor-throws-" + mode, r, SHIFT, strict);
                    // derived: t() then x(), which throws; u() never runs; no construction
                    assertEquals(2, o.get(0).accessorCalls());
                    assertEquals(0, o.get(0).ctorCalls());
                    if (strict) {
                        assertTrue(o.get(0).thrown().startsWith("java.lang.IllegalStateException: cannot normalise"
                                + " the thread ids in " + ThrowsInAccessor.class.getName()
                                + ": java.lang.reflect.InvocationTargetException"), o.get(0).thrown());
                    } else {
                        assertTrue(o.get(0).result().startsWith("="), o.get(0).result());
                    }
                    Holder h = new Holder(t(4), r, t(5));
                    List<Outcome> hh = same("holder-of-accessor-throws-" + mode, h, SHIFT, strict);
                    // derived: Holder.t, Holder.inner, (inner: t, x throws), then strict: Holder's catch
                    // wraps the inner ISE; non-strict: inner returned as is, Holder.after runs, Holder rebuilt
                    assertEquals(strict ? 4 : 5, hh.get(0).accessorCalls());
                    assertEquals(strict ? 0 : 1, hh.get(0).ctorCalls());
                }
            }
        } finally {
            boom = 0;
        }
        same("accessor-throws-off", r, SHIFT, true);
    }

    @Test
    void constructorsThatThrow() {
        ThrowsInCtor r = new ThrowsInCtor(t(1), 2);
        try {
            for (int mode : new int[] {1, 2}) {
                boom = mode;
                for (boolean strict : new boolean[] {false, true}) {
                    List<Outcome> o = same("ctor-throws-" + mode, r, SHIFT, strict);
                    // derived: both accessors, one constructor call that throws; the Error too is
                    // wrapped by newInstance and caught
                    assertEquals(2, o.get(0).accessorCalls());
                    assertEquals(1, o.get(0).ctorCalls());
                    if (strict) {
                        assertTrue(o.get(0).thrown().contains(" <- java.lang.reflect.InvocationTargetException: null <- "
                                + (mode == 1 ? IllegalArgumentException.class.getName() : BoomError.class.getName())),
                                o.get(0).thrown());
                    } else {
                        assertTrue(o.get(0).result().startsWith("="), o.get(0).result());
                    }
                    // unchanged: the constructor is not called, so nothing throws
                    List<Outcome> u = same("ctor-throws-unchanged-" + mode, r, SAME, strict);
                    assertEquals(0, u.get(0).ctorCalls());
                    assertEquals("none", u.get(0).thrown());
                    Holder h = new Holder(t(4), r, t(5));
                    same("holder-of-ctor-throws-" + mode, h, SHIFT, strict);
                }
            }
        } finally {
            boom = 0;
        }
    }

    @Test
    void accessFailures_moduleThatOpensNothing() throws Exception {
        Object hidden = HiddenModule.hidden(3, t(1));
        Object internal = HiddenModule.internal(List.of(t(2)));
        for (boolean strict : new boolean[] {false, true}) {
            List<Outcome> h = same("hidden", hidden, SHIFT, strict);
            // derived: setAccessible on the first accessor throws InaccessibleObjectException; no call
            assertEquals(0, h.get(0).accessorCalls());
            if (strict) {
                assertTrue(h.get(0).thrown().contains("java.lang.reflect.InaccessibleObjectException"),
                        h.get(0).thrown());
            } else {
                assertTrue(h.get(0).result().startsWith("="), h.get(0).result());
            }
            same("hidden-same", hidden, SAME, strict);
            same("internal", internal, SHIFT, strict);
            Holder holder = new Holder(t(4), hidden, t(5));
            List<Outcome> hh = same("holder-of-hidden", holder, SHIFT, strict);
            // derived: Holder.t, Holder.inner (hidden fails inside), then strict stops; non-strict
            // goes on with Holder.after and rebuilds Holder
            assertEquals(strict ? 2 : 3, hh.get(0).accessorCalls());
            same("list-of-hidden", List.of(t(6), hidden), SHIFT, strict);
        }
    }

    @Test
    void severalRecordClassesOfTheSameArity() {
        OneA a = new OneA(t(1));
        OneB b = new OneB(t(2));
        OneB bs = new OneB("no tid");
        for (boolean strict : new boolean[] {false, true}) {
            same("oneA", a, SHIFT, strict);
            same("oneB", b, SHIFT, strict);
            same("oneB-string", bs, SHIFT, strict);
            same("list-a-b", List.of(a, b, bs, new OneA(t(3))), SHIFT, strict);
        }
    }

    // ------------------------------------------------------------------ onStored's guard path

    /**
     * {@code onStored(guard)} maps each candidate message, once per guard test, through the
     * iteration's ids map ({@code runtimeOfStable}); the guard sees the mapped message. Expected:
     * the reference {@code guard.test((MpMessage) Ref.mapTids(m, t -> Ref.runtimeTid(t, ids),
     * false))}, per candidate, with the same accessor and constructor counts.
     */
    @Test
    @SuppressWarnings("unchecked")
    void onStored_guardPath_everyCandidatePerTest() throws Exception {
        MustStrategy s = new MustStrategy(1L, "build/test-results/speed2-j1");
        Field f = MustStrategy.class.getDeclaredField("runtimeOfStable");
        f.setAccessible(true);
        Map<Long, Long> ids = new HashMap<>(Map.of(1L, 11L, 2L, 12L, 3L, 13L));
        f.set(s, ids);
        Method onStored = MustStrategy.class.getDeclaredMethod("onStored", Predicate.class);
        onStored.setAccessible(true);
        List<String> seenNew = new ArrayList<>();
        List<String> seenRef = new ArrayList<>();
        Predicate<MpMessage> guardNew = m -> {
            seenNew.add(describe(m, null));
            return m instanceof OneA oa && oa.a.raw() == 12L;
        };
        Predicate<MpMessage> guardRef = m -> {
            seenRef.add(describe(m, null));
            return m instanceof OneA oa && oa.a.raw() == 12L;
        };
        Predicate<MpMessage> stored = (Predicate<MpMessage>) onStored.invoke(s, guardNew);
        List<MpMessage> candidates = List.of(new OneA(t(1)), new OneA(t(2)), new OneA(t(9)),
                new Outer(new Inner(t(3), List.of(t(1))), new NoTid(0, "", List.of()), "o"), new NoTid(5, "n", List.of()),
                new ThrowsInCtor(t(2), 0));
        for (int round = 0; round < 3; round++) {
            for (MpMessage m : candidates) {
                ACC.set(0);
                CTOR.set(0);
                boolean gotNew = stored.test(m);
                int an = ACC.get();
                int cn = CTOR.get();
                ACC.set(0);
                CTOR.set(0);
                boolean gotRef = guardRef.test((MpMessage) Ref.mapTids(m, t -> Ref.runtimeTid(t, ids), false));
                int ar = ACC.get();
                int cr = CTOR.get();
                assertEquals(gotRef, gotNew, "guard verdict on " + m);
                assertEquals(ar, an, "accessor calls on " + m);
                assertEquals(cr, cn, "constructor calls on " + m);
            }
        }
        assertEquals(seenRef, seenNew, "the messages the guard saw");
        assertEquals(3 * candidates.size(), seenNew.size(), "one guard call per candidate per test");
        System.out.println("GUARD saw " + seenNew.subList(0, candidates.size()));
        // the mapping reads the map at test time (it is the iteration's own, captured by reference)
        ids.put(9L, 19L);
        assertTrue(!stored.test(new OneA(t(9))), "9 -> 19 is not 12");
        ids.put(9L, 12L);
        assertTrue(stored.test(new OneA(t(9))), "the guard sees the map's current entry");
        assertNotNull(s);
    }

    // ------------------------------------------------------------------ constructor access (SecurityManager)

    /**
     * Constructor-access failure while the accessors succeed exists only under a SecurityManager
     * that refuses {@code Constructor.setAccessible}; {@link J1Probe} runs those cases in a fresh
     * JVM ({@code -Djava.security.manager=allow}).
     */
    @Test
    void constructorAccessFailure_underSecurityManager() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Path log = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "j1probe-", ".txt");
        Process p = new ProcessBuilder(java, "-Djava.security.manager=allow", "-cp",
                System.getProperty("java.class.path"), J1Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done = p.waitFor(300, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
        }
        String text = Files.readString(log, StandardCharsets.UTF_8);
        System.out.println(text);
        assertTrue(done, "probe finished");
        assertTrue(text.contains("DONE failed=0"), text);
        assertEquals(0, p.exitValue(), text);
    }
}
