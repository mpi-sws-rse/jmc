package programStructure

data class Initialization( override val type: EventType = EventType.INITIAL ) : Event, ReadsFrom{
    override fun deepCopy(): Event {
        return Initialization()
    }

}