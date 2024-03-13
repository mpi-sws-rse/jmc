package programStructure

import java.io.Serializable

data class EnterMonitorEvent(
    override val tid: Int = 0,
    override val type: EventType = EventType.ENTER_MONITOR,
    override val serial: Int = 0,
    val monitor: Monitor? = null
): ThreadEvent(), Serializable {


    override fun deepCopy(): Event {
        return EnterMonitorEvent(
            type = EventType.ENTER_MONITOR,
            tid = copy().tid,
            serial = copy().serial,
            monitor = monitor?.deepCopy()
        )
    }
}