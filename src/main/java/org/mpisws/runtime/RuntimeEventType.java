package org.mpisws.runtime;

public enum RuntimeEventType {
    // Thread creation and termination events
    START_EVENT,
    JOIN_EVENT,
    FINISH_EVENT,

    // Thread park and un-park events
    PARK_EVENT,
    UNPARK_EVENT,

    // Monitor events
    ENTER_MONITOR_EVENT,
    EXIT_MONITOR_EVENT,

    // Lock events
    LOCK_ACQUIRE_EVENT,
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

    // TODO: explain
    GET_FUTURE_EVENT,
    TAKE_WORK_QUEUE,
    CON_ASSUME_EVENT,
    SYM_ASSUME_EVENT,
    ASSUME_BLOCKED_EVENT,
    WAIT_EVENT,
}
