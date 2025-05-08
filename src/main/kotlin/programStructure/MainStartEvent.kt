package programStructure

import java.io.Serializable

data class MainStartEvent(
    override val type: EventType = EventType.MAIN_START,
    override val tid: Int = 0,
    override val serial: Int = 0
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return MainStartEvent(
            type = EventType.MAIN_START,
            tid = copy().tid,
            serial = copy().serial
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MainStartEvent) return false
        return this.tid == other.tid && this.serial == other.serial && this.type == other.type
    }

    override fun hashCode(): Int {
        var result = tid
        result = 31 * result + type.hashCode()
        result = 31 * result + serial
        return result
    }

    override fun toString(): String {
        return "{$type{${tid - 1}, $serial}}"
    }
}
