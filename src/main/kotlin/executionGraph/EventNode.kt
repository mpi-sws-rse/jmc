package executionGraph

import programStructure.*
import java.io.Serializable

/**
 * EventNode is a node in the execution graph that contains an event
 * and a reference to the next event in the graph.
 *
 * @property value The event that the node contains.
 * @property child The next event in the graph.
 */
data class EventNode(
    override var value: Event,
    var child: EventNode? = null
): Node, Serializable {

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