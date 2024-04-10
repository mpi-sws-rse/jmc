package executionGraph

import programStructure.Event
import java.io.Serializable

/**
 * RootNode class is a data class that represents the root node of the execution graph.
 */
data class RootNode(

    /**
     * @property value The event that the root node represents. The value of the root node
     * is always an object of the [programStructure.InitializationEvent] class.
     */
    override var value: Event,

    /**
     * @property children A map of children nodes of the root node. The key of the map is the
     * thread id of the branch that the eventNode represents. The value of the map is the eventNode
     * that represents the event that the thread id is executing.
     */
    var children: MutableMap<Int,EventNode>? = mutableMapOf()
): Node, Serializable {

    /**
     * Creates a deep copy of the root node.
     *
     * @return A deep copy of the root node.
     */
    override fun deepCopy(): Node {
        val newRootNode = RootNode(
            value = this.value.deepCopy(),
            children = mutableMapOf()
        )

        for (i in this.children!!.keys) {
            newRootNode.children!![i] = (this.children!![i]!!.deepCopy()) as EventNode
        }
        return newRootNode
    }
}