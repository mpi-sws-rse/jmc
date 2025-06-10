package programStructure

import org.mpisws.symbolic.SymbolicOperation
import java.io.Serializable

data class SymExecutionEvent(
    override val tid: Int,
    override val type: EventType = EventType.SYM_EXECUTION,
    override var serial: Int = 0,
    var result: Boolean = false,
    var formula: String = "",
    @Transient var symbolicOp: SymbolicOperation?,
    var isNegatable: Boolean = false
) : ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return SymExecutionEvent(
            tid = copy().tid,
            type = EventType.SYM_EXECUTION,
            serial = copy().serial,
            result = copy().result,
            formula = copy().formula,
            symbolicOp = copy().symbolicOp,
            isNegatable = copy().isNegatable
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SymExecutionEvent) return false
        return this.tid == other.tid && this.serial == other.serial && this.type == other.type
    }

    override fun hashCode(): Int {
        var res = tid
        res = 31 * res + type.hashCode()
        res = 31 * res + serial
        return res
    }

    override fun toString(): String {
        return "$type{${tid - 1}, $serial}"
    }
}
