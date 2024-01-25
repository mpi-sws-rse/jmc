package programStructure

abstract class ThreadEvent : Event {
    abstract val tid : Int
    abstract val serial : Int
}