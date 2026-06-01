package org.mpi_sws.jmc.runtime;

import java.util.HashMap;
import java.util.Map;

import static org.mpi_sws.jmc.api.JmcObject.handleToString;

/**
 * Represents an event that occurs during the execution of an instrumented program.
 *
 * <p>An event marks an interesting point in a task's execution at which a scheduling decision can
 * be made (e.g. thread start/finish, lock acquire/release, field read/write, wait/notify). Events
 * are typically reported to the runtime via {@link JmcRuntime#updateEvent(JmcRuntimeEvent)} just
 * before a {@link JmcRuntime#yield()} and are forwarded to the scheduling strategy.
 *
 * <p>Each event carries a {@link Type}, the ID of the originating task, and an arbitrary map of
 * parameters. Instances are usually built with the fluent {@link Builder}.
 */
public class JmcRuntimeEvent {


    /** The type of the event. */
    private Type type;
    /** The ID of the task that generated the event. */
    private Long taskId;
    /** The additional parameters of the event, keyed by name. */
    private Map<String, Object> params;

    /**
     * Constructs a new runtime event with the specified type, task ID, and parameters.
     *
     * @param type   the type of the event
     * @param taskId the ID of the task that generated the event
     * @param params the parameters of the event
     */
    public JmcRuntimeEvent(Type type, Long taskId, Map<String, Object> params) {
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
    public JmcRuntimeEvent(Type type, Long taskId) {
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
     * Returns the value of the parameter with the specified key, cast to the caller's expected type.
     *
     * <p>The cast is unchecked; a {@link ClassCastException} may be thrown at the call site if the
     * stored value is not of the expected type.
     *
     * @param <T> the expected type of the parameter value
     * @param key the key of the parameter
     * @return the value of the parameter, or {@code null} if no such parameter exists
     */
    public <T> T getParam(String key) {
        return (T) params.get(key);
    }

    /**
     * Renders the parameter map into a human-readable string for {@link #toString()}.
     *
     * <p>The {@code "instance"} parameter is rendered via {@code handleToString} so that
     * instrumented object handles print meaningfully; all other values use their own
     * {@code toString}.
     *
     * @param params the parameter map to render (may be {@code null})
     * @return a comma-separated string of the parameter values, or an empty string if {@code null}
     */
    private String paramToString(Map<String, Object> params) {
        if (params == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            Object o = entry.getValue();
            if (entry.getKey().equals("instance")) {
                sb.append(handleToString(o));
            } else {
                sb.append(o);
            }
        }
        return sb.toString();
    }

    /**
     * Returns a human-readable representation of this event, including its type, task ID, and
     * rendered parameters. Used for debug logging of events.
     *
     * @return a string representation of this event
     */
    @Override
    public String toString() {
        return "RuntimeEvent{" + "type=" + type + ", taskId=" + taskId + ", params=" + paramToString(params) + '}';
    }

    /**
     * A builder for constructing a {@link JmcRuntimeEvent} object.
     */
    public static class Builder {
        /** The event type to build with. */
        private Type type;
        /** The originating task ID to build with. */
        private Long taskId;
        /** The accumulated event parameters; lazily created by {@link #param(String, Object)}. */
        private Map<String, Object> params;

        /**
         * Sets the type of the event.
         *
         * @param type the type of the event
         * @return this builder, for chaining
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the ID of the task that generated the event.
         *
         * @param taskId the ID of the originating task
         * @return this builder, for chaining
         */
        public Builder taskId(Long taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * Sets the full parameter map of the event, replacing any parameters added so far.
         *
         * @param params the parameter map
         * @return this builder, for chaining
         */
        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        /**
         * Adds a single parameter to the event, creating the parameter map if necessary.
         *
         * @param key the parameter key
         * @param value the parameter value
         * @return this builder, for chaining
         */
        public Builder param(String key, Object value) {
            if (params == null) {
                params = new HashMap<>();
            }
            params.put(key, value);
            return this;
        }

        /**
         * Builds the {@link JmcRuntimeEvent} from the configured type, task ID, and parameters.
         *
         * @return a new {@link JmcRuntimeEvent} instance
         */
        public JmcRuntimeEvent build() {
            return new JmcRuntimeEvent(type, taskId, params);
        }
    }

    /**
     * Enum representing the different types of runtime events that can occur.
     *
     * <p>Each event type corresponds to a specific action or occurrence in the program's execution,
     * such as thread creation, locking, reading, writing, and more.
     * TODO :: Refactor this enum
     */
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

        TAKE_WORK_QUEUE,
        CON_ASSUME_EVENT,
        SYM_ASSUME_EVENT,
        ASSUME_BLOCKED_EVENT,

        WAIT_EVENT,
        WAKEUP_EVENT,
        NOTIFY_EVENT,
        NOTIFY_ALL_EVENT,

        // Task events when using an executor
        TASK_ASSIGNED_EVENT,
        THREAD_POOL_CREATED,
        TASK_CREATED_EVENT,

        // Reactive Event (Events that require information from the strategy, upto the strategy to
        // deal with it)
        REACTIVE_EVENT_RANDOM_VALUE,

        // Related to assertions in the code
        ASSUME_EVENT,
        ASSERT_EVENT,

        SYMBOLIC_EVENT,
        SYMB_ASSUME_EVENT,
        SYMB_ASSERT_EVENT,

        // Static Initialization Event
        START_STATIC_INIT_EVENT,
        END_STATIC_INIT_EVENT,

        //Executor tracking event
        EXECUTOR_SHUTDOWN_EVENT,
    }
}
