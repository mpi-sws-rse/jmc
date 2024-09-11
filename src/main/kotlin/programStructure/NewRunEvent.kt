package programStructure

import java.io.Serializable

data class NewRunEvent(
    override val type: EventType = EventType.NEW_RUN,

    override val tid: Int,

    override val serial: Int
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return FailureEvent(
            type = EventType.NEW_RUN,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}
