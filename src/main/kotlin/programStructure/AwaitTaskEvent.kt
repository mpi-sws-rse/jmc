package programStructure

import java.io.Serializable

data class AwaitTaskEvent(
    override val type: EventType = EventType.AWAIT_TASK,

    override val tid: Int,

    override val serial: Int
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return AwaitTaskEvent(
            type = EventType.AWAIT_TASK,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}
