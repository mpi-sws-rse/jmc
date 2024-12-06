package org.mpisws.strategies.trust;

public class EventUtils {
    public static boolean isExclusiveWrite(Event event) {
        return event.getType() == Event.Type.WRITE_EX;
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
