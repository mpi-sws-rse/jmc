package org.mpi_sws.jmc.strategies.trust;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * A single action of the program under test, as seen by the Trust algorithm.
 *
 * <p>Every instrumented operation the runtime reports is translated (by {@link EventFactory}) into
 * one or more {@code Event}s and added to an {@link ExecutionGraph} as an {@link
 * ExecutionGraphNode}. An event carries its {@link Type} (read, write, exclusive read/write, thread
 * bookkeeping, assume, ...), the memory {@link #location} it touches (or {@code null} for
 * non-memory events), a {@link Key} that identifies it within the graph, and an open-ended
 * attribute map for extra data (values, the spawning task of a thread start, symbolic formulas,
 * ...).
 */
public class Event {
    /** The memory location this event accesses, or {@code null} for non-memory events. */
    private Integer location;
    /** Identifies this event within the graph (task id + program-order index). */
    private final Key key;
    /** The kind of action this event represents. */
    private final Type type;
    /** Open-ended per-event data (read/written values, thread-start metadata, formulas, ...). */
    private final Map<String, Object> attributes;

    /**
     * Creates a new event with the given task ID, location, and type.
     *
     * @param taskId   The task ID.
     * @param location The location.
     * @param type     The type.
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
        e.key.setToStamp(key().getToStamp());
        e.attributes.putAll(attributes);
        return e;
    }

    /**
     * Serializes this event (key, location, type, and attributes) to JSON, used when dumping graphs
     * for debugging.
     *
     * @return the JSON representation.
     */
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

