package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.programs.j4.A3Lock;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * J8 tester rounds 07–08: the M1 regression (Codex step-1 round 01) through the production seam
 * {@code Config.liveness}, made deterministic in round 08 (Codex step-1 round 02, m3).
 *
 * <p><b>Gate.</b> Every worker is started through {@link J8PidWorker}, which records its worker id and
 * does not start the real worker until the probe releases it. The probe releases a worker as soon as the
 * latest assignment naming its id (by modification time) is not the target {@code <j>.<a>}; a worker
 * whose latest assignment is the target is held until the probe is in the intended window.
 *
 * <p><b>Window.</b> When the coordinator asks whether the target's process is alive, the result check
 * just before has necessarily missed ({@code item-<j>.json} cannot exist: the worker has not started;
 * this is asserted). The probe then releases the worker, which explores {@code j}, renames its result
 * and exits ({@code die-after:<j>:<a>}); the probe waits for both the result file and the process's
 * exit and only then answers {@code false}, so the recheck after the death must commit the result. A
 * timeout (120 s) is recorded and fails the test.
 *
 * <p>Derived (A3Lock:fifo,3,1, K = 1: R 1, N 5, items 4..0 = 2 4 3 10 10, sequential export md5
 * {@code ddc264d8…}): attempt 0 (W = 1, {@code die-after:4:0}) — item 4 committed at attempt 0, no
 * {@code 4.1}, run complete, export as sequential. Attempt 1 (W = 2, {@code die-before:4:0},
 * {@code die-after:4:1}) — worker 0 dies on 4.0 without a result; the coordinator restarts slot 0 and,
 * in the same loop pass, assigns {@code 4.1} to it (slot 0 is the first idle slot); the recheck commits
 * 4.1 (not "lost its worker twice"); run complete, export as sequential.
 */
class J8Round07Test {

    /** What the probe observed. */
    static final class State {
        volatile boolean intercepted;
        volatile boolean resultAbsentAtEntry;
        volatile String failure;
    }

    static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The latest assignment file naming worker {@code id}, by modification time, or {@code null}. */
    static String latestAssignment(Path dir, String id) {
        Path assigned = dir.resolve("assigned");
        if (!Files.isDirectory(assigned)) {
            return null;
        }
        String best = null;
        long bestTime = Long.MIN_VALUE;
        try (var s = Files.list(assigned)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String f = p.getFileName().toString();
                if (!f.endsWith(".json") || !read(p).replace(" ", "").contains("\"worker\":" + id + "}")) {
                    continue;
                }
                long t = Files.getLastModifiedTime(p).to(TimeUnit.NANOSECONDS);
                if (t > bestTime) {
                    bestTime = t;
                    best = f.substring(0, f.length() - ".json".length());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return best;
    }

    static void release(Path dir, Process p) {
        try {
            Files.createDirectories(dir.resolve("gate"));
            Path g = dir.resolve("gate").resolve(Long.toString(p.pid()));
            if (!Files.exists(g)) {
                Files.writeString(g, "go");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Predicate<Process> probe(Path dir, int j, int a, State st) {
        String target = j + "." + a;
        return p -> {
            Path pidFile = dir.resolve("pids").resolve(Long.toString(p.pid()));
            if (!Files.exists(pidFile)) {
                return p.isAlive();
            }
            String latest = latestAssignment(dir, read(pidFile).trim());
            if (latest == null) {
                return p.isAlive(); // no assignment yet: stay gated
            }
            if (!latest.equals(target) || st.intercepted || st.failure != null) {
                release(dir, p);
                return p.isAlive();
            }
            // The intended window: the coordinator's result check for <j>.<a> has just missed.
            st.resultAbsentAtEntry = !Files.exists(dir.resolve("item-" + j + ".json"));
            release(dir, p);
            long deadline = System.currentTimeMillis() + 120_000;
            try {
                while (!(Files.exists(dir.resolve("item-" + j + ".json")) && !p.isAlive())) {
                    if (System.currentTimeMillis() > deadline) {
                        st.failure = "timeout: item-" + j + ".json " + Files.exists(dir.resolve("item-" + j + ".json"))
                                + ", alive " + p.isAlive();
                        return p.isAlive();
                    }
                    p.waitFor(20, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                st.failure = "interrupted";
                return p.isAlive();
            }
            st.intercepted = true;
            System.out.println("J8R8 probe: " + target + " result published and process exited -> answering false");
            return false;
        };
    }

    /**
     * Runs the coordinator; afterwards kills any real worker of this run that is still alive (an
     * aborted run destroys the J8PidWorker wrappers, not their children) and fails if there was one.
     */
    static SplitCoordinator.Result run(Path dir, int workers, String fault, Predicate<Process> liveness)
            throws Exception {
        try {
            return run0(dir, workers, fault, liveness);
        } finally {
            List<String> left = new ArrayList<>();
            ProcessHandle.allProcesses().forEach(p -> p.info().commandLine().ifPresent(c -> {
                if (c.contains("--worker " + dir) && p.isAlive()) {
                    left.add(p.pid() + "");
                    p.destroyForcibly();
                }
            }));
            if (!left.isEmpty()) {
                throw new AssertionError("worker processes of " + dir + " left alive (killed): " + left);
            }
        }
    }

    static SplitCoordinator.Result run0(Path dir, int workers, String fault, Predicate<Process> liveness)
            throws Exception {
        A3Lock a3 = J8Round06Test.a3();
        return SplitCoordinator.run(new SplitCoordinator.Config(J8Round06Test.A3 + "|p2p", a3, a3.rolesInSpawnOrder(),
                a3.visibleRoles(), 1, workers, 300, dir, (x, id) -> {
                    List<String> c = J8Round06Test.realWorker(x, id, J8Round06Test.A3, "r7");
                    List<String> out = new ArrayList<>(List.of(J8Round06Test.JAVA, "-cp", J8Round06Test.CP,
                            J8PidWorker.class.getName(), x.toString(), Integer.toString(id)));
                    out.addAll(c.subList(0, 1));
                    out.add("-Djmc.split.fault=" + fault);
                    out.addAll(c.subList(1, c.size()));
                    return out;
                }, false, liveness));
    }

    static String files(Path dir) throws Exception {
        StringBuilder b = new StringBuilder();
        for (String sub : List.of("", "assigned", "consumed")) {
            Path p = sub.isEmpty() ? dir : dir.resolve(sub);
            if (Files.isDirectory(p)) {
                try (var s = Files.list(p)) {
                    b.append(sub.isEmpty() ? "." : sub).append(": ");
                    s.map(x -> x.getFileName().toString()).sorted().forEach(x -> b.append(x).append(' '));
                    b.append('\n');
                }
            }
        }
        return b.toString();
    }

    static void common(State st, SplitCoordinator.Result r, String f) throws Exception {
        assertNull(st.failure, "probe: " + st.failure + "\n" + f);
        assertTrue(st.intercepted, "the probe entered the window and answered false\n" + f);
        assertTrue(st.resultAbsentAtEntry, "the result check had missed (no result at the window's entry)");
        assertNull(r.failure(), f);
        assertEquals("{0=10, 1=10, 2=3, 3=4, 4=2}", r.itemIterations().toString());
        assertEquals(J8Round06Test.MD5, J8Round06Test.exportMd5(J8Round06Test.a3(), r));
    }

    @Test
    void m1_resultRenamedBetweenCheckAndDeath_attempt0_committedNotRetried() throws Exception {
        Path dir = J8Round06Test.dir("r8-attempt0");
        State st = new State();
        SplitCoordinator.Result r = run(dir, 1, "die-after:4:0", probe(dir, 4, 0, st));
        String f = files(dir);
        System.out.println("J8R8 attempt 0: failure " + r.failure() + ", items " + r.itemIterations() + ", lost "
                + r.workersLost() + "\n" + f);
        common(st, r, f);
        assertFalse(Files.exists(dir.resolve("assigned/4.1.json")), "no retry");
        assertTrue(read(dir.resolve("item-4.json")).replace(" ", "").contains("\"attempt\":0"));
    }

    @Test
    void m1_resultRenamedBetweenCheckAndDeath_attempt1_committedNotLostTwice() throws Exception {
        Path dir = J8Round06Test.dir("r8-attempt1");
        State st = new State();
        SplitCoordinator.Result r = run(dir, 2, "die-before:4:0,die-after:4:1", probe(dir, 4, 1, st));
        String f = files(dir);
        System.out.println("J8R8 attempt 1: failure " + r.failure() + ", items " + r.itemIterations() + ", lost "
                + r.workersLost() + "\n" + f);
        common(st, r, f);
        assertTrue(Files.exists(dir.resolve("assigned/4.1.json")), "retried once");
        assertFalse(Files.exists(dir.resolve("assigned/4.2.json")), "not twice");
        assertTrue(read(dir.resolve("item-4.json")).replace(" ", "").contains("\"attempt\":1"));
        assertTrue(r.workersLost() <= 1);
    }
}
