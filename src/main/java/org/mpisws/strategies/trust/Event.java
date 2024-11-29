package org.mpisws.strategies.trust;

/** Represents an event object used by the algorithm. */
public class Event {
    private final Location location;
    private final Long taskId;
    private final Type type;
    private Long timestamp;

    /**
     * Creates a new event with the given task ID, location, and type.
     *
     * @param taskId The task ID.
     * @param location The location.
     * @param type The type.
     */
    public Event(Long taskId, Location location, Type type) {
        this.location = location;
        this.type = type;
        this.taskId = taskId;
    }

    /**
     * Returns the location of the event.
     *
     * @return The location of type {@link Location}.
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Returns the task ID of the event.
     *
     * @return The task ID.
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * Returns the type of the event.
     *
     * @return The type of the event.
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the timestamp of the event.
     *
     * @return The timestamp of the event.
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp of the event.
     *
     * @param timestamp The timestamp of the event.
     */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Creates an init event.
     *
     * @return An init event {@link Event}.
     */
    public static Event init() {
        return new Event(null, null, Type.INIT);
    }

    /**
     * Creates the bottom event to indicate end of the execution.
     *
     * @return An end event {@link Event}.
     */
    public static Event end() {
        return new Event(null, null, Type.END);
    }

    /** Represents the type of the event according to the algorithm. */
    public static enum Type {
        READ,
        INIT,
        WRITE,
        END,
    }

    /**
     * Returns true if the event is an init event.
     *
     * @return True if the event is an init event.
     */
    public boolean isInit() {
        return type == Type.INIT;
    }

    @Override
    public String toString() {
        return "Event{"
                + "location="
                + location
                + ", taskId="
                + taskId
                + ", type="
                + type
                + ", timestamp="
                + timestamp
                + '}';
    }

    @FunctionalInterface
    public interface EventPredicate {
        boolean test(Event event);
    }
}