    /**
     * Serializes this event without its location, so two graphs that differ only in concrete
     * location ids hash identically (used for graph-coverage counting).
     *
     * @return the location-independent JSON representation.
     */
    public JsonElement toJsonIgnoreLocation() {
        JsonObject json = new JsonObject();
        json.add("key", key.toJson());
        json.addProperty("type", type.toString());
        // Sort the attributes by key
        /*JsonObject attributesJson = new JsonObject();
        attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> attributesJson.addProperty(entry.getKey(), entry.getValue().toString()));
        json.add("attributes", attributesJson);*/
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

    /**
     * Two events are equal when they have the same {@link Key} and the same {@link Type}.
     *
     * @param obj the object to compare with.
     * @return true if {@code obj} is an event with an equal key and type.
     */
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
     * @param key   The key of the attribute.
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

    /**
     * Sets the memory location of the event. Used when locations are re-mapped across iterations.
     *
     * @param location the location hash code.
     */
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

    /**
     * Returns whether an attribute with the given key is present.
     *
     * @param key the attribute key.
     * @return true if the attribute exists.
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    /**
     * The kinds of action an {@link Event} can represent.
     *
     * <p>Reads and writes are ordinary memory accesses; the {@code _EX} variants are the two halves
     * of a read-modify-write (used for locks and atomics). Thread creation, start, and join are
     * modeled as {@code NOOP} events (with attributes), and locks are expanded into exclusive
     * read/write and release {@code WRITE}s by {@link EventFactory}.
     */
    public enum Type {
        /** A {@code JmcAssume} assumption; a failed assumption prunes the execution. */
        ASSUME,
        /** An ordinary read of a memory location. */
        READ,
        /** The read half of a read-modify-write (e.g. a lock acquire). */
        READ_EX,
        /** A blocking label parking a task at its program-order tip (e.g. waiting for a lock). */
        BLOCK,
        /** The initial event; the single root of every graph. */
        INIT,
        /** An ordinary write to a memory location. */
        WRITE,
        /** The write half of a read-modify-write (e.g. completing a lock acquire). */
        WRITE_EX,
        /** The end-of-execution marker for a task. */
        END,
        /** A detected error (e.g. an assertion failure), carrying a message attribute. */
        ERROR,
        /** A lock-acquire request, before it is expanded into its exclusive read/write pair. */
        LOCK_ACQUIRE,
        /** A lock release, modeled as a {@code WRITE} on the lock's location. */
        LOCK_RELEASE,
        /** A symbolic branch introduced by the concolic (ConDpor) extension. */
        SYMBOLIC,
        /** A no-op used for thread-lifecycle bookkeeping (start/finish/join) and similar markers. */
        NOOP
    }

    /**
     * Identifies an {@link Event} within an {@link ExecutionGraph}.
     *
     * <p>A key is the pair {@code (taskId, timestamp)}, where {@code timestamp} is the event's index
     * in its task's program order; this pair is what {@link #equals(Object)} and {@link #hashCode()}
     * use, and it is how {@code ExecutionGraph.getEventNode} locates a node. The {@link #toStamp}
     * (position in the graph's addition order) is a cached convenience value and is <em>not</em>
     * part of the key's identity — the authoritative addition order is the graph's {@code allEvents}
     * list.
     */
    public static class Key {
        /** The task the event belongs to. */
        private final Long taskId;
        /** The event's index within its task's program order (assuming deterministic executions). */
        private Integer timestamp;
        /** Cached position in the graph's addition order; not part of key identity. */
        private Integer toStamp;

        /**
         * Creates a key for the given task with no timestamp yet (assigned when the event is added).
         *
         * @param taskId The task ID.
         */
        public Key(Long taskId) {
            this.taskId = taskId;
            this.timestamp = null;
            this.toStamp = null;
        }

        /**
         * Copy constructor.
         *
         * @param other the key to copy.
         */
        public Key(Key other) {
            this.taskId = other.taskId;
            this.timestamp = other.timestamp;
            this.toStamp = other.toStamp;
        }

        /**
         * Returns a copy of this key.
         *
         * @return a new key with the same fields.
         */
        public Key clone() {
            return new Key(this);
        }

        /**
         * Returns the task id.
         *
         * @return the task id (may be {@code null} for the init/end events).
         */
        public Long getTaskId() {
            return taskId;
        }

        /**
         * Returns the program-order index of the event within its task.
         *
         * @return the timestamp.
         */
        public Integer getTimestamp() {
            return timestamp;
        }

        /**
         * Sets the program-order index of the event within its task.
         *
         * @param timestamp the timestamp.
         */
        public void setTimestamp(Integer timestamp) {
            this.timestamp = timestamp;
        }

        /**
         * Returns the cached addition-order position of the event.
         *
         * @return the total-order stamp.
         */
        public Integer getToStamp() {
            return toStamp;
        }

        /**
         * Sets the cached addition-order position of the event.
         *
         * @param toStamp the total-order stamp.
         */
        public void setToStamp(Integer toStamp) {
            this.toStamp = toStamp;
        }

        /**
         * Two keys are equal when they have the same task id and timestamp (the init/end key, with
         * both {@code null}, equals only another such key).
         *
         * @param o the object to compare with.
         * @return true if the keys share task id and timestamp.
         */
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

        /**
         * Hash consistent with {@link #equals(Object)} (over task id and timestamp).
         *
         * @return the hash code.
         */
        @Override
        public int hashCode() {
            if (taskId == null && timestamp == null) {
                return 0;
            }
            int result = taskId.hashCode();
            result = 31 * result + timestamp.hashCode();
            return result;
        }

        /**
         * Returns a compact {@code {taskId, timestamp}} rendering.
         *
         * @return the string form of the key.
         */
        @Override
        public String toString() {
            return "{" + taskId + ", " + timestamp + '}';
        }

        /**
         * Serializes the key's task id and timestamp to JSON.
         *
         * @return the JSON representation.
         */
        public JsonElement toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("taskId", taskId);
            json.addProperty("timestamp", timestamp);
            return json;
        }

        /**
         * Orders keys by task id, then by timestamp, treating a {@code null} task id/timestamp (the
         * init/end event) as smallest. This total order is what makes the graph's topological sort
         * deterministic.
         *
         * @param key the key to compare with.
         * @return a negative, zero, or positive value as this key is less than, equal to, or greater
         *     than {@code key}.
         */
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

    /**
     * Returns true if this is an ordinary read event.
     *
     * @return true if the type is {@code READ}.
     */
    public boolean isRead() {
        return type == Type.READ;
    }

    /**
     * Returns true if this is an ordinary write event.
     *
     * @return true if the type is {@code WRITE}.
     */
    public boolean isWrite() {
        return type == Type.WRITE;
    }

    /**
     * Returns true if this is the read half of a read-modify-write.
     *
     * @return true if the type is {@code READ_EX}.
     */
    public boolean isReadEx() {
        return type == Type.READ_EX;
    }

    /**
     * Returns true if this is the write half of a read-modify-write.
     *
     * @return true if the type is {@code WRITE_EX}.
     */
    public boolean isWriteEx() {
        return type == Type.WRITE_EX;
    }

    /**
     * Returns true if this is a symbolic (ConDpor) event.
     *
     * @return true if the type is {@code SYMBOLIC}.
     */
    public boolean isSymbolic() {
        return type == Type.SYMBOLIC;
    }

    /**
     * Returns a short {@code Event(TYPE){key}} rendering.
     *
     * @return the string form of the event.
     */
    @Override
    public String toString() {
        return "Event(" + type.toString() + ")" + key;
    }

    /**
     * Returns a fuller rendering that also includes the location and attributes when present.
     *
     * @return a verbose string form of the event.
     */
    public String toVerboseString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Event(").append(type.toString()).append(") key: ").append(key);
        if (location != null) {
            sb.append(", location: ").append(location);
        }
        if (!attributes.isEmpty()) {
            sb.append(", attributes: ").append(attributes);
        }
        return sb.toString();
    }

    /**
     * A generic event predicate.
     */
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

    /**
     * Returns this event's key (identical to {@link #key()}).
     *
     * @return the event key.
     */
    public Key getKey() {
        return key;
    }
}
