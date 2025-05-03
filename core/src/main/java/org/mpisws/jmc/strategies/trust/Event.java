package org.mpisws.jmc.strategies.trust;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/** Represents an event object used by the algorithm. */
public class Event {
    private Integer location;
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
    public Event(Long taskId, Integer location, Type type) {
        this.location = location;
        this.type = type;
        this.key = new Key(taskId);
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

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.add("key", key.toJson());
        if (location != null) {
            json.addProperty("location", location);
        }
        json.addProperty("type", type.toString());
        JsonObject attributesJson = new JsonObject();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            attributesJson.addProperty(entry.getKey(), entry.getValue().toString());
        }
        json.add("attributes", attributesJson);
        return json;
    }

    public JsonElement toJsonIgnoreLocation() {
        JsonObject json = new JsonObject();
        json.add("key", key.toJson());
        json.addProperty("type", type.toString());
        JsonObject attributesJson = new JsonObject();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            attributesJson.addProperty(entry.getKey(), entry.getValue().toString());
        }
        json.add("attributes", attributesJson);
        return json;
    }

    /**
     * Returns the attribute of the event with the given key in the type T.
     *
     * @param key The key of the attribute.
     * @param <T> The type of the attribute.
     * @return The attribute with the given key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        if (!attributes.containsKey(key)) {
            return null;
        }
        return (T) attributes.get(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Event that)) {
            return false;
        }
        return this.key.equals(that.key) && this.type == that.type;
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
     * @return The location of the event.
     */
    public Integer getLocation() {
        return location;
    }

    public void setLocation(Integer location) {
        this.location = location;
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
     * Returns the total order timestamp of the event.
     *
     * @return The total order timestamp of the event.
     */
    public Integer getToStamp() {
        return key.getToStamp();
    }

    /**
     * Sets the total order timestamp of the event.
     *
     * @param toStamp The total order timestamp of the event.
     */
    public void setToStamp(Integer toStamp) {
        this.key.setToStamp(toStamp);
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

    /**
     * Creates a new error event with the given message.
     *
     * @param message The message of the error.
     * @return An error event {@link Event}.
     */
    public static Event error(String message) {
        Event e = new Event(null, null, Type.ERROR);
        e.setAttribute("message", message);
        return e;
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    /** Represents the type of the event according to the algorithm. */
    public enum Type {
        READ,
        READ_EX,
        LOCK_AWAIT,
        BLOCK,
        INIT,
        WRITE,
        WRITE_EX,
        END,
        ERROR,
        LOCK_ACQUIRE,
        LOCK_RELEASE,
        NOOP,
    }

    /** Unique key for the event. */
    public static class Key {
        // The task to which the event belongs to
        private final Long taskId;
        // The index of the event in that task. Assuming deterministic executions here.
        private Integer timestamp;
        // The index of the event in the total order
        private Integer toStamp;

        /**
         * Creates a new key with the given task ID and timestamp.
         *
         * @param taskId The task ID.
         */
        public Key(Long taskId) {
            this.taskId = taskId;
            this.timestamp = null;
            this.toStamp = null;
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

        public Integer getToStamp() {
            return toStamp;
        }

        public void setToStamp(Integer toStamp) {
            this.toStamp = toStamp;
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
            if (taskId == null && timestamp == null) {
                return key.taskId == null && key.timestamp == null;
            }

            if (!taskId.equals(key.taskId)) {
                return false;
            }
            return timestamp.equals(key.timestamp);
        }

        @Override
        public int hashCode() {
            if (taskId == null && timestamp == null) {
                return 0;
            }
            int result = taskId.hashCode();
            result = 31 * result + timestamp.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "{" + taskId + ", " + timestamp + '}';
        }

        public JsonElement toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("taskId", taskId);
            json.addProperty("timestamp", timestamp);
            return json;
        }

        public int compareTo(Key key) {
            if (taskId == null && key.taskId == null) {
                return 0;
            }
            if (taskId == null) {
                return -1;
            }
            if (key.taskId == null) {
                return 1;
            }
            int cmp = taskId.compareTo(key.taskId);
            if (cmp != 0) {
                return cmp;
            }
            if (timestamp == null && key.timestamp == null) {
                return 0;
            }
            if (timestamp == null) {
                return -1;
            }
            if (key.timestamp == null) {
                return 1;
            }
            return timestamp.compareTo(key.timestamp);
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

    public boolean isRead() {
        return type == Type.READ;
    }

    public boolean isWrite() {
        return type == Type.WRITE;
    }

    public boolean isReadEx() {
        return type == Type.READ_EX;
    }

    public boolean isWriteEx() {
        return type == Type.WRITE_EX;
    }

    @Override
    public String toString() {
        return "Event(" + type.toString() + ")" + key;
    }

    /** A generic event predicate. */
    @FunctionalInterface
    public interface EventPredicate {
        /**
         * Tests the event.
         *
         * @param event The event to test.
         * @return True if the event passes the test, false otherwise.
         */
        boolean test(Event event);
    }
}
