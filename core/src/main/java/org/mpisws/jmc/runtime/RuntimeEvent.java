package org.mpisws.jmc.runtime;

import java.util.HashMap;
import java.util.Map;

/** Represents an event that occurs during the execution of a program. */
public class RuntimeEvent {

    // The type of the event
    private RuntimeEventType type;
    // The ID of the task that generated the event
    private Long taskId;
    // The parameters of the event
    private Map<String, Object> params;

    /**
     * Constructs a new runtime event with the specified type, task ID, and parameters.
     *
     * @param type the type of the event
     * @param taskId the ID of the task that generated the event
     * @param params the parameters of the event
     */
    public RuntimeEvent(RuntimeEventType type, Long taskId, Map<String, Object> params) {
        this.type = type;
        this.taskId = taskId;
        this.params = params;
    }

    /**
     * Constructs a new runtime event with the specified type and task ID.
     *
     * <p>The parameters of the event are initialized to an empty map.
     *
     * @param type the type of the event
     * @param taskId the ID of the task that generated the event
     */
    public RuntimeEvent(RuntimeEventType type, Long taskId) {
        this.type = type;
        this.taskId = taskId;
        this.params = new HashMap<>();
    }

    /**
     * Returns the type of the event.
     *
     * @return the type of the event
     */
    public RuntimeEventType getType() {
        return type;
    }

    /**
     * Returns the ID of the task that generated the event.
     *
     * @return the ID of the task that generated the event
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * Returns the parameters of the event.
     *
     * @return the parameters of the event
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Sets the type of the event.
     *
     * @param type the type of the event
     */
    public void setType(RuntimeEventType type) {
        this.type = type;
    }

    /**
     * Sets the ID of the task that generated the event.
     *
     * @param taskId the ID of the task that generated the event
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    /**
     * Sets the parameters of the event.
     *
     * @param params the parameters of the event
     */
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    /**
     * Sets the value of the parameter with the specified key.
     *
     * @param key the key of the parameter
     * @param value the value of the parameter
     */
    public void setParam(String key, Object value) {
        params.put(key, value);
    }

    /**
     * Returns the value of the parameter with the specified key as an object of the specified
     * class.
     *
     * @param key the key of the parameter
     * @return the value of the parameter as an object of the specified class. Can throw an
     *     exception when casting.
     */
    public <T> T getParam(String key) {
        return (T) params.get(key);
    }

    @Override
    public String toString() {
        return "RuntimeEvent{" + "type=" + type + ", taskId=" + taskId + ", params=" + params + '}';
    }

    /** A builder for constructing a {@link RuntimeEvent} object. */
    public static class Builder {
        private RuntimeEventType type;
        private Long taskId;
        private Map<String, Object> params;

        /** Sets the type of the event. */
        public Builder type(RuntimeEventType type) {
            this.type = type;
            return this;
        }

        /** Sets the ID of the task that generated the event. */
        public Builder taskId(Long taskId) {
            this.taskId = taskId;
            return this;
        }

        /** Sets the parameters of the event. */
        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        /** Adds a parameter to the event. */
        public Builder param(String key, Object value) {
            if (params == null) {
                params = new HashMap<>();
            }
            params.put(key, value);
            return this;
        }

        /** Builds the {@link RuntimeEvent} object. */
        public RuntimeEvent build() {
            return new RuntimeEvent(type, taskId, params);
        }
    }
}
