package programStructure

import java.io.Serializable

data class NewTaskEvent(
    override val type: EventType = EventType.NEW_TASK,

    override val tid: Int,

    override val serial: Int
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return FailureEvent(
            type = EventType.NEW_TASK,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}
