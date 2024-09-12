package programStructure

import java.io.Serializable

data class NewTaskEvent(
    override val type: EventType = EventType.NEW_TASK,

    override val tid: Int,

    override val serial: Int,

    val threadPoolId: Int,

    @Transient
    val task: Runnable
) : ThreadEvent(), Serializable {

    var taskString: String = task.toString()

    override fun deepCopy(): Event {
        return NewTaskEvent(
            type = EventType.NEW_TASK,
            tid = copy().tid,
            serial = copy().serial,
            threadPoolId = copy().threadPoolId,
            task = copy().task
        )
    }
}
