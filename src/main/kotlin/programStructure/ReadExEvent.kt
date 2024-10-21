package programStructure

import java.io.Serializable

data class ReadExEvent(
    override val type: EventType = EventType.READ_EX,
    override val tid: Int,
    override var serial: Int,
    var intValue: Int,
    override var rf: ReadsFrom? = null,
    override var loc: Location? = null
) : ReadEvent(tid, type, serial, intValue, rf, loc), Serializable {

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
            intValue = intValue,
            rf = deepCopyRf(),
            loc = this.loc
        )
    }

    override fun deepCopyRf(): ReadsFrom? {
        return when (this.rf) {
            is WriteEvent -> (this.rf as WriteEvent).deepCopy() as ReadsFrom
            is WriteExEvent -> (this.rf as WriteExEvent).deepCopy() as ReadsFrom
            is InitializationEvent -> (this.rf as InitializationEvent).deepCopy() as ReadsFrom
            else -> null
        }
    }

    override fun hashCode(): Int {
        var result = tid
        result = 31 * result + type.hashCode() * type.hashCode()
        result = 31 * result + serial
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ReadExEvent) return false
        return tid == other.tid && type == other.type && serial == other.serial
    }
}
