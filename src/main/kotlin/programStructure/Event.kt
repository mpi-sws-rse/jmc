package programStructure

interface Event{
    val type : EventType
    fun deepCopy() : Event
}