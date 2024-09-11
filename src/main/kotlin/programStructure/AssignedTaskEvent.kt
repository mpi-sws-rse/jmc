package programStructure

import java.io.Serializable

data class AssignedTaskEvent(
    override val type: EventType = EventType.ASSIGNED_TASK,

    override val tid: Int,

    override val serial: Int
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return FailureEvent(
            type = EventType.ASSIGNED_TASK,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}
