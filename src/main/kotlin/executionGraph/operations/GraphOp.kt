package executionGraph.operations

import executionGraph.OptExecutionGraph
import programStructure.ThreadEvent
import java.io.Serializable

data class GraphOp(
    var firstEvent: ThreadEvent?,
    var secondEvent: ThreadEvent?,
    var type: GraphOpType,
    var g: OptExecutionGraph? = null,
    var proverId: Int?,
    var toBeAddedEvents: ArrayList<ThreadEvent> = arrayListOf()
) : Serializable {

    override fun toString(): String {
        return "$type(firstEvent=$firstEvent, secondEvent=$secondEvent)"
    }
}
