package programStructure

import java.io.Serializable

/**
 * EnterMonitorEvent is a subclass of [ThreadEvent] and represents an enter monitor event in a program.
 * It contains the monitor object that was entered.
 */
data class EnterMonitorEvent(

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int = 0,

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.ENTER_MONITOR,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int = 0,

    /**
     * @property monitor The monitor that was entered.
     */
    val monitor: Monitor? = null
): ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return A deep copy of this object
     */
    override fun deepCopy(): Event {
        return EnterMonitorEvent(
            type = EventType.ENTER_MONITOR,
            tid = copy().tid,
            serial = copy().serial,
            monitor = monitor?.deepCopy()
        )
    }
}