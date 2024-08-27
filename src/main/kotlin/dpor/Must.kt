package dpor

import consistencyChecking.communicationConsistency.FullyAsynchronousConsistency
import executionGraph.CO
import executionGraph.ExecutionGraph
import programStructure.*

class Must(path: String) : DPOR(path) {

    override fun visit(G: ExecutionGraph, allEvents: MutableList<Event>) {

        val graphConsistency = FullyAsynchronousConsistency.porfConsistency(G)

        if (graphConsistency) {
            val nextEvent = findNext(allEvents)
            when {
                nextEvent == null -> {
                    this.graphCounter++
                    G.id = this.graphCounter
                    println("[Must Message] : Visited full execution graph G_$graphCounter")
                    G.visualizeGraph(this.graphCounter, this.graphsPath)
                    allGraphs.add(G)
                }

                nextEvent.type == EventType.RECEIVE -> {
                    println("[Must Message] : The next event is a RECEIVE event -> $nextEvent")
                    println("[Must Message] : The forward revisit phase begins")
                    val G1 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val nextReceiveEvent = (nextEvent as ReceiveEvent)
                    for (i in 0..<G.graphEvents.size) {
                        if (G.graphEvents[i].type == EventType.SEND) {
                            var findSendEvent = G.graphEvents[i] as SendEvent
                            if (isSendRecvPair(findSendEvent, nextReceiveEvent) &&
                                isSendSatisfiesPredicate(findSendEvent, nextReceiveEvent) &&
                                isSendFree(findSendEvent, G)
                            ) {
                                val newNextReceiveEvent = nextReceiveEvent.deepCopy() as ReceiveEvent
                                var G2 = G1.deepCopy()
                                val copySendEvent = (findSendEvent.deepCopy()) as SendEvent
                                newNextReceiveEvent.rf = copySendEvent
                                G2.addRecvFrom(copySendEvent, newNextReceiveEvent)
                                newNextReceiveEvent.value = copySendEvent.value
                                println("[Must Message] : The event $newNextReceiveEvent revisits event $copySendEvent")
                                G2.addEvent(newNextReceiveEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                visit(G2, newAllEvents)
                            }
                        } else if (G.graphEvents[i].type == EventType.INITIAL) {
                            if (!nextReceiveEvent.blocking) {
                                var G3 = G1.deepCopy()
                                val newNextEvent = nextReceiveEvent.deepCopy()
                                val newNextReceiveEvent = newNextEvent as ReceiveEvent
                                val copyInit = (G.graphEvents[i].deepCopy()) as InitializationEvent
                                newNextReceiveEvent.rf = copyInit
                                newNextReceiveEvent.value = null
                                G3.addRecvFrom(copyInit, newNextReceiveEvent)
                                println("[Must Message] : The event $newNextReceiveEvent revisits event $copyInit")
                                G3.addEvent(newNextReceiveEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                visit(G3, newAllEvents)
                            }
                        }
                    }
                }

                nextEvent.type == EventType.BLOCK_RECV_REQ -> {
                    println("[Must Message] : The next event is a BLOCK_RECV_REQ event -> $nextEvent")
                    val G1 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val nextBlockingReceiveEvent = (nextEvent as BlockingRecvReq)
                    var sendFound: Boolean = false
                    for (i in 0..<G.graphEvents.size) {
                        if (G.graphEvents[i].type == EventType.SEND) {
                            var findSendEvent = G.graphEvents[i] as SendEvent
                            if (isSendRecvPair(findSendEvent, nextBlockingReceiveEvent.receiveEvent) &&
                                isSendSatisfiesPredicate(findSendEvent, nextBlockingReceiveEvent.receiveEvent) &&
                                isSendFree(findSendEvent, G)
                            ) {
                                nextBlockingReceiveEvent.isBlocked = false
                                sendFound = true
                                break
                            }
                        }
                    }
                    if (!sendFound) {
                        nextBlockingReceiveEvent.isBlocked = true
                        println("[Must Message] : The thread-${nextBlockingReceiveEvent.receiveEvent.tid} is blocked")
                    } else {
                        println("[Must Message] : The thread-${nextBlockingReceiveEvent.receiveEvent.tid} is eligible to perform the receive event")
                    }
                    val newAllEvents = deepCopyAllEvents(allEvents)
                    G1.addEvent(nextBlockingReceiveEvent)
                    visit(G1, newAllEvents)
                }

                nextEvent.type == EventType.BLOCKED_RECV -> {
                    println("[Must Message] : The next event is a BLOCKED_RECV event -> $nextEvent")
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.UNBLOCKED_RECV -> {
                    println("[Must Message] : The next event is a UNBLOCKED_RECV event -> $nextEvent")
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.SEND -> {
                    println("[Must Message] : The next event is a SEND event -> $nextEvent")
                    // Forward Revisit
                    println("[Must Message] : The forward revisit phase begins")
                    val G1 = G.deepCopy()
                    G1.addEvent(nextEvent.deepCopy())
                    val newAllEvents = deepCopyAllEvents(allEvents)
                    visit(G1, newAllEvents)

                    // Backward Revisit
                    println("[Must Message] : The backward revisit phase begins")
                    val G2 = G.deepCopy()
                    G2.addEvent(nextEvent.deepCopy())
                    val possibleRecvs = calculatePossibleReceiveEvents(nextEvent as SendEvent, G2)
                    G2.computeProgramOrderReceiveFrom() // computes porf
                    val nextSendEvent = nextEvent
                    for (i in 0..<possibleRecvs.size) {
                        val newRecv = possibleRecvs[i]
                        if (!G2.porf.contains(Pair(nextSendEvent, newRecv))) {
                            G2.computeDeleted(newRecv, nextSendEvent)

                            var isAllowedRevist = true
                            G2.deleted.add(newRecv)
                            for (j in 0..<G2.deleted.size) {
                                if (!allowsRevisit(
                                        G2.deepCopy(),
                                        G2.deleted[j].deepCopy(),
                                        nextSendEvent.deepCopy()
                                    )
                                ) {
                                    isAllowedRevist = false
                                    break
                                }
                            }
                            if (isAllowedRevist) {
                                G2.deleted.remove(newRecv)
                                var G3 = G2.deepCopy()
                                G3 = G3.restrictingGraph()

                                val recv: ReceiveEvent
                                if (G3.graphEvents.contains(newRecv)) {
                                    recv = G3.graphEvents.find { it.equals(newRecv) } as ReceiveEvent
                                    recv.rf = nextSendEvent.deepCopy() as SendEvent
                                    G3.removeRecvFrom(recv)
                                    G3.addRecvFrom(recv.rf as Event, recv)
                                    println("[Must Message] : The event $recv revisits event $nextSendEvent")
                                }
                                visit(G3, deepCopyAllEvents(allEvents))
                            }
                        }
                    }
                }

                nextEvent.type == EventType.START -> {
                    println("[Must Message] : The next event is a START event -> $nextEvent")
                    val threadId = (nextEvent as StartEvent).callerThread
                    var threadEvent = findLastEvent(G, threadId)
                    if (threadEvent != null) {
                        // From the list of G.graphEvents, find the last inserted START event where its callerThread is equal to the threadId of the nextEvent and store it in threadEvent
                        val prevStart =
                            G.graphEvents.filter { it.type == EventType.START && (it as StartEvent).callerThread == nextEvent.callerThread }
                                .lastOrNull()
                        G.addEvent(nextEvent)
                        if (prevStart != null) {
                            G.addTC(prevStart, nextEvent)
                        }
                        G.addST(threadEvent, nextEvent)
                        visit(G, allEvents)
                    }
                }

                nextEvent.type == EventType.JOIN -> {
                    println("[Must Message] : The next event is a JOIN event -> $nextEvent")
                    val threadId = (nextEvent as JoinEvent).joinTid
                    val finishEvent = findFinishEvent(G, threadId)
                    if (finishEvent != null) {
                        G.addEvent(nextEvent)
                        G.addJT(finishEvent, nextEvent)
                        visit(G, allEvents)
                    }
                }

                nextEvent.type == EventType.FINISH -> {
                    println("[Must Message] : The next event is a FINISH event -> $nextEvent")
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.FAILURE -> {
                    println("[Must Message] : The next event is a FAILURE event -> $nextEvent")
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.DEADLOCK -> {
                    println("[Must Message] : The next event is a DEADLOCK event -> $nextEvent")
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.MAIN_START -> {
                    println("[Must Message] : The next event is a MAIN_START event -> $nextEvent")
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.SYM_EXECUTION -> {
                    // The following is for debugging purposes only
                    println("[Must Message] : The next event is a SYM_EXECUTION event -> $nextEvent")
                    var nextSymEvent = nextEvent as SymExecutionEvent
                    if (nextSymEvent.isNegatable) {
                        val G1 = G.deepCopy()
                        val negatedSymEvent = nextSymEvent.deepCopy() as SymExecutionEvent
                        negatedSymEvent.result = !negatedSymEvent.result
                        negatedSymEvent.formula = "(not (${negatedSymEvent.formula}))"
                        G1.addEvent(negatedSymEvent)
                        visit(G1, allEvents)
                    }
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                else -> { // TODO() : For possible future extensions
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }
            }
        } else {
            println("[Must Message] : The graph is not consistent")
        }
    }

    private fun findNext(allEvents: MutableList<Event>): Event? {
        return if (allEvents.isNotEmpty()) allEvents.removeFirst()
        else null
    }

    fun satisfiesCondition(sendEvent: SendEvent, receiveEvent: ReceiveEvent, executionGraph: ExecutionGraph): Boolean {
        if (sendEvent.receiverId == receiveEvent.tid.toLong()) {
            for (i in executionGraph.recvFrom.indices) {
                val firstElement = executionGraph.recvFrom.elementAt(i).first as SendEvent
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

    private fun isSendRecvPair(sendEvent: SendEvent, receiveEvent: ReceiveEvent): Boolean {
        return sendEvent.receiverId == receiveEvent.tid.toLong()
    }

    private fun isSendSatisfiesPredicate(sendEvent: SendEvent, receiveEvent: ReceiveEvent): Boolean {
        if (receiveEvent.predicate != null) {
            return receiveEvent.predicate!!.apply(sendEvent.tid.toLong(), sendEvent.tag!!.toLong())
        } else {
            return true
        }
    }

    private fun isSendFree(sendEvent: SendEvent, executionGraph: ExecutionGraph): Boolean {
        for (i in executionGraph.recvFrom.indices) {
            if (executionGraph.recvFrom.elementAt(i).first is SendEvent) {
                val firstElement = executionGraph.recvFrom.elementAt(i).first as SendEvent
                if ((firstElement.tid == sendEvent.tid) && (firstElement.serial == sendEvent.serial)) {
                    return false
                }
            }
        }
        return true
    }

    private fun getRecvOfSend(sendEvent: SendEvent, executionGraph: ExecutionGraph): ReceiveEvent? {
        for (i in executionGraph.recvFrom.indices) {
            if (executionGraph.recvFrom.elementAt(i).first is SendEvent) {
                val firstElement = executionGraph.recvFrom.elementAt(i).first as SendEvent
                if ((firstElement.tid == sendEvent.tid) && (firstElement.serial == sendEvent.serial)) {
                    return executionGraph.recvFrom.elementAt(i).second as ReceiveEvent
                }
            }
        }
        return null
    }

    private fun deepCopyAllEvents(allEvents: MutableList<Event>): MutableList<Event> {
        val newAllEvents: MutableList<Event> = mutableListOf()
        for (i in 0..<allEvents.size) {
            newAllEvents.add(allEvents[i].deepCopy())
        }
        return newAllEvents
    }

    private fun calculatePossibleReceiveEvents(sendEvent: SendEvent, graph: ExecutionGraph): MutableList<ReceiveEvent> {
        val possibleReceiveEvents: MutableList<ReceiveEvent> = mutableListOf()
        for (i in 0..<graph.graphEvents.size) {
            if (graph.graphEvents[i].type == EventType.RECEIVE) {
                val receiveEvent = graph.graphEvents[i] as ReceiveEvent
                if (isSendRecvPair(sendEvent, receiveEvent) && isSendSatisfiesPredicate(sendEvent, receiveEvent)) {
                    possibleReceiveEvents.add(receiveEvent)
                }
            }
        }
        return possibleReceiveEvents
    }

    private fun allowsRevisit(graph: ExecutionGraph, firstEvent: Event, secondEvent: Event): Boolean {

        if (firstEvent is ReceiveEvent) {
            val recvEvent = firstEvent
            if (!recvEvent.blocking) { // For non-blocking receive events
                return recvEvent.rf is InitializationEvent
            }
        }

        graph.computePrevious(firstEvent, secondEvent)
        if (firstEvent is ReceivesFrom) {
            for (i in 0..<graph.previous.size) {
                if (graph.previous[i].type == EventType.RECEIVE) {
                    val recv = graph.previous[i] as ReceiveEvent
                    if (recv.rf!!.equals(firstEvent)) {
                        return false
                    }
                }
            }
            return true
        }

        // It is guaranteed that the first event is a blocking receive event
        if (firstEvent is ReceiveEvent) {
            val candidateSend = getConsTieBreaker(graph, firstEvent)
            if (candidateSend != null) {
                val actualSend = firstEvent.rf as SendEvent
                return candidateSend.tid == actualSend.tid && candidateSend.serial == actualSend.serial
            } else {
                println("[Must Message] : **Error** the tie breaker is null")
                return false
            }
        }

        // For all other cases like BlockingRecvReq, BlockedRecv, UnblockedRecv
        return true
    }

    private fun getConsTieBreaker(graph: ExecutionGraph, recv: ReceiveEvent): SendEvent {
        var minimalSendEvent: SendEvent? = null
        for (i in 0..<graph.previous.size) {
            if (graph.previous[i].type == EventType.SEND) {
                val send = graph.previous[i] as SendEvent
                if (isSendRecvPair(send, recv) && isSendSatisfiesPredicate(send, recv)) {
                    val recvOfSend = getRecvOfSend(send, graph)
                    if (recvOfSend != null) {
                        if (recvOfSend.serial != recv.serial) {
                            continue
                        }
                    }
                    minimalSendEvent = updateMinimalSendEvent(minimalSendEvent, send)
                }
            }
        }
        return minimalSendEvent!!
    }

    private fun updateMinimalSendEvent(currentMinimalSendEvent: SendEvent?, newSendEvent: SendEvent): SendEvent {
        if (currentMinimalSendEvent == null) {
            return newSendEvent
        } else if (newSendEvent.tid < currentMinimalSendEvent.tid) {
            return newSendEvent
        } else if (newSendEvent.tid == currentMinimalSendEvent.tid) {
            if (newSendEvent.serial < currentMinimalSendEvent.serial) {
                return newSendEvent
            }
        }
        return currentMinimalSendEvent
    }
}