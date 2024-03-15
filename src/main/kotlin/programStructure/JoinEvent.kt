package programStructure

import java.io.Serializable

/**
 * JoinEvent event is a subclass of [ThreadEvent] and represents a join event in a program.
 */
data class JoinEvent(

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.JOIN,

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int,

    /**
     * @property joinTid The thread id to join.
     */
    val joinTid: Int
): ThreadEvent() , Serializable {

    /**
     * Returns a deep copy of this object
     */
    override fun deepCopy(): Event {
        return JoinEvent(
            type = EventType.JOIN,
            tid = copy().tid,
            serial = copy().serial,
            joinTid = copy().joinTid
        )
    }
}