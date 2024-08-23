package programStructure

import java.io.Serializable

data class MainStartEvent(
    override val type: EventType = EventType.MAIN_START,
    override val tid: Int = 0,
    override val serial: Int = 0
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return MainStartEvent(
            type = EventType.MAIN_START,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}
