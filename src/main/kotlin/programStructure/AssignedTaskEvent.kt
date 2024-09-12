package programStructure

import java.io.Serializable

data class AssignedTaskEvent(
    override val type: EventType = EventType.ASSIGNED_TASK,

    override val tid: Int,

    override val serial: Int,

    @Transient
    val task: Runnable
) : ThreadEvent(), Serializable {

    var taskString: String = task.toString()

    override fun deepCopy(): Event {
        return AssignedTaskEvent(
            type = EventType.ASSIGNED_TASK,
            tid = copy().tid,
            serial = copy().serial,
            task = copy().task
        )
    }
}
