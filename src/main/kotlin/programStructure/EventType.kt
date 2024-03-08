package programStructure

import java.io.Serializable

/**
 * Enum class for the different types of events that can occur in a program.
 */
enum class EventType: Serializable {

    INITIAL,
    WRITE,
    READ,
    START,
    JOIN,
    FINISH,
    OTHER
}