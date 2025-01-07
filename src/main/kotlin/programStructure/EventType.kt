package programStructure

import java.io.Serializable

/**
 * Enum class for the different types of events that can occur in a program.
 */
enum class EventType : Serializable {
    INITIAL,
    WRITE,
    READ,
    RECEIVE,
    BLOCK_RECV_REQ,
    BLOCKED_RECV,
    UNBLOCKED_RECV,
    SEND,
    START,
    JOIN,
    FINISH,
    ENTER_MONITOR,
    EXIT_MONITOR,
    FAILURE,
    DEADLOCK,
    MONITOR_REQUEST,
    SUSPEND,
    UNSUSPEND,
    SYM_EXECUTION,
    PARK,
    UNPARK,
    UNPARKING,
    MAIN_START,
    NEW_TASK,
    ASSIGNED_TASK,
    NEW_RUN,
    AWAIT_TASK,
    CON_ASSUME,
    SYM_ASSUME,
    ASSUME_BLOCKED,
    READ_EX,
    WRITE_EX,
    ASSERT,
    OTHER,
}