package executionGraph

import programStructure.*
import java.io.Serializable

/**
 * EventNode is a node in the execution graph that contains an event
 * and a reference to the next event in the graph.
 */
data class EventNode(

    /**
     * @property value The event that the node contains.
     */
    override var value: Event,

    /**
     * @property child The next event in the graph.
     */
    var child: EventNode? = null
): Node, Serializable {

    /**
     * Creates a deep copy of the EventNode.
     *
     * @return A deep copy of the EventNode.
     */
    override fun deepCopy(): Node {
        val newEventNode = EventNode(
            value = this.value.deepCopy(),
            child = null
        )
        val next = this.child
        if (next != null) {
            newEventNode.child = next.deepCopy() as EventNode
        }
        return newEventNode
    }
}