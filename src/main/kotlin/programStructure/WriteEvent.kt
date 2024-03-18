package programStructure

import java.io.Serializable

/**
 * WriteEvent class is a subclass of [ThreadEvent], and it represents a write event in the program.
 */
data class WriteEvent(

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.WRITE,

    /**
     * @property serial The serial number of the event.
     */
    override var serial : Int = 0,

    /**
     * @property value The value that is written.
     */
    var value: Any = Any(),

    /**
     * @property loc The location that is written to.
     */
    var loc: Location? = null
): ThreadEvent(), ReadsFrom, Serializable {

    /**
     * Returns a deep copy of this object
     */
    override fun deepCopy(): Event {
        return WriteEvent(
            tid = copy().tid,
            type = EventType.WRITE,
            serial = copy().serial,
            value = copy().value,
            loc = loc?.deepCopy()
        )
    }
}