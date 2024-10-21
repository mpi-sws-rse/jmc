package programStructure

import java.io.Serializable

data class WriteExEvent(
    override val type: EventType = EventType.WRITE_EX,
    override val tid: Int,
    override var serial: Int,
    var intValue: Int,
    var conditionValue: Int,
    override var loc: Location? = null,
    var operationSuccess: Boolean = false
) : WriteEvent(tid, EventType.WRITE_EX, serial, intValue, loc), ReadsFrom, Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return WriteExEvent(
            type = copy().type,
            tid = copy().tid,
            serial = copy().serial,
            intValue = copy().intValue,
            conditionValue = copy().conditionValue,
            operationSuccess = copy().operationSuccess,
            //loc = loc?.deepCopy()
            loc = this.loc
        )
    }

    override fun hashCode(): Int {
        var result = tid
        result = 31 * result + type.hashCode() * type.hashCode()
        result = 31 * result + serial
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is WriteExEvent) return false
        return this.tid == other.tid && this.serial == other.serial && this.type == other.type
    }
}
