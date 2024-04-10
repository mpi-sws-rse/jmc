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
    FINISH_REQUEST,
    WAIT_REQUEST
}
