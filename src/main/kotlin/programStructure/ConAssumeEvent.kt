package programStructure

import java.io.Serializable

data class ConAssumeEvent(
    override val tid: Int,
    override val type: EventType = EventType.CON_ASSUME,
    override val serial: Int,
    val result: Boolean
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return ConAssumeEvent(
            tid = copy().tid,
            type = copy().type,
            serial = copy().serial,
            result = copy().result
        )
    }
}
