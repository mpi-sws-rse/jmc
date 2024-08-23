package org.mpisws.runtime;

/**
 * Enumerates the different types of requests that can be made to the runtime.
 */
public enum RequestType {
    START_REQUEST,
    ENTER_MONITOR_REQUEST,
    EXIT_MONITOR_REQUEST,
    JOIN_REQUEST,
    READ_REQUEST,
    WRITE_REQUEST,
    SEND_REQUEST,
    RECV_REQUEST,
    RECV_BLOCKING_REQUEST,
    FINISH_REQUEST,
    WAIT_REQUEST,
    SYMB_ARTH_REQUEST,
    PARK_REQUEST,
    UNPARK_REQUEST,
    MAIN_START_REQUEST
}
