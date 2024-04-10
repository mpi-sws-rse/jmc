package executionGraph

import programStructure.Event
import java.io.Serializable

/**
 * Interface for the nodes of the execution graph
 */
interface Node: Serializable {

    /**
     * The event that the node represents
     */
    var value: Event

    /**
     * Creates a deep copy of the node
     */
    fun deepCopy(): Node
}