package programStructure

import java.io.Serializable

/**
 * FinishEvent is a subclass of [ThreadEvent] and represents the end of a thread.
 */
data class FinishEvent(

    /**
     * @property type The type of event.
     */
    override val type: EventType = EventType.FINISH,

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
     */
    override fun deepCopy(): Event {
        return FinishEvent(
            type = EventType.FINISH,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}