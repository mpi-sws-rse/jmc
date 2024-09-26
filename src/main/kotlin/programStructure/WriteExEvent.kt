package programStructure

import java.io.Serializable

data class WriteExEvent(
    override val type: EventType = EventType.WRITE_EX,
    override val tid: Int,
    override val serial: Int,
    var value: Int,
    var loc: Location? = null
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return WriteExEvent(
            type = type,
            tid = tid,
            serial = serial,
            value = value,
            loc = loc?.deepCopy()
        )
    }
}
