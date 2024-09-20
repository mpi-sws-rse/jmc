package programStructure

import java.io.Serializable

data class AssumeBlockedEvent(
    override val type: EventType = EventType.ASSUME_BLOCKED,
    override val tid: Int,
    override val serial: Int
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return AssumeBlockedEvent(
            type = copy().type,
            tid = copy().tid,
            serial = copy().serial
        )
    }
}
