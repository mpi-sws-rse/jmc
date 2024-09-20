package programStructure

import java.io.Serializable

data class SymAssumeEvent(
    override val type: EventType = EventType.SYM_ASSUME,
    override val tid: Int,
    override val serial: Int,
    val result: Boolean,
    val formula: String,
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
            formula = formula
        )
    }
}
