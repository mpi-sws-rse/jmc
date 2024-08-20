package consistencyChecking.communicationConsistency

import executionGraph.ExecutionGraph

class FullyAsynchronousConsistency {

    companion object {

        fun porfConsistency(graph: ExecutionGraph): Boolean {

            // First, computing the porf of graph
            graph.computeProgramOrderReceiveFrom()

            // Finally, finding a cycle within it
            var cycleFound = false


            for (pair in graph.porf.toList()) {
                val (a, b) = pair
                if (a.equals(b)) {
                    cycleFound = true
                    println("[Fully Asynchronous Consistency Checker Message] : Cycle found in porf relation and the cycle is: $a -> $b")
                    break
                }
            }
            return !cycleFound
        }
    }
}