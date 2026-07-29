package org.mpi_sws.jmc.strategies.trust;

/**
 * Static predicates and small helpers for classifying {@link Event}s.
 *
 * <p>Because the algorithm encodes threads and locks as memory events distinguished only by their
 * {@link Event.Type} and attributes, the handlers throughout {@link Algo} and {@link ExecutionGraph}
 * ask questions like "is this a lock-acquire read?" or "is this a thread-start no-op?" constantly.
 * This class centralizes those checks (and the attribute-key conventions they depend on) so the
 * predicates stay consistent everywhere.
 */
public class EventUtils {
    /**
     * Returns true for an exclusive write that is <em>not</em> a lock acquire (i.e. an atomic RMW
     * write). Lock-acquire writes are excluded because they are handled by the dedicated lock path.
     *
     * @param event the event to test.
     * @return true if it is a non-lock exclusive write.
     */
    public static boolean isExclusiveWrite(Event event) {
        if (event.getType() != Event.Type.WRITE_EX) {
            return false;
        }
        // We exclude writes related to lock acquisition
        return !event.hasAttribute("lock_acquire");
    }

    /**
     * Returns true if the event is any write ({@code WRITE} or {@code WRITE_EX}).
     *
     * @param event the event to test.
     * @return true if it is a write or exclusive write.
     */
    public static boolean isWrite(Event event) {
        return event.getType() == Event.Type.WRITE_EX || event.getType() == Event.Type.WRITE;
    }

    /**
     * Returns true if the event is any read ({@code READ} or {@code READ_EX}).
     *
     * @param event the event to test.
     * @return true if it is a read or exclusive read.
     */
    public static boolean isRead(Event event) {
        return event.getType() == Event.Type.READ || event.getType() == Event.Type.READ_EX;
    }

    /**
     * Returns true if the event is the read half of a lock acquire.
     *
     * @param event the event to test.
     * @return true if it is a lock-acquire exclusive read.
     */
    public static boolean isLockAcquireRead(Event event) {
        return event.getType() == Event.Type.READ_EX && event.hasAttribute("lock_acquire");
    }

    /**
     * Returns true if the event is a lock release write.
     *
     * @param event the event to test.
     * @return true if it is a lock-release write.
     */
    public static boolean isLockReleaseWrite(Event event) {
        return event.getType() == Event.Type.WRITE && event.hasAttribute("lock_release");
    }

    /**
     * Returns true if the event is the write half of a lock acquire.
     *
     * @param event the event to test.
     * @return true if it is a lock-acquire exclusive write.
     */
    public static boolean isLockAcquireWrite(Event event) {
        return event.getType() == Event.Type.WRITE_EX && event.hasAttribute("lock_acquire");
    }

    /**
     * Returns true if the event is a blocking label (a parked task at its program-order tip).
     *
     * @param event the event to test.
     * @return true if it is a {@code BLOCK} label.
     */
    public static boolean isBlockingLabel(Event event) {
        return event.getType() == Event.Type.BLOCK;
    }

    /**
     * Returns true if the event is a symbolic (ConDpor) event.
     *
     * @param event the event to test.
     * @return true if it is a {@code SYMBOLIC} event.
     */
    public static boolean isSymbolic(Event event) {
        return event.getType() == Event.Type.SYMBOLIC;
    }

    /**
     * Returns the (0-indexed) id of the task that spawned this thread-start event.
     *
     * @param event a thread-start event.
     * @return the spawning task id, or {@code null} if unset.
     */
    public static Long getStartedBy(Event event) {
        return event.getAttribute("started_by");
    }

    /**
     * Returns true if the event is an exclusive read ({@code READ_EX}), whether or not it is a lock.
     *
     * @param event the event to test.
     * @return true if it is an exclusive read.
     */
    public static boolean isExclusiveRead(Event event) {
        return event.getType() == Event.Type.READ_EX;
    }

