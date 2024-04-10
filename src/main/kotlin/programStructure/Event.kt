package programStructure

import java.io.Serializable

/**
 * Event interface that represents an event in the program
 */
interface Event: Serializable {

    /**
     * @property type the type of the event
     */
    val type: EventType

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    fun deepCopy() : Event
}