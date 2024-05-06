package programStructure

import java.io.Serializable

data class UnsuspendEvent(
    override val type: EventType = EventType.UNSUSPEND,
    override val tid: Int,
    override val serial: Int,
    val monitor: Monitor? = null
) : ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return UnsuspendEvent(
            type = EventType.UNSUSPEND,
            tid = copy().tid,
            serial = copy().serial,
            monitor = monitor?.deepCopy()
        )
    }
}
