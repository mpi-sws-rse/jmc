package programStructure

import java.io.Serializable

/**
 * InitializationEvent event is a subclass of [ThreadEvent] and represents an initialization event in a program.
 */
data class InitializationEvent(

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.INITIAL,

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int = 0,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int = 0
): ThreadEvent(), ReadsFrom, Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return A deep copy of this object
     */
    override fun deepCopy(): Event {
        return InitializationEvent()
    }
}