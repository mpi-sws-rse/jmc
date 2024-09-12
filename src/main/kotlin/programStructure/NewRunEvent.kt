package programStructure

import java.io.Serializable

data class NewRunEvent(
    override val type: EventType = EventType.NEW_RUN,

    override val tid: Int,

    override val serial: Int,

    @Transient
    val task: Runnable
) : ThreadEvent(), Serializable {

    var taskString: String = task.toString()

    override fun deepCopy(): Event {
        return NewRunEvent(
            type = EventType.NEW_RUN,
            tid = copy().tid,
            serial = copy().serial,
            task = copy().task
        )
    }
}
