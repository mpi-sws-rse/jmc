package programStructure

import java.io.Serializable

data class ReadExEvent(
    override val type: EventType = EventType.READ_EX,
    override val tid: Int,
    override val serial: Int,
    val value: Int,
    var loc: Location? = null
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return ReadExEvent(
            type = type,
            tid = tid,
            serial = serial,
            value = value,
            loc = loc?.deepCopy()
        )
    }
}
