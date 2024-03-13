package programStructure

import java.io.Serializable

data class ExitMonitorEvent(
    override val tid: Int = 0,
    override val type: EventType = EventType.EXIT_MONITOR,
    override val serial: Int = 0,
    val monitor: Monitor? = null
): ThreadEvent(), Serializable {


    override fun deepCopy(): Event {
        return ExitMonitorEvent(
            type = EventType.EXIT_MONITOR,
            tid = copy().tid,
            serial = copy().serial,
            monitor = monitor?.deepCopy()
        )
    }
}