package programStructure

import java.io.Serializable

/**
 * FailureEvent is a subclass of [ThreadEvent] and represents a failure event.
 */
data class FailureEvent(

    /**
     * @property type The type of event.
     */
    override val type: EventType,

    /**
     * @property tid The thread id.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int
): ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return A deep copy of this object
     */
    override fun deepCopy(): Event {
        return FailureEvent(
            type = EventType.FAILURE,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}