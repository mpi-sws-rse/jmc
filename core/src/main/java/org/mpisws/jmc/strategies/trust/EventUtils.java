package org.mpisws.jmc.strategies.trust;

public class EventUtils {
    public static boolean isExclusiveWrite(Event event) {
        if (event.getType() != Event.Type.WRITE_EX) {
            return false;
        }
        // We exclude writes related to lock acquisition
        return !event.hasAttribute("lock_acquire");
    }

    public static boolean isWrite(Event event) {
        return event.getType() == Event.Type.WRITE_EX || event.getType() == Event.Type.WRITE;
    }

    public static boolean isRead(Event event) {
        return event.getType() == Event.Type.READ || event.getType() == Event.Type.READ_EX;
    }

    public static boolean isLockAcquireRead(Event event) {
        return event.getType() == Event.Type.READ && event.hasAttribute("lock_acquire");
    }

    public static boolean isLockReleaseWrite(Event event) {
        return event.getType() == Event.Type.WRITE && event.hasAttribute("lock_release");
    }

    public static boolean isLockAcquireWrite(Event event) {
        return event.getType() == Event.Type.WRITE && event.hasAttribute("lock_acquire");
    }

    public static boolean isBlockingLabel(Event event) {
        return event.getType() == Event.Type.BLOCK || event.getType() == Event.Type.LOCK_AWAIT;
    }

    public static Long getStartedBy(Event event) {
        return event.getAttribute("started_by");
    }

    public static boolean isExclusiveRead(Event event) {
        return event.getType() == Event.Type.READ_EX;
    }

    public static boolean isThreadStart(Event event) {
        return event.hasAttribute("thread_start");
    }

    public static boolean isThreadFinish(Event event) {
        return event.hasAttribute("thread_finish");
    }

    public static boolean isThreadJoin(Event event) {
        return event.hasAttribute("thread_join");
    }

    public static int getJoinedTask(Event event) {
        Long joinedTask = event.getAttribute("joined_task");
        if (joinedTask == null) {
            return -1;
        }
        return Math.toIntExact(joinedTask);
    }

    public static void makeUnRevistable(Event event) {
        event.setAttribute("revisit", false);
    }

    public static void makeRevistable(Event event) {
        event.setAttribute("revisit", true);
    }

    public static boolean isRevisit(Event event) {
        Boolean revisit = event.getAttribute("revisit");
        return revisit == null || revisit;
    }
}
