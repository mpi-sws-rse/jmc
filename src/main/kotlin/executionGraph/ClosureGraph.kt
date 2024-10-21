package executionGraph

import programStructure.EventType
import programStructure.ThreadEvent


data class ClosureGraph(
    val vertices: ArrayList<ThreadEvent> = arrayListOf()
) {
    val edges: HashMap<ThreadEvent, ArrayList<ThreadEvent>> = HashMap()
    var visited: LinkedHashSet<ThreadEvent> = LinkedHashSet()

    fun addVertex(vertex: ThreadEvent) {
        //println("Adding vertex: ${vertex.type}(T${vertex.tid}:${vertex.serial})")
        vertices.add(vertex)
        edges[vertex] = ArrayList()
    }

    fun addEdge(from: ThreadEvent, to: ThreadEvent) {
        //println("Adding edge: ${from.type}(T${from.tid}:${from.serial}) -> ${to.type}(T${to.tid}:${to.serial})")
        edges[from]?.add(to)
    }

    fun pathExists(from: ThreadEvent, to: ThreadEvent): Boolean {
        visited = LinkedHashSet()
        return dfsPathExists(from, to)
    }

    fun dfsPathExists(from: ThreadEvent, to: ThreadEvent): Boolean {
        if (from == to) {
            return true
        }

        visited.add(from)

        for (vertex in edges[from]!!) {
            if (!visited.contains(vertex)) {
                if (dfsPathExists(vertex, to)) {
                    return true
                }
            }
        }

        return false
    }

    fun topologicalSort(): ArrayList<ThreadEvent> {
        visited = LinkedHashSet()
        val recStack: HashSet<ThreadEvent> = HashSet()
        val topoSort: ArrayList<ThreadEvent> = ArrayList()
        for (vertex in vertices) {
            if (dfsTopoSort(vertex, recStack, topoSort)) {
                return ArrayList()
            }
        }
        topoSort.reverse()
        return topoSort
    }

    fun dfsTopoSort(event: ThreadEvent, recStack: HashSet<ThreadEvent>, topoSort: ArrayList<ThreadEvent>): Boolean {
        if (recStack.contains(event)) {
            return true
        }

        if (visited.contains(event)) {
            return false
        }

        visited.add(event)
        recStack.add(event)

        //println("Event: $event")
        for (vertex in edges[event]!!) {
            if (dfsTopoSort(vertex, recStack, topoSort)) {
                return true
            }
        }

        recStack.remove(event)
        topoSort.add(event)
        return false
    }

    fun getNextEvent(current: ThreadEvent, neighbors: ArrayList<ThreadEvent>): ThreadEvent? {
        if (current.type == EventType.READ_EX) {
            for (neighbor in neighbors) {
                if (neighbor.type == EventType.WRITE_EX && current.tid == neighbor.tid && current.serial == neighbor.serial - 1) {
                    return neighbor
                }
            }
        }
        return if (neighbors.isEmpty()) null else neighbors[0]
    }

    fun printEdges() {
        println("Printing edges:")
        for (vertex in edges.keys) {
            print("${vertex.type}(${vertex.tid}:${vertex.serial}) -> ")
            for (neighbor in edges[vertex]!!) {
                print("${neighbor.type}(${neighbor.tid}:${neighbor.serial}) -> ")
            }
            println()
        }
    }

//    fun dfsTopoSortPriority(
//        event: ThreadEvent,
//        recStack: HashSet<ThreadEvent>,
//        topoSort: ArrayList<ThreadEvent>
//    ): Boolean {
//        if (recStack.contains(event)) {
//            return true
//        }
//
//        if (visited.contains(event)) {
//            return false
//        }
//
//        visited.add(event)
//        recStack.add(event)
//
//        val neighbors: ArrayList<ThreadEvent> = edges.get(event)!!
//
//        while (neighbors.isNotEmpty()) {
//            val neighbor = neighbors.removeAt(0)
//            if (dfsTopoSortPriority(neighbor, recStack, topoSort)) {
//                return true
//            }
//        }
//    }
}
