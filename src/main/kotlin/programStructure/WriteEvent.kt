package programStructure

import java.io.Serializable

/**
 * WriteEvent class is a subclass of ThreadEvent, and it represents
 * a write event in the program.
 * @property tid the thread id of the event
 * @property type the type of the event
 * @property serial the serial number of the event
 * @property value the value that is written
 * @property loc the location that is written to
 */
data class WriteEvent(
    override val tid: Int,
    override val type: EventType = EventType.WRITE,
    override var serial : Int,
    var value: Any = Any(),
    var loc: Location? = null
): ThreadEvent(), ReadsFrom, Serializable {

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