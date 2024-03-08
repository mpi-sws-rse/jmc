package programStructure

import java.io.Serializable

/**
 * ReadEvent is a subclass of ThreadEvent that represents a read event in a
 * program. It contains the value that was read, the event that it reads from,
 * and the location of the read.
 *
 * @property tid The thread id of the event.
 * @property type The type of the event.
 * @property serial The serial number of the event.
 * @property value The value that was read.
 * @property rf The event that this event reads from.
 * @property loc The location of the read.
 */
data class ReadEvent(
    override val tid: Int,
    override val type: EventType = EventType.READ,
    override var serial: Int = 0,
    var value: Any = Any(),
    var rf: ReadsFrom? = null,
    var loc: Location? = null
): ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        val newRead = ReadEvent(
            tid = this.copy().tid,
            type = EventType.READ,
            serial = this.copy().serial,
            value = this.copy().value,
            rf = null,
            loc = this.loc?.deepCopy()
        )

        if (this.rf != null) {
            if (this.rf is WriteEvent) {
                newRead.rf = ((this.rf as WriteEvent).deepCopy()) as
                        ReadsFrom
            } else {
                newRead.rf = ((this.rf as InitializationEvent).deepCopy()) as
                        ReadsFrom
            }
        }
        return newRead
    }
}