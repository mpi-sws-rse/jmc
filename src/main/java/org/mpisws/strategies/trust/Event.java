package org.mpisws.strategies.trust;

import org.mpisws.runtime.RuntimeEvent;

import java.util.HashMap;
import java.util.Map;

/** Represents an event object used by the algorithm. */
public class Event {
    private final Location location;
    private final Key key;
    private final Type type;
    private final Map<String, Object> attributes;

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
        this.key = new Key(taskId, null);
        this.attributes = new HashMap<>();
    }

    /**
     * Creates a clone of the event.
     *
     * @return A clone of the event.
     */
    public Event clone() {
        Event e = new Event(key.getTaskId(), location, type);
        e.key.setTimestamp(key.getTimestamp());
        e.attributes.putAll(attributes);
        return e;
    }

    /**
     * Returns the attribute of the event with the given key in the type T.
     *
     * @param key The key of the attribute.
     * @param <T> The type of the attribute.
     * @return The attribute with the given key.
     */
    public <T> T getAttribute(String key) {
        if (!attributes.containsKey(key)) {
            return null;
        }
        return (T) attributes.get(key);
    }

    /**
     * Sets the attribute of the event with the given key and value.
     *
     * @param key The key of the attribute.
     * @param value The value of the attribute.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * The key of the event.
     *
     * @return The key of the event.
     */
    public Key key() {
        return key;
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
        return key.getTaskId();
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
    public Integer getTimestamp() {
        return key.getTimestamp();
    }

    /**
     * Sets the timestamp of the event.
     *
     * @param timestamp The timestamp of the event.
     */
    public void setTimestamp(Integer timestamp) {
        this.key.setTimestamp(timestamp);
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

    public static Event error(String message) {
        Event e = new Event(null, null, Type.ERROR);
        e.setAttribute("message", message);
        return e;
    }

    public boolean isExclusiveWrite() {
        return type == Type.WRITE_EX;
    }

    /** Represents the type of the event according to the algorithm. */
    public enum Type {
        READ,
        READ_EX,
        LOCK_AWAIT,
        INIT,
        WRITE,
        WRITE_EX,
        END,
        ERROR,
    }

    public static class Key {
        // The task to which the event belongs to
        private final Long taskId;
        // The index of the event in that task. Assuming deterministic executions here.
        private Integer timestamp;

        public Key(Long taskId, Integer timestamp) {
            this.taskId = taskId;
            this.timestamp = timestamp;
        }

        public Long getTaskId() {
            return taskId;
        }

        public Integer getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Integer timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (!taskId.equals(key.taskId)) {
                return false;
            }
            return timestamp.equals(key.timestamp);
        }

        @Override
        public int hashCode() {
            int result = taskId.hashCode();
            result = 31 * result + timestamp.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "{" + taskId + ", " + timestamp + '}';
        }
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
        return "Event" + key;
    }

    @FunctionalInterface
    public interface EventPredicate {
        boolean test(Event event);
    }
}
