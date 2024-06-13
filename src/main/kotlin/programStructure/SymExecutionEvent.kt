package programStructure

import java.io.Serializable

data class SymExecutionEvent(
    override val tid: Int,
    override val type: EventType = EventType.SYM_EXECUTION,
    override var serial: Int = 0,
    var result: Boolean = false,
    var formula: String = "",
    var isNegatable: Boolean = false
) : ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return SymExecutionEvent(
            tid = copy().tid,
            type = EventType.SYM_EXECUTION,
            serial = copy().serial,
            result = copy().result,
            formula = copy().formula,
            isNegatable = copy().isNegatable
        )
    }
}
