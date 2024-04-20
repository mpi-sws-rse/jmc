package programStructure

import java.io.Serializable

data class MonitorRequestEvent(

    /**
     * @property tid The thread id of the event.
     */
    override val type: EventType,

    /**
     * @property type The type of the event.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int,

    /**
     * @property monitor The monitor that was requested.
     */
    val monitor: Monitor? = null
) : ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return MonitorRequestEvent(
            type = EventType.MONITOR_REQUEST,
            tid = copy().tid,
            serial = copy().serial,
            monitor = monitor?.deepCopy()
        )
    }
}
