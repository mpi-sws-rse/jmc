package programStructure

import java.io.Serializable

/**
 * Event interface
 * @property type the type of the event
 */
interface Event: Serializable {

    val type: EventType

    fun deepCopy() : Event
}