package programStructure

import java.io.Serializable

/**
 * ParkEvent event is a subclass of [ThreadEvent] and represents a thread parking event in a program.
 */
data class ParkEvent(
    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.PARK,

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int,

    ) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return ParkEvent(
            type = EventType.PARK,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}