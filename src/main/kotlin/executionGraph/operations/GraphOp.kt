package executionGraph.operations

import executionGraph.OptExecutionGraph
import programStructure.ThreadEvent
import java.io.Serializable

data class GraphOp(
    var firstEvent: ThreadEvent,
    var secondEvent: ThreadEvent,
    var type: GraphOpType,
    var g: OptExecutionGraph? = null,
    var toBeAddedEvents: ArrayList<ThreadEvent> = arrayListOf()
) : Serializable
