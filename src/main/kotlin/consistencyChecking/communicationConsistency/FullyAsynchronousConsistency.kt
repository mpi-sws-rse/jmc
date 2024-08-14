package consistencyChecking.communicationConsistency

import executionGraph.ExecutionGraph

class FullyAsynchronousConsistency {

    companion object {

        fun porfConsistency(graph: ExecutionGraph): Boolean {

            // First, computing the sc of graph
            graph.computeSc()

            // Finally, finding a cycle within it
            var cycleFound = false


            for (pair in graph.sc.toList()) {
                val (a, b) = pair
                if (a.equals(b)) {
                    cycleFound = true
                    println("[Sequential Consistency Checker Message] : Cycle found in SC relation and the cycle is: $a -> $b")
                    //graph.printSc()
                    break
                }
            }
            return !cycleFound
        }
    }
}