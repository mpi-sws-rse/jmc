package consistencyChecking.memoryConsistency

import executionGraph.ExecutionGraph
import programStructure.Event
import programStructure.ReadEvent

/*

In this class you can find two method for Sequential Consistency(SC)
checking against an execution graph. The first method, which seems to be
incomplete is checking acyclicity of the "porf" relation based on the
Trust algorithm. The second approach is based on Kater's paper and is so
similar to first, but in contrast, it is complete.


 */
class SequentialConsistency {

    companion object {

        /*

        This function implements the consistency checking technique,
        introduced in Trust's paper, which checks if the porf relation
        is acyclic or not.

        Porf = {po \cup rf}^+

         */
        @JvmStatic
        fun porfAcyclicity(graph: ExecutionGraph): Boolean {

            // First, computing the porf of graph
            graph.computeProgramOrderReadFrom()

            // Next, finding a cycle within it
            var cycleFound = false

            for (pair in graph.porf.toList()) {
                val (a, b) = pair
                if (a.equals(b)) {
                    cycleFound = true
                    break
                }
            }
            return !cycleFound
        }

        /*
         The scAcyclicity function is the implementation of the second approach.
         In this approach we check the Irreflexivity if sc relation.
         sc = {po \cup rf \cup co \cup fr}^+
         fr = rf^{-1} ; co
         */

        fun scAcyclicity(graph: ExecutionGraph): Boolean {

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

        @JvmStatic
        fun oldSCAcyclicity(graph: ExecutionGraph): Boolean {

            // First, computing the sc of graph
            val sc = mutableListOf<Pair<Event, Event>>()

            // Adding pairs of initialization event to each graph event
            for (i in 1..<graph.graphEvents.size) {
                graph.porf.add(Pair(graph.graphEvents[0], graph.graphEvents[i]))
            }

            // The whole following part computes the po, rf, and fr relations
            for (i in graph.root?.children!!.keys) {
                var node = graph.root?.children!![i]!!
                // Adding rf
                if (node.value is ReadEvent) {
                    val read = node.value as ReadEvent
                    if (read.rf != null) {
                        sc.add(Pair(read.rf as Event, read as Event))

                        //Adding fr = rf^{-1} ; co
                        // rf^{-1} = Pair(read,read.rf)
                        for (j in 0..(graph.COs.size - 1)) {
                            if (read.rf!!.equals(graph.COs[j].firstWrite as Event)) {
                                sc.add(Pair(read, graph.COs[j].secondWrite as Event))
                            }
                        }
                    }
                }

                // Adding po
                var next = node.child
                while (next != null) {
                    sc.add(Pair(node.value, next.value))
                    node = next
                    next = node.child
                    // Adding rf
                    if (node.value is ReadEvent) {
                        val read = node.value as ReadEvent
                        if (read.rf != null) {
                            sc.add(Pair(read.rf as Event, read as Event))

                            //Adding fr = rf^{-1} ; co
                            // rf^{-1} = Pair(read,read.rf)
                            for (j in 0..(graph.COs.size - 1)) {
                                if (read.rf!!.equals(graph.COs[j].firstWrite as Event)) {
                                    sc.add(Pair(read, graph.COs[j].secondWrite as Event))
                                }
                            }
                        }
                    }
                }
            }

            //This part adds the co edges
            for (i in 0..(graph.COs.size - 1)) {
                sc.add(Pair(graph.COs[i].firstWrite as Event, graph.COs[i].secondWrite as Event))
            }

            // Next, computing the transitive closure of sc relation
            var addedNewPairs = true
            while (addedNewPairs) {
                addedNewPairs = false
                for (pair in sc.toList()) {
                    val (a, b) = pair
                    for (otherPair in sc.toList()) {
                        val (c, d) = otherPair
                        if (b.equals(c) && !sc.contains(Pair(a, d))) {
                            sc.add(Pair(a, d))
                            addedNewPairs = true
                        }
                    }
                }
            }

            // Finally, finding a cycle within it
            var cycleFound = false

            for (pair in sc.toList()) {
                val (a, b) = pair
                if (a.equals(b)) {
                    cycleFound = true
                    break
                }
            }
            return !cycleFound
        }

    }
}