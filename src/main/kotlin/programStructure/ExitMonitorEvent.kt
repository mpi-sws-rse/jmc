package programStructure

import java.io.Serializable

/**
 * ExitMonitorEvent is a subclass of [ThreadEvent] and represents an exit monitor event in a program.
 * It contains the monitor object that was exited.
 */
data class ExitMonitorEvent(

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int = 0,

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.EXIT_MONITOR,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int = 0,

    /**
     * @property monitor The monitor that was exited.
     */
    val monitor: Monitor? = null
): ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     */
    override fun deepCopy(): Event {
        return ExitMonitorEvent(
            type = EventType.EXIT_MONITOR,
            tid = copy().tid,
            serial = copy().serial,
            monitor = monitor?.deepCopy()
        )
    }
}