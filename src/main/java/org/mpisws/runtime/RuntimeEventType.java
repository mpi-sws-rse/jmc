package org.mpisws.runtime;

public enum RuntimeEventType {
    // Thread creation and termination events
    START_EVENT,
    FINISH_EVENT,
    HALT_EVENT,

    // Thread join events
    JOIN_REQUEST_EVENT,

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

    // Related to assertions in the code
    ASSUME_EVENT,
    ASSERT_EVENT,

    SYMB_OP_EVENT,
    SYMB_ASSUME_EVENT,
    SYMB_ASSERT_EVENT,
}
