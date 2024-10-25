package programStructure

import java.io.Serializable

data class AssumeBlockedEvent(
    override val type: EventType = EventType.ASSUME_BLOCKED,
    override val tid: Int,
    override val serial: Int
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return AssumeBlockedEvent(
            type = copy().type,
            tid = copy().tid,
            serial = copy().serial
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AssumeBlockedEvent) return false
        return this.tid == other.tid && this.serial == other.serial && this.type == other.type
    }

    override fun hashCode(): Int {
        var result = tid
        result = 31 * result + type.hashCode()
        result = 31 * result + serial
        return result
    }
}
