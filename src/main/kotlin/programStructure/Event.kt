package programStructure

import java.io.Serializable

/**
 * Event interface
 */
interface Event: Serializable {

    /**
     * @property type the type of the event
     */
    val type: EventType

    /**
     * Returns a deep copy of this object
     */
    fun deepCopy() : Event
}