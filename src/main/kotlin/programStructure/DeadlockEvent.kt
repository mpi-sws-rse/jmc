package programStructure

import java.io.Serializable

/**
 * DeadlockEvent is a subclass of [ThreadEvent] and represents a deadlock event.
 */
data class DeadlockEvent(

    /**
     * The type of event.
     */
    override val type: EventType,

    /**
     * The thread id.
     */
    override val tid: Int,

    /**
     * The serial number of the event.
     */
    override val serial: Int
): ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return DeadlockEvent(
            type = EventType.DEADLOCK,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}