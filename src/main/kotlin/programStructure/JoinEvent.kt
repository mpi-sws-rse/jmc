package programStructure

import java.io.Serializable

/**
 * JoinEvent event
 * @property type the type of the event
 * @property tid the thread id
 * @property serial the serial number of the event
 * @property joinTid the thread id to join
 */
data class JoinEvent(
    override val type: EventType = EventType.JOIN,
    override val tid: Int,
    override val serial: Int,
    val joinTid: Int
): ThreadEvent() , Serializable {
    override fun deepCopy(): Event {
        return JoinEvent(
            type = EventType.JOIN,
            tid = copy().tid,
            serial = copy().serial,
            joinTid = copy().joinTid
        )
    }
}