package programStructure

import java.io.Serializable

/**
 * ReadEvent is a subclass of [ThreadEvent] that represents a read event in a program.
 * It contains the value that was read, the event that it reads from, and the location of the read.
 */
open class ReadEvent(

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
    open var value: Any = Any(),

    /**
     * @property rf The event that this event reads from.
     */
    open var rf: ReadsFrom? = null,

    /**
     * @property loc The location of the read.
     */
    open var loc: Location? = null
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return A deep copy of this object
     */
    override fun deepCopy(): Event {
        return ReadEvent(
            tid = this.tid,
            type = EventType.READ,
            serial = this.serial,
            value = this.value,
            rf = deepCopyRf(),
            loc = loc?.deepCopy()
        )
    }

    /**
     * Returns a deep copy of the ReadsFrom object
     *
     * @return A deep copy of the ReadsFrom object
     */
    open fun deepCopyRf(): ReadsFrom? {
        return when (this.rf) {
            is WriteEvent -> (this.rf as WriteEvent).deepCopy() as ReadsFrom
            is InitializationEvent -> (this.rf as InitializationEvent).deepCopy() as ReadsFrom
            else -> null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReadEvent) return false

        if (tid != other.tid) return false
        if (type != other.type) return false
        if (serial != other.serial) return false
        if (value != other.value) return false
        if (rf != other.rf) return false
        if (loc != other.loc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tid
        result = 31 * result + type.hashCode()
        result = 31 * result + serial
        result = 31 * result + value.hashCode()
        result = 31 * result + (rf?.hashCode() ?: 0)
        result = 31 * result + (loc?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "ReadEvent(tid=$tid, type=$type, serial=$serial, value=$value, rf=$rf, loc=$loc)"
    }
}