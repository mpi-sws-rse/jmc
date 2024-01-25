package programStructure

data class WriteEvent(
    override val tid: Int,
    override val type: EventType = EventType.WRITE,
    override var serial: Int = 0,
    var value : Any = "nothing",
    var loc : Location? = null
) : ThreadEvent(), ReadsFrom {
    override fun deepCopy(): Event {
        return WriteEvent(
            tid = copy().tid,
            type = EventType.WRITE,
            serial = copy().serial,
            value = copy().value,
            loc = loc?.deepCopy()
        )
    }

}

