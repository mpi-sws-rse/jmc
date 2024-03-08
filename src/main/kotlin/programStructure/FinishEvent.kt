package programStructure

import java.io.Serializable

/**
 * FinishEvent is a subclass of ThreadEvent and represents the end of a thread.
 * @property type The type of event.
 * @property tid The thread id.
 * @property serial The serial number of the event.
 */
data class FinishEvent(
    override val type: EventType = EventType.FINISH,
    override val tid: Int,
    override val serial: Int
): ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return FinishEvent(
            type = EventType.FINISH,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}