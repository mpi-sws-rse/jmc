package executionGraph

import programStructure.Event

interface Node{
    var value : Event
    fun deepCopy() : Node
}


