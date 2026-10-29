package org.mpi_sws.jmc.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tester test for J7a round 04 (the owner's runtime change, CI1 report section 10, item 3): a waiter on
 * an old future of task id 2 that resumes only after a reset and the creation of a newer future for the
 * same id must not remove the newer future. Driven on {@link TaskManager} directly, so the api.mp barrier
 * is out of the way. The old waiter is suspended while parked in {@code future.get} (JDK 17; it holds no
 * monitor there) so that it resumes deterministically after the new future exists. Derived: with
 * {@code taskFutures.remove(taskId, future)} the new future stays and {@code resume(2)} completes it;
 * with the former {@code remove(taskId)} the old waiter removes it and {@code resume(2)} throws
 * {@link TaskNotExists}.
 */
class TaskManagerStaleWaiterTest {

    @SuppressWarnings("removal")
    private static void suspendWhenParked(Thread t) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (t.getState() != Thread.State.WAITING) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("waiter never parked: " + t.getState());
            }
            Thread.onSpinWait();
        }
        t.suspend();
    }

    @SuppressWarnings("removal")
    private static void scenario(boolean exceptional) throws Exception {
        TaskManager tm = new TaskManager();
        assertEquals(1L, tm.addNextTask());
        assertEquals(2L, tm.addNextTask());
        CompletableFuture<Object> old = tm.pause(2L);
        AtomicReference<Object> waiterSaw = new AtomicReference<>("nothing");
        Thread waiter = new Thread(() -> {
            try {
                waiterSaw.set(tm.<Object>wait(Long.valueOf(2L))); // not Object.wait(long)
            } catch (Exception e) {
                waiterSaw.set(e.getClass().getSimpleName());
            }
        }, "old-waiter");
        waiter.setDaemon(true);
        waiter.start();
        suspendWhenParked(waiter);
        if (exceptional) {
            tm.error(2L, new IllegalStateException("old iteration failed"));
        }
        tm.reset();
        assertTrue(old.isDone(), "the reset (or the error) completed the old future");
        assertEquals(1L, tm.addNextTask());
        assertEquals(2L, tm.addNextTask());
        CompletableFuture<Object> fresh = tm.pause(2L);
        waiter.resume();
        waiter.join(10_000L);
        assertFalse(waiter.isAlive(), "the old waiter finished");
        System.out.println("CI1-R4 tm exceptional=" + exceptional + " waiter saw " + waiterSaw.get());
        String outcome;
        try {
            tm.resume(2L, "for the new task");
            outcome = fresh.isDone() ? "new future resumed" : "new future not completed";
        } catch (TaskNotExists e) {
            outcome = "TaskNotExists: the old waiter removed the new future";
        }
        assertEquals("new future resumed", outcome);
        assertSame("for the new task", fresh.getNow(null));
    }

    @Test
    @Timeout(60)
    void staleWaiter_normalCompletion_doesNotRemoveTheNewerFuture() throws Exception {
        scenario(false);
    }

    @Test
    @Timeout(60)
    void staleWaiter_exceptionalCompletion_doesNotRemoveTheNewerFuture() throws Exception {
        scenario(true);
    }
}