    /**
     * Returns true if the event marks a thread start.
     *
     * @param event the event to test.
     * @return true if it carries the thread-start attribute.
     */
    public static boolean isThreadStart(Event event) {
        return event.hasAttribute("thread_start");
    }

    /**
     * Returns true if the event marks a thread finish.
     *
     * @param event the event to test.
     * @return true if it carries the thread-finish attribute.
     */
    public static boolean isThreadFinish(Event event) {
        return event.hasAttribute("thread_finish");
    }

    /**
     * Returns true if the event marks a thread join (completion).
     *
     * @param event the event to test.
     * @return true if it carries the thread-join attribute.
     */
    public static boolean isThreadJoin(Event event) {
        return event.hasAttribute("thread_join");
    }

    /**
     * Returns true if the event marks a join request.
     *
     * @param event the event to test.
     * @return true if it carries the join-request attribute.
     */
    public static boolean isJoinRequest(Event event) {
        return event.hasAttribute("join-req");
    }

    /**
     * Returns the (0-indexed) id of the task joined by this join event.
     *
     * @param event a thread-join event.
     * @return the joined task id, or {@code -1} if unset.
     */
    public static int getJoinedTask(Event event) {
        Long joinedTask = event.getAttribute("joined_task");
        if (joinedTask == null) {
            return -1;
        }
        return Math.toIntExact(joinedTask);
    }

    /**
     * Marks the event as not revisitable (sets its {@code revisit} attribute to false).
     *
     * @param event the event to mark.
     */
    public static void makeUnRevistable(Event event) {
        event.setAttribute("revisit", false);
    }

    /**
     * Marks the event as revisitable (sets its {@code revisit} attribute to true).
     *
     * @param event the event to mark.
     */
    public static void makeRevistable(Event event) {
        event.setAttribute("revisit", true);
    }

    /**
     * Returns whether the event is revisitable; events default to revisitable when unmarked.
     *
     * @param event the event to test.
     * @return true if the event is revisitable.
     */
    public static boolean isRevisit(Event event) {
        Boolean revisit = event.getAttribute("revisit");
        return revisit == null || revisit;
    }

    /**
     * Returns true if the event is a lock-acquire write that has been marked "final".
     *
     * @param event the event to test.
     * @return true if it is a final lock-acquire write.
     */
    public static boolean isFinalLockWrite(Event event) {
        return event.getType() == Event.Type.WRITE_EX
                && event.hasAttribute("final_lock")
                && event.hasAttribute("lock_acquire");
    }

    /**
     * Marks a lock-acquire write as "final" (sets its {@code final_lock} attribute).
     *
     * @param event the event to mark.
     */
    public static void markLockWriteFinal(Event event) {
        event.setAttribute("final_lock", true);
    }

    /**
     * Returns true if the event is a "lock acquired" no-op.
     *
     * @param event the event to test.
     * @return true if it is a lock-acquired marker.
     */
    public static boolean isLockAcquired(Event event) {
        return event.getType() == Event.Type.NOOP && event.hasAttribute("lock_acquired");
    }

    /**
     * Returns true if the event is a no-op.
     *
     * @param event the event to test.
     * @return true if it is a {@code NOOP}.
     */
    public static boolean isNoop(Event event) {
        return event.getType() == Event.Type.NOOP;
    }

    /**
     * Returns true if the event is an assumption.
     *
     * @param event the event to test.
     * @return true if it is an {@code ASSUME}.
     */
    public static boolean isAssume(Event event) {
        return event.getType() == Event.Type.ASSUME;
    }

    /**
     * Returns true if the event is an assumption that evaluated to false (a blocked assume, which
     * prunes the execution).
     *
     * @param event the event to test.
     * @return true if it is a failed assumption.
     */
    public static boolean isBlockedAssume(Event event) {
        return event.getType() == Event.Type.ASSUME && !(Boolean) event.getAttribute("result");
    }
}
