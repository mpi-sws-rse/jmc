package programStructure

import java.io.Serializable

/**
 * UnparkEvent event is a subclass of [ThreadEvent] and represents a thread unparking event in a program.
 */
data class UnparkingEvent(
    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.UNPARKING,

    /**
     * @property tid The caller thread id of the event.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int,

    /**
     * @property tid The callee thread id of the event.
     */
    var unparkTid: Int

) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return UnparkingEvent(
            type = EventType.UNPARKING,
            tid = copy().tid,
            serial = copy().serial,
            unparkTid = copy().unparkTid
        )
    }
}