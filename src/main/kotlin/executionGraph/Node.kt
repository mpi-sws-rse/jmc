package executionGraph

import programStructure.Event
import java.io.Serializable

/**
 * Interface for the nodes of the execution graph
 */
interface Node: Serializable {

    var value: Event

    fun deepCopy(): Node
}