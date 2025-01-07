package programStructure

import java.io.Serializable

data class AssertEvent(
    override val type: EventType = EventType.ASSERT,
    override val tid: Int,
    override val serial: Int,
    val result: Boolean,
) : ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return AssertEvent(
            type = copy().type,
            tid = copy().tid,
            serial = copy().serial,
            result = result,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AssertEvent) return false
        return this.type == other.type && this.tid == other.tid && this.serial == other.serial
    }
}
