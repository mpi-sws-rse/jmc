package programStructure

import java.io.Serializable

/**
 * WriteEvent class is a subclass of [ThreadEvent], and it represents a write event in the program.
 */
open class WriteEvent(

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
    override var serial: Int = 0,

    /**
     * @property value The value that is written.
     */
    open var value: Any = Any(),

    /**
     * @property loc The location that is written to.
     */
    open var loc: Location? = null
) : ThreadEvent(), ReadsFrom, Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return A deep copy of this object
     */
    override fun deepCopy(): Event {
        return WriteEvent(
            tid = this.tid,
            type = EventType.WRITE,
            serial = this.serial,
            value = this.value,
            //loc = loc?.deepCopy()
            loc = this.loc
        )
    }

    override fun toString(): String {
        return "WriteEvent(tid=$tid, type=$type, serial=$serial, value=$value, loc=$loc)"
    }

    override fun hashCode(): Int {
        var result = tid
        result = 31 * result + type.hashCode() * type.hashCode()
        result = 31 * result + serial
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is WriteEvent) return false
        return this.tid == other.tid && this.serial == other.serial && this.type == other.type
    }
}