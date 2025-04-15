package org.mpisws.jmc.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an event that occurs during the execution of a program.
 */
public class RuntimeEvent {

    // The type of the event
    private Type type;
    // The ID of the task that generated the event
    private Long taskId;
    // The parameters of the event
    private Map<String, Object> params;

    /**
     * Constructs a new runtime event with the specified type, task ID, and parameters.
     *
     * @param type   the type of the event
     * @param taskId the ID of the task that generated the event
     * @param params the parameters of the event
     */
    public RuntimeEvent(Type type, Long taskId, Map<String, Object> params) {
        this.type = type;
        this.taskId = taskId;
        this.params = params;
    }

    /**
     * Constructs a new runtime event with the specified type and task ID.
     *
     * <p>The parameters of the event are initialized to an empty map.
     *
     * @param type   the type of the event
     * @param taskId the ID of the task that generated the event
     */
    public RuntimeEvent(Type type, Long taskId) {
        this.type = type;
        this.taskId = taskId;
        this.params = new HashMap<>();
    }

    /**
     * Returns the type of the event.
     *
     * @return the type of the event
     */
    public Type getType() {
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
    public void setType(Type type) {
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
     * @param key   the key of the parameter
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
     * exception when casting.
     */
    public <T> T getParam(String key) {
        return (T) params.get(key);
    }

    @Override
    public String toString() {
        return "RuntimeEvent{" + "type=" + type + ", taskId=" + taskId + ", params=" + params + '}';
    }

    /**
     * A builder for constructing a {@link RuntimeEvent} object.
     */
    public static class Builder {
        private Type type;
        private Long taskId;
        private Map<String, Object> params;

        /**
         * Sets the type of the event.
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the ID of the task that generated the event.
         */
        public Builder taskId(Long taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * Sets the parameters of the event.
         */
        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        /**
         * Adds a parameter to the event.
         */
        public Builder param(String key, Object value) {
            if (params == null) {
                params = new HashMap<>();
            }
            params.put(key, value);
            return this;
        }

        /**
         * Builds the {@link RuntimeEvent} object.
         */
        public RuntimeEvent build() {
            return new RuntimeEvent(type, taskId, params);
        }
    }

    public enum Type {
        // Thread creation and termination events
        START_EVENT,
        FINISH_EVENT,
        HALT_EVENT,

        // Thread join events
        JOIN_REQUEST_EVENT,
        JOIN_COMPLETE_EVENT,

        // Thread park and un-park events
        PARK_EVENT,
        UNPARK_EVENT,

        // Monitor events
        ENTER_MONITOR_EVENT,
        EXIT_MONITOR_EVENT,

        // Lock events
        LOCK_ACQUIRE_EVENT,
        LOCK_ACQUIRED_EVENT,
        LOCK_RELEASE_EVENT,

        // Read and write events
        READ_EVENT,
        WRITE_EVENT,
        CAS_EVENT,

        // Message sending and receiving events
        SEND_EVENT,
        RECV_EVENT,
        RECV_BLOCKING_EVENT,

        // Symbolic arithmetic execution
        SYMB_ARTH_EVENT,

        // Related to futures
        FUTURE_START_EVENT,
        GET_FUTURE_EVENT,
        FUTURE_EXCEPTION_EVENT,
        FUTURE_SET_EVENT,

        // TODO: explain
        TAKE_WORK_QUEUE,
        CON_ASSUME_EVENT,
        SYM_ASSUME_EVENT,
        ASSUME_BLOCKED_EVENT,
        WAIT_EVENT,

        // Task events when using an executor
        TASK_ASSIGNED_EVENT,
        THREAD_POOL_CREATED,
        TASK_CREATED_EVENT,

        // Reactive Event
        REACTIVE_EVENT_RANDOM_VALUE,

        // Related to assertions in the code
        ASSUME_EVENT,
        ASSERT_EVENT,

        SYMB_OP_EVENT,
        SYMB_ASSUME_EVENT,
        SYMB_ASSERT_EVENT,
    }
}
