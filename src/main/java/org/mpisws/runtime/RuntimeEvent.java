package org.mpisws.runtime;

import java.util.HashMap;
import java.util.Map;

/** Represents an event that occurs during the execution of a program. */
public class RuntimeEvent {

    // The type of the event
    private RuntimeEventType type;
    // The ID of the thread that generated the event
    private Long threadId;
    // The parameters of the event
    private Map<String, Object> params;

    /**
     * Constructs a new runtime event with the specified type, thread ID, and parameters.
     *
     * @param type the type of the event
     * @param threadId the ID of the thread that generated the event
     * @param params the parameters of the event
     */
    public RuntimeEvent(RuntimeEventType type, Long threadId, Map<String, Object> params) {
        this.type = type;
        this.threadId = threadId;
        this.params = params;
    }

    /**
     * Constructs a new runtime event with the specified type and thread ID.
     *
     * <p>The parameters of the event are initialized to an empty map.
     *
     * @param type the type of the event
     * @param threadId the ID of the thread that generated the event
     */
    public RuntimeEvent(RuntimeEventType type, Long threadId) {
        this.type = type;
        this.threadId = threadId;
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
     * Returns the ID of the thread that generated the event.
     *
     * @return the ID of the thread that generated the event
     */
    public Long getThreadId() {
        return threadId;
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
     * Sets the ID of the thread that generated the event.
     *
     * @param threadId the ID of the thread that generated the event
     */
    public void setThreadId(Long threadId) {
        this.threadId = threadId;
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
     * Returns the value of the parameter with the specified key.
     *
     * @param key the key of the parameter
     * @return the value of the parameter
     */
    public Object getParam(String key) {
        return params.get(key);
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
     * @param clazz the class of the object
     * @return the value of the parameter as an object of the specified class. Can throw an
     *     exception when casting.
     */
    public Object getParamAs(String key, Class<?> clazz) {
        return clazz.cast(params.get(key));
    }

    /** A builder for constructing a {@link RuntimeEvent} object. */
    public static class Builder {
        private RuntimeEventType type;
        private Long threadId;
        private Map<String, Object> params;

        /** Sets the type of the event. */
        public Builder type(RuntimeEventType type) {
            this.type = type;
            return this;
        }

        /** Sets the ID of the thread that generated the event. */
        public Builder threadId(Long threadId) {
            this.threadId = threadId;
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
            return new RuntimeEvent(type, threadId, params);
        }
    }
}
