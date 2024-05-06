package programStructure

import java.io.Serializable

data class SuspendEvent(
    override val type: EventType = EventType.SUSPEND,
    override val tid: Int,
    override val serial: Int,
    val monitor: Monitor? = null
) : ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return SuspendEvent(
            type = EventType.SUSPEND,
            tid = copy().tid,
            serial = copy().serial,
            monitor = monitor?.deepCopy()
        )
    }
}
