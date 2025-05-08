package programStructure

import java.io.Serializable

/**
 * JoinEvent event is a subclass of [ThreadEvent] and represents a join event in a program.
 */
data class JoinEvent(

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.JOIN,

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int,

    /**
     * @property joinTid The thread id to join.
     */
    val joinTid: Int
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return JoinEvent(
            type = EventType.JOIN,
            tid = copy().tid,
            serial = copy().serial,
            joinTid = copy().joinTid
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is JoinEvent) return false
        return this.tid == other.tid && this.serial == other.serial && this.type == other.type
    }

    override fun hashCode(): Int {
        var result = tid
        result = 31 * result + type.hashCode()
        result = 31 * result + serial
        return result
    }

    override fun toString(): String {
        return "$type{${tid - 1}, $serial}"
    }
}