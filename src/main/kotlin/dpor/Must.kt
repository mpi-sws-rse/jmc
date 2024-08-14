package dpor

import consistencyChecking.communicationConsistency.FullyAsynchronousConsistency
import executionGraph.ExecutionGraph
import programStructure.*

class Must(path: String) {

    var graphCounter: Int = 0
    private var graph: ExecutionGraph = ExecutionGraph()
    var allGraphs: MutableList<ExecutionGraph> = mutableListOf()
    var graphsPath: String = path

    fun visit(G: ExecutionGraph, allEvents: MutableList<Event>) {
        /*
         To use consistency checking of Kater's Algorithm use the following
         */
        val graphConsistency = FullyAsynchronousConsistency.porfConsistency(G)

        /*
         To use consistency checking of Trust's Algorithm use the following
         */
        // val graphConsistency = SequentialConsistency.porfAcyclicity(G)

        //println("[Model Checker Message] : Model checking started")
        if (graphConsistency) {
            //println("[Model Checker Message] : The graph G_${G.id} is consistent")
            val nextEvent = findNext(allEvents)
            when {
                nextEvent == null -> {
                    this.graphCounter++
                    G.id = this.graphCounter
                    println("[Model Checker Message] : Visited full execution graph G_$graphCounter")
                    G.visualizeGraph(this.graphCounter, this.graphsPath)
                    allGraphs.add(G)
                }

                nextEvent.type == EventType.RECEIVE -> {
                    println("[Model Checker Message] : The next event is a RECEIVE event -> $nextEvent")
                    val G1 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val nextReceiveEvent = (nextEvent as ReceiveEvent)
                    for (i in 0..<G.graphEvents.size) {
                        if (G.graphEvents[i].type == EventType.SEND) {
                            var findSendEvent = G.graphEvents[i] as SendEvent
                            if (satisfiesCondition(findSendEvent, nextReceiveEvent, G)) {
                                val newNextEvent = nextReceiveEvent.deepCopy()
                                val newNextReceiveEvent = newNextEvent as ReceiveEvent
                                var G2 = G1.deepCopy()
                                val copySendEvent = (findSendEvent.deepCopy()) as SendEvent
                                newNextReceiveEvent.rf = copySendEvent
                                G2.addRecvFrom(copySendEvent, newNextReceiveEvent)
                                G2.addEvent(newNextReceiveEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                //println("[Model Checker Message] : Forward Revisit(R -> W) : ($newNextReadEvent, $findWriteEvent)")
                                visit(G2, newAllEvents)
                            }
                        } else if (G.graphEvents[i].type == EventType.INITIAL) {
                            if (nextReceiveEvent.predicate == null) {
                                var G3 = G1.deepCopy()
                                val newNextEvent = nextReceiveEvent.deepCopy()
                                val newNextReceiveEvent = newNextEvent as ReceiveEvent
                                val copyInit = (G.graphEvents[i].deepCopy()) as InitializationEvent
                                newNextReceiveEvent.rf = copyInit
                                G3.addRecvFrom(copyInit, newNextReceiveEvent)
                                G3.addEvent(newNextReceiveEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                println("[Model Checker Message] : Forward Revisit(R -> I) : ($newNextReceiveEvent, ${G.graphEvents[i]})")
                                visit(G3, newAllEvents)
                            }
                        }
                    }
                }

                nextEvent.type == EventType.BLOCK_RECV_REQ -> {
                    val G1 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val nextBlockingReceiveEvent = (nextEvent as BlockingRecvReq)
                    if (nextBlockingReceiveEvent.receiveEvent.predicate == null) {

                    }
                }

                else -> { // TODO() : For possible future extensions
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }
            }
        } else {
            println("[Model Checker Message] : The graph is not consistent")
            //G.printSc()
        }
    }

    private fun findNext(allEvents: MutableList<Event>): Event? {
        return if (allEvents.isNotEmpty()) allEvents.removeFirst()
        else null
    }

    fun satisfiesCondition(sendEvent: SendEvent, receiveEvent: ReceiveEvent, executionGraph: ExecutionGraph): Boolean {
        if (sendEvent.receiverId == receiveEvent.tid.toLong()) {
            for (i in executionGraph.recvfrom.indices) {
                val firstElement = executionGraph.recvfrom.elementAt(i).first as SendEvent
                if ((firstElement.tid == sendEvent.tid) && (firstElement.serial == sendEvent.serial)) {
                    return false
                }
            }
            if (receiveEvent.predicate != null) {
                return receiveEvent.predicate!!.apply(sendEvent.tid.toLong(), sendEvent.tag!!.toLong())
            } else {
                return true
            }
        }
        return false
    }

    private fun deepCopyAllEvents(allEvents: MutableList<Event>): MutableList<Event> {
        val newAllEvents: MutableList<Event> = mutableListOf()
        for (i in 0..<allEvents.size) {
            newAllEvents.add(allEvents[i].deepCopy())
        }
        return newAllEvents
    }

}