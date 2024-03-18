package programStructure

import java.io.Serializable

/**
 * ReadEvent is a subclass of [ThreadEvent] that represents a read event in a program.
 * It contains the value that was read, the event that it reads from, and the location of the read.
 */
data class ReadEvent(

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.READ,

    /**
     * @property serial The serial number of the event.
     */
    override var serial: Int = 0,

    /**
     * @property value The value that was read.
     */
    var value: Any = Any(),

    /**
     * @property rf The event that this event reads from.
     */
    var rf: ReadsFrom? = null,

    /**
     * @property loc The location of the read.
     */
    var loc: Location? = null
): ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     */
    override fun deepCopy(): Event {
        return ReadEvent(
            tid = copy().tid,
            type = EventType.READ,
            serial = copy().serial,
            value = copy().value,
            rf = deepCopyRf(),
            loc = loc?.deepCopy()
        )
    }

    /**
     * Returns a deep copy of the ReadsFrom object
     */
    private fun deepCopyRf(): ReadsFrom? {
        return when (this.rf) {
            is WriteEvent -> (this.rf as WriteEvent).deepCopy() as ReadsFrom
            is InitializationEvent -> (this.rf as InitializationEvent).deepCopy() as ReadsFrom
            else -> null
        }
    }
}