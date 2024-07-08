package programStructure

import java.io.Serializable

/**
 * Enum class for the different types of events that can occur in a program.
 */
enum class EventType : Serializable {
    INITIAL,
    WRITE,
    READ,
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
    OTHER
}