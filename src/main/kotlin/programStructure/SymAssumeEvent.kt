package programStructure

import org.mpisws.symbolic.SymbolicOperation
import java.io.Serializable

data class SymAssumeEvent(
    override val type: EventType = EventType.SYM_ASSUME,
    override val tid: Int,
    override val serial: Int,
    val result: Boolean,
    val formula: String,
    @Transient var symbolicOp: SymbolicOperation?,
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    override fun deepCopy(): Event {
        return SymAssumeEvent(
            type = copy().type,
            tid = copy().tid,
            serial = copy().serial,
            result = result,
            formula = formula,
            symbolicOp = copy().symbolicOp
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SymAssumeEvent) return false
        return this.type == other.type && this.tid == other.tid && this.serial == other.serial
    }

    override fun hashCode(): Int {
        var res = tid
        res = 31 * res + type.hashCode()
        res = 31 * res + serial
        return res
    }
}
