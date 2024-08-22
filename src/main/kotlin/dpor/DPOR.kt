package dpor

import executionGraph.ExecutionGraph
import programStructure.Event
import programStructure.EventType
import programStructure.FinishEvent

abstract class DPOR(path: String) {

    var graphCounter: Int = 0
    var allGraphs: MutableList<ExecutionGraph> = mutableListOf()
    var graphsPath: String = path

    abstract fun visit(G: ExecutionGraph, allEvents: MutableList<Event>)

    fun findLastEvent(graph: ExecutionGraph, threadId: Int): Event? {
        var EventNode = graph.root?.children?.get(threadId)
        while (EventNode != null) {
            if (EventNode.child == null) {
                return EventNode.value
            } else {
                EventNode = EventNode.child
            }
        }
        return null
    }

    fun findFinishEvent(graph: ExecutionGraph, threadId: Int): FinishEvent? {
        var EventNode = graph.root?.children?.get(threadId)
        while (EventNode != null) {
            if (EventNode.value.type == EventType.FINISH) {
                return EventNode.value as FinishEvent
            } else {
                EventNode = EventNode.child
            }
        }
        return null
    }
}