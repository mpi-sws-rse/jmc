package programStructure

import java.io.Serializable

/**
 * UnparkEvent event is a subclass of [ThreadEvent] and represents a thread unparked event in a program.
 */
data class UnparkEvent(
    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.UNPARK,

    /**
     * @property tid The caller thread id of the event.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int,

    /**
     * @property unparkerTid The caller thread id of the event.
     */
    var unparkerTid: Int = 0

) : ThreadEvent(), Serializable {

    constructor(EventType: EventType, tid: Int, serialNumber: Int) : this(EventType, tid, serialNumber, 0)

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return UnparkEvent(
            type = EventType.UNPARK,
            tid = copy().tid,
            serial = copy().serial,
            unparkerTid = copy().unparkerTid
        )
    }
}