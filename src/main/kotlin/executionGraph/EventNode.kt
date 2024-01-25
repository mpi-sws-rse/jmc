package executionGraph

import programStructure.*

data class EventNode(override var value: Event,
                     var child : EventNode? = null) : Node {
    override fun deepCopy(): Node {
            val newEventNode = EventNode(value = this.value.deepCopy(),
                child = null)
            val next = this.child
            if (next != null){
                newEventNode.child = next.deepCopy() as EventNode
            }
        return newEventNode
    }
}
