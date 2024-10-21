package dpor


import consistencyChecking.memoryConsistency.SequentialConsistency
import executionGraph.CO
import executionGraph.EventNode
import executionGraph.ExecutionGraph
import programStructure.*
import kotlin.system.exitProcess

/*
 In This Version of the Trust, we assume that there is no any error in threads execution
 Moreover, we assume that threads will not be blocked in their execution
 TODO() : Extends this algorithm by discharging the above-mentioned assumptions
 */

class Trust(path: String, verbose: Boolean) : DPOR(path, verbose) {
//    private var graph: ExecutionGraph = ExecutionGraph()
//    var allAbstractThread: MutableMap<Int, AbstractThread>? = mutableMapOf()
//    var allEvents: MutableList<Event> = mutableListOf()
//
//
//    init {
//        val init: Event = InitializationEvent()
//        this.allEvents.add(init)
//    }
//
//    /*
//     * TODO() : This function is not needed in the final version of the Trust algorithm
//     */
//    private fun makeAllEvents() {
//        for (i in this.allAbstractThread!!.keys)
//            for (e in this.allAbstractThread?.get(i)?.instructions!!) {
//                this.allEvents.add(e)
//                println("[MC Message] : the event : $e")
//            }
//    }
//
//    fun setThreads(trds: MutableMap<Int, AbstractThread>?) {
//        this.allAbstractThread = trds
//        makeAllEvents()
//    }
//
//    fun printEvents() {
//        for (i in this.allEvents) {
//            when (i.type) {
//                EventType.READ -> {
//                    val read: ReadEvent? = i as ReadEvent?
//                    println(read)
//                }
//
//                EventType.RECEIVE -> {
//                    val receive: ReceiveEvent? = i as ReceiveEvent?
//                    println(receive)
//                }
//
//                EventType.WRITE -> {
//                    val write: WriteEvent = i as WriteEvent
//                    println(write)
//                }
//
//                EventType.SEND -> {
//                    val send: SendEvent = i as SendEvent
//                    println(send)
//                }
//
//                EventType.INITIAL -> {
//                    val init: InitializationEvent = i as InitializationEvent
//                    println(init)
//                }
//
//                EventType.START -> {
//                    val start: StartEvent = i as StartEvent
//                    println(start)
//                }
//
//                EventType.JOIN -> {
//                    val join: JoinEvent = i as JoinEvent
//                    println(join)
//                }
//
//                EventType.FINISH -> {
//                    val finish: FinishEvent = i as FinishEvent
//                    println(finish)
//                }
//
//                EventType.ENTER_MONITOR -> {
//                    val enterMonitor: EnterMonitorEvent = i as EnterMonitorEvent
//                    println(enterMonitor)
//                }
//
//                EventType.EXIT_MONITOR -> {
//                    val exitMonitor: ExitMonitorEvent = i as ExitMonitorEvent
//                    println(exitMonitor)
//                }
//
//                EventType.DEADLOCK -> {
//                    val deadlock: DeadlockEvent = i as DeadlockEvent
//                    println(deadlock)
//                }
//
//                EventType.MONITOR_REQUEST -> {
//                    val monitorRequestEvent: MonitorRequestEvent = i as MonitorRequestEvent
//                    println(monitorRequestEvent)
//                }
//
//                EventType.FAILURE -> {
//                    val failureEvent: FailureEvent = i as FailureEvent
//                    println(failureEvent)
//                }
//
//                EventType.SUSPEND -> {
//                    val suspendEvent: SuspendEvent = i as SuspendEvent
//                    println(suspendEvent)
//                }
//
//                EventType.UNSUSPEND -> {
//                    val unsuspendEvent: UnsuspendEvent = i as UnsuspendEvent
//                    println(unsuspendEvent)
//                }
//
//                EventType.SYM_EXECUTION -> {
//                    val symExecutionEvent: SymExecutionEvent = i as SymExecutionEvent
//                    println(symExecutionEvent)
//                }
//
//                EventType.PARK -> {
//                    val parkEvent: ParkEvent = i as ParkEvent
//                    println(parkEvent)
//                }
//
//                EventType.UNPARK -> {
//                    val unparkEvent: UnparkEvent = i as UnparkEvent
//                    println(unparkEvent)
//                }
//
//                EventType.UNPARKING -> {
//                    val unparkingEvent: UnparkingEvent = i as UnparkingEvent
//                    println(unparkingEvent)
//                }
//
//                EventType.UNBLOCKED_RECV -> {
//                    val unblockedRecvEvent: UnblockedRecvEvent = i as UnblockedRecvEvent
//                    println(unblockedRecvEvent)
//                }
//
//                EventType.BLOCKED_RECV -> {
//                    val blockedRecvEvent: BlockedRecvEvent = i as BlockedRecvEvent
//                    println(blockedRecvEvent)
//                }
//
//                EventType.BLOCK_RECV_REQ -> {
//                    val blockRecvReqEvent: BlockingRecvReq = i as BlockingRecvReq
//                    println(blockRecvReqEvent)
//                }
//
//                EventType.OTHER -> TODO()
//            }
//        }
//
//    }
//
//    // This is the 'procedure VERIFY(P) in Algorithm 1'
//    fun verify() {
//        graph.addRoot(this.findNext(allEvents)!!)
//        visit(graph, allEvents)
//    }

    /*
     This is the 'procedure VISIT(P,G) in Algorithm 1
     Where 'P' is "allEvents"
     */
    override fun visit(G: ExecutionGraph, allEvents: MutableList<Event>) {
        /*
         To use consistency checking of Kater's Algorithm use the following
         */
        val graphConsistency = SequentialConsistency.scAcyclicity(G)

        /*
         To use consistency checking of Trust's Algorithm use the following
         */
        // val graphConsistency = SequentialConsistency.porfAcyclicity(G)

        //println("[Trust Message] : Model checking started")
        if (graphConsistency) {
            //println("[Trust Message] : The graph G_${G.id} is consistent")
            val nextEvent = findNext(allEvents)
            println("[Trust Debugging Message] : The next event is : $nextEvent")
            println("[Trust Debugging Message] : The next event type is : ${nextEvent?.type}")
            when {
                nextEvent == null -> {
                    this.graphCounter++
                    G.id = this.graphCounter
                    println("[Trust Message] : Visited full execution graph G_$graphCounter")
                    if (verbose) {
                        G.visualizeGraph(this.graphCounter, this.graphsPath)
                    }
                    allGraphs.add(G)
                    //G.printEvents()
                    //G.printPorf()
                    //G.printSc()
                    /*
                     For printing the resulted graph in text-based version,
                     use the following

                     G.printEvents()
                     G.printGraph()
                     G.printPorf()
                     */
                }

                nextEvent.type == EventType.READ -> {
                    println("[Trust Message] : The next event is a READ event -> $nextEvent")
                    val G1 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val nextReadEvent = (nextEvent as ReadEvent)
                    for (i in 0..<G.graphEvents.size) {
                        if (G.graphEvents[i].type == EventType.WRITE) {
                            var G2 = G1.deepCopy()
                            var findWriteEvent = G.graphEvents[i] as WriteEvent
                            val newNextEvent = nextReadEvent.deepCopy()
                            val newNextReadEvent = newNextEvent as ReadEvent
                            if (locEquals(findWriteEvent.loc!!, nextReadEvent.loc!!)) {
                                newNextReadEvent.rf = (findWriteEvent.deepCopy()) as WriteEvent
                                G2.addEvent(newNextReadEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                //println("[Trust Message] : Forward Revisit(R -> W) : ($newNextReadEvent, $findWriteEvent)")
                                visit(G2, newAllEvents)
                            }
                        }
//                        else if (G.graphEvents[i].type == EventType.INITIAL) {
//                            var G3 = G1.deepCopy()
//                            val newNextEvent = nextReadEvent.deepCopy()
//                            val newNextReadEvent = newNextEvent as ReadEvent
//                            newNextReadEvent.rf = (G.graphEvents[i].deepCopy()) as InitializationEvent
//                            G3.addEvent(newNextReadEvent as Event)
//                            val newAllEvents = deepCopyAllEvents(allEvents)
//                            println("[Trust Message] : Forward Revisit(R -> I) : ($newNextReadEvent, ${G.graphEvents[i]})")
//                            visit(G3, newAllEvents)
//                        }
                    }
                }

                nextEvent.type == EventType.READ_EX -> {
                    println("[Trust Message] : The next event is a READ_EX event -> $nextEvent")
                    val G1 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val nextReadExEvent = (nextEvent as ReadExEvent)
                    for (i in 0..<G.graphEvents.size) {
                        if (G.graphEvents[i].type == EventType.WRITE_EX) {
                            var G2 = G1.deepCopy()
                            var findWriteExEvent = G.graphEvents[i] as WriteExEvent
                            val newNextEvent = nextReadExEvent.deepCopy()
                            val newNextReadExEvent = newNextEvent as ReadExEvent
                            if (findWriteExEvent.operationSuccess && locEquals(
                                    findWriteExEvent.loc!!,
                                    nextReadExEvent.loc!!
                                )
                            ) {
                                newNextReadExEvent.rf = (findWriteExEvent.deepCopy()) as WriteExEvent
                                newNextReadExEvent.intValue = findWriteExEvent.intValue
                                G2.addEvent(newNextReadExEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                println("[Trust Message] : Forward Revisit(R -> W) : ($newNextReadExEvent, $findWriteExEvent)")
                                visit(G2, newAllEvents)
                            }
                        } else if (G.graphEvents[i].type == EventType.WRITE) {
                            var G2 = G1.deepCopy()
                            var findWriteEvent = G.graphEvents[i] as WriteEvent
                            val newNextEvent = nextReadExEvent.deepCopy()
                            val newNextReadExEvent = newNextEvent as ReadExEvent
                            if (locEquals(findWriteEvent.loc!!, nextReadExEvent.loc!!)) {
                                newNextReadExEvent.rf = (findWriteEvent.deepCopy()) as WriteEvent
                                newNextReadExEvent.intValue = findWriteEvent.value as Int
                                G2.addEvent(newNextReadExEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                println("[Trust Message] : Forward Revisit(R -> W) : ($newNextReadExEvent, $findWriteEvent)")
                                visit(G2, newAllEvents)
                            }
                        } else if (G.graphEvents[i].type == EventType.INITIAL) {
                            var G3 = G1.deepCopy()
                            val newNextEvent = nextReadExEvent.deepCopy()
                            val newNextReadExEvent = newNextEvent as ReadExEvent
                            newNextReadExEvent.rf = (G.graphEvents[i].deepCopy()) as InitializationEvent
                            newNextReadExEvent.intValue = 0
                            G3.addEvent(newNextReadExEvent as Event)
                            val newAllEvents = deepCopyAllEvents(allEvents)
                            println("[Trust Message] : Forward Revisit(R -> I) : ($newNextReadExEvent, ${G.graphEvents[i]})")
                            visit(G3, newAllEvents)
                        }
                    }
                }

                nextEvent.type == EventType.WRITE_EX -> {
                    println("[Trust Message] : The next event is a WRITE_EX event -> $nextEvent")
                    val G1 = G.deepCopy()
                    val nextWriteExEvent = (nextEvent as WriteExEvent)
                    val readExEvent = findLastEvent(G1, nextWriteExEvent.tid) as ReadExEvent
                    if (readExEvent == null) {
                        println("[Trust Message] : The readExEvent is null")
                        // Terminate the program
                        exitProcess(0)
                    }

                    if (readExEvent.intValue == nextWriteExEvent.conditionValue) {
                        nextWriteExEvent.operationSuccess = true
                        //println("[Trust Debugging Message] : The operation is successful")
                        // Forward Revisits
                        if (G1.areExReadsConsistent(nextWriteExEvent.loc!!)) {
                            //println("[Trust Debugging Message] : The ExReads are consistent")
                            val G2 = G1.deepCopy()
                            val newAllEvents = deepCopyAllEvents(allEvents)
                            visitCOs(G2, nextWriteExEvent.deepCopy() as WriteExEvent, newAllEvents)
                        } else {
                            println("[Trust Message] : The ExReads are not consistent. The graph is discarded")
                        }

                        // Backward Revisits
                        val G3 = G.deepCopy()
                        G3.addEvent(nextWriteExEvent.deepCopy())
                        G3.computeProgramOrderReadFrom()
                        for (i in 0..<G3.graphEvents.size) {
                            if (G3.graphEvents[i] is ReadEvent) {
                                val findReadExEvent = G3.graphEvents[i] as ReadEvent
                                if (locEquals(findReadExEvent.loc!!, nextWriteExEvent.loc!!) && !G3.porf.contains(
                                        Pair(
                                            findReadExEvent,
                                            nextWriteExEvent
                                        )
                                    )
                                ) {
                                    G3.computeDeleted(findReadExEvent, nextWriteExEvent)
                                    var allIsMaximal = true
                                    G3.deleted.add(findReadExEvent)
                                    for (j in 0..<G3.deleted.size) {
                                        if (!isMaximallyAdded(
                                                G3.deepCopy(),
                                                G3.deleted[j].deepCopy(),
                                                nextWriteExEvent.deepCopy()
                                            )
                                        ) {
                                            allIsMaximal = false
                                            break
                                        }
                                    }
                                    if (allIsMaximal) {
                                        G3.deleted.remove(findReadExEvent)
                                        var newNewAllEvents = deepCopyAllEvents(allEvents)

                                        if (findReadExEvent is ReadExEvent) {
                                            // In the G3.deleted set, find the event which its type is WRITE_EX and its tid is equal to the tid of the findReadExEvent and its serial number is exactly one more than the serial number of the findReadExEvent
                                            // if exists, store it in candidateWriteExEvent
                                            var candidateWriteExEvent: WriteExEvent? = null
                                            for (j in 0..<G3.deleted.size) {
                                                if (G3.deleted[j].type == EventType.WRITE_EX) {
                                                    val writeEx = G3.deleted[j] as WriteExEvent
                                                    if (writeEx.tid == findReadExEvent.tid && writeEx.serial == findReadExEvent.serial + 1) {
                                                        candidateWriteExEvent = writeEx
                                                        break
                                                    }
                                                }
                                            }
                                            newNewAllEvents.add(candidateWriteExEvent!!.deepCopy())
                                        }

                                        var G4 = G3.deepCopy()
                                        G4 = G4.restrictingGraph()
                                        val readEx: ReadExEvent
                                        if (G4.graphEvents.contains(findReadExEvent)) {
                                            readEx = G4.graphEvents.find { it.equals(findReadExEvent) } as ReadExEvent
                                            readEx.rf = nextWriteExEvent.deepCopy() as WriteExEvent
                                            readEx.intValue = nextWriteExEvent.intValue
                                        }
                                        if (G4.areExReadsConsistent(nextWriteExEvent.loc!!)) {
                                            visitCOs(
                                                G4.deepCopy(),
                                                nextWriteExEvent.deepCopy() as WriteExEvent,
                                                newNewAllEvents
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        //println("[Trust Debugging Message] : The condition of the writeExEvent is not satisfied")
                        nextWriteExEvent.operationSuccess = false
//                        if (G1.areExReadsConsistent(nextWriteExEvent.loc!!)) {
//                            G1.addEvent(nextWriteExEvent)
//                            visit(G1, allEvents)
//                        }
                        G1.addEvent(nextWriteExEvent)
                        visit(G1, allEvents)
                    }
                }

                nextEvent.type == EventType.WRITE -> {
                    println("[Trust Message] : The next event is a WRITE event -> $nextEvent")
                    val G3 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val newAllEvents = deepCopyAllEvents(allEvents)
                    visitCOs(G3.deepCopy(), nextEvent.deepCopy() as WriteEvent, newAllEvents)
                    G3.addEvent(nextEvent.deepCopy())
                    G3.computeProgramOrderReadFrom()
                    for (i in 0..<G3.graphEvents.size) {
                        if (G3.graphEvents[i] is ReadEvent) {
                            val findReadEvent = G3.graphEvents[i] as ReadEvent
                            val nextWriteEvent = nextEvent as WriteEvent
                            if (locEquals(findReadEvent.loc!!, nextWriteEvent.loc!!) && !G3.porf.contains(
                                    Pair(
                                        findReadEvent,
                                        nextEvent
                                    )
                                )
                            ) {
                                G3.computeDeleted(findReadEvent, nextEvent)

                                var allIsMaximal = true
                                G3.deleted.add(findReadEvent)
                                for (j in 0..<G3.deleted.size) {
                                    if (!isMaximallyAdded(
                                            G3.deepCopy(),
                                            G3.deleted[j].deepCopy(),
                                            nextEvent.deepCopy()
                                        )
                                    ) {
                                        allIsMaximal = false
                                        break
                                    }
                                }
                                if (allIsMaximal) {
                                    G3.deleted.remove(findReadEvent)
                                    var newNewAllEvents = deepCopyAllEvents(allEvents)

                                    if (findReadEvent is ReadExEvent) {
                                        var findReadExEvent = findReadEvent
                                        var candidateWriteExEvent: WriteExEvent? = null
                                        for (j in 0..<G3.deleted.size) {
                                            if (G3.deleted[j].type == EventType.WRITE_EX) {
                                                val writeEx = G3.deleted[j] as WriteExEvent
                                                if (writeEx.tid == findReadExEvent.tid && writeEx.serial == findReadExEvent.serial + 1) {
                                                    candidateWriteExEvent = writeEx
                                                    break
                                                }
                                            }
                                        }
                                        newNewAllEvents.add(candidateWriteExEvent!!.deepCopy())
                                    }
                                    var G4 = G3.deepCopy()
                                    G4 = G4.restrictingGraph()
                                    val read: ReadEvent
                                    if (G4.graphEvents.contains(findReadEvent)) {
                                        read = G4.graphEvents.find { it.equals(findReadEvent) } as ReadEvent
                                        read.rf = nextEvent.deepCopy() as WriteEvent
                                        if (read is ReadExEvent) {
                                            read.intValue = nextEvent.value as Int
                                        }
                                    }
                                    visitCOs(G4.deepCopy(), nextEvent.deepCopy() as WriteEvent, newNewAllEvents)
                                }
                            }
                        }
                    }
                }

                nextEvent.type == EventType.JOIN -> {

                    // The following is for debugging purposes only
                    //println("[Trust Message] : The next event is a JOIN event")
                    //println("[Trust Message] : The JOIN event is : $nextEvent")

                    val threadId = (nextEvent as JoinEvent).joinTid
                    val finishEvent = findFinishEvent(G, threadId)
                    if (finishEvent != null) {
                        //println("[Trust Message] : The finish event is found")
                        //println("[Trust Message] : The finish event is : $finishEvent")
                        G.addEvent(nextEvent)
                        G.addJT(finishEvent, nextEvent)
                        visit(G, allEvents)
                    }
                }

                nextEvent.type == EventType.START -> {
                    // The following is for debugging purposes only
                    println("[Trust Message] : The next event is a START event")
                    println("[Trust Message] : The START event is : $nextEvent")

                    val threadId = (nextEvent as StartEvent).callerThread
                    val threadEvent = findLastEvent(G, threadId)
                    if (threadEvent != null) {
                        //println("[Trust Debugging] : The starter thread event is found : $threadEvent")
                        //println("[Trust Message] : The thread event is found")
                        //println("[Trust Message] : The thread event is : $threadEvent")
                        // From the list of G.graphEvents, find the last inserted START event where its callerThread is equal to the threadId of the nextEvent and store it in threadEvent
                        val prevStart =
                            G.graphEvents.filter { it.type == EventType.START && (it as StartEvent).callerThread == nextEvent.callerThread }
                                .lastOrNull()
                        G.addEvent(nextEvent)
                        if (prevStart != null) {
                            G.addTC(prevStart, nextEvent)
                        }
                        G.addST(threadEvent, nextEvent)
                        println("[Trust Debugging] : The graph ST is : ")
                        println(G.STs)
                        visit(G, allEvents)
                    }
                }

                nextEvent.type == EventType.FINISH -> {
                    // The following is for debugging purposes only
                    //println("[Trust Message] : The next event is a FINISH event")
                    //println("[Trust Message] : The FINISH event is : $nextEvent")

                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.FAILURE -> {
                    // The following is for debugging purposes only
                    //println("[Trust Message] : The next event is a FAILURE event")
                    //println("[Trust Message] : The FAILURE event is : $nextEvent")

                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.DEADLOCK -> {
                    // The following is for debugging purposes only
                    //println("[Trust Message] : The next event is a DEADLOCK event")
                    //println("[Trust Message] : The DEADLOCK event is : $nextEvent")

                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.MONITOR_REQUEST -> {
                    // The following is for debugging purposes only
                    //println("[Trust Message] : The next event is a MONITOR_REQUEST event")
                    //println("[Trust Message] : The MONITOR_REQUEST event is : $nextEvent")

                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.ENTER_MONITOR -> {
                    //println("[Trust Message] : The next event is a ENTER_MONITOR event")
                    //println("[Trust Message] : The ENTER_MONITOR event is : $nextEvent")
                    val suspendEvent = isLastEventSusspende(G, (nextEvent as ThreadEvent).tid)
                    // For the given ENTER_MONITOR event, find the last SUSPEND event in the same thread if exists
                    //println("[Trust debugging] : The suspend event is found : $suspendEvent")
                    if (suspendEvent != null) {
                        //println("[Trust Message] : The suspend event is found")
                        //println("[Trust Message] : The suspend event is : $suspendEvent")
                        var nextExitMonitorEvent = findExitMonitorEvent(G, suspendEvent)
                        //println("[Trust Message] : The next exit monitor event is : $nextExitMonitorEvent")
                        if (nextExitMonitorEvent != null) {
                            //println("[Trust Message] : The next exit monitor event is found")
                            //println("[Trust Message] : The next exit monitor event is : $nextExitMonitorEvent")
                            //removeSuspend(G, suspendEvent)
                            turnSuspendToEnterMonitor(G, suspendEvent, nextEvent as EnterMonitorEvent)
                            //G.addEvent(nextEvent)
                            G.addMC(nextExitMonitorEvent as Event, nextEvent)
                            val newAllEvents = deepCopyAllEvents(allEvents)
                            visit(G, newAllEvents)
                        }
                    } else {
                        val G1 = G.deepCopy()
                        G.addEvent(nextEvent)
                        val nextEnterMonitorEvent = (nextEvent as EnterMonitorEvent)

                        // FORWARD REVISITS
                        //System.out.println("[Trust Message] : Forward Revisit(EnM -> ExM) : $nextEnterMonitorEvent")
                        for (i in 0..<G.graphEvents.size) {
                            if (G.graphEvents[i].type == EventType.EXIT_MONITOR) {
                                val findExitMonitorEvent = G.graphEvents[i] as ExitMonitorEvent
                                if (monitorEquals(nextEnterMonitorEvent.monitor!!, findExitMonitorEvent.monitor!!) &&
                                    isExitMonitorEventFree(G, findExitMonitorEvent)
                                ) {
                                    var G2 = G1.deepCopy()
                                    val newNextEnterMonitorEvent = nextEnterMonitorEvent.deepCopy() as EnterMonitorEvent
                                    G2.addEvent(newNextEnterMonitorEvent)
                                    G2.addMC(findExitMonitorEvent, newNextEnterMonitorEvent)
                                    val newAllEvents = deepCopyAllEvents(allEvents)
                                    visit(G2, newAllEvents)
                                }
                            }
                        }

                        // BACKWARD REVISITS
                        G1.addEvent(nextEvent.deepCopy())
                        G1.computeProgramOrderReadFrom()
                        for (i in 0..<G1.graphEvents.size) {
                            val findEnterMonitorEvent: EnterMonitorEvent
                            if (G1.graphEvents[i].type == EventType.ENTER_MONITOR) {
                                findEnterMonitorEvent = G1.graphEvents[i] as EnterMonitorEvent
                                if (!findEnterMonitorEvent.equals(nextEnterMonitorEvent) &&
                                    monitorEquals(findEnterMonitorEvent.monitor!!, nextEnterMonitorEvent.monitor!!) &&
                                    !G1.porf.contains(Pair(findEnterMonitorEvent, nextEnterMonitorEvent))
                                ) {
                                    var G2 = G1.deepCopy()
                                    G2.computeDeleted(findEnterMonitorEvent, nextEnterMonitorEvent)
                                    // Check if in the G2.MCs there is a pair with the second element equal to the findEnterMonitorEvent
                                    // if exists, finds its first element and add the pair of the first element and the nextEnterMonitorEvent to the G2.MCs.
                                    val matchingPair = G2.MCs.find { it.second == findEnterMonitorEvent }
                                    if (matchingPair != null) {
                                        val exitMonitorEvent = matchingPair.first as ExitMonitorEvent
                                        G2.addMC(exitMonitorEvent, nextEnterMonitorEvent)
                                        G2.MCs.remove(matchingPair)
                                    } else {
                                        // In the G2.root.children, traverse the branch of findEnterMonitorEvent.tid, till reaching the corresponding node of findEnterMonitorEvent,
                                        // if there were ExitMonitor event which its monitor object was equal to findEnterMonitorEvent, store the last of them
                                        var eventNode = G2.root?.children?.get(findEnterMonitorEvent.tid)
                                        var exitMonitorEvent: ExitMonitorEvent? = null
                                        while (eventNode != null) {
                                            if (eventNode.value.equals(findEnterMonitorEvent)) {
                                                break
                                            } else if (eventNode.value.type == EventType.EXIT_MONITOR) {
                                                val exitMonitor = eventNode.value as ExitMonitorEvent
                                                if (monitorEquals(
                                                        exitMonitor.monitor!!,
                                                        findEnterMonitorEvent.monitor
                                                    )
                                                ) {
                                                    exitMonitorEvent = exitMonitor
                                                }
                                            }
                                            eventNode = eventNode.child
                                        }
                                        if (exitMonitorEvent != null) {
                                            G2.addMC(exitMonitorEvent, nextEnterMonitorEvent)
                                        }
                                    }
                                    G2.addMC(nextEnterMonitorEvent, findEnterMonitorEvent)
                                    G2.turnEnterMonitorToSuspend(findEnterMonitorEvent)
                                    // For each event in the deleted set, check if it is an EnterMonitorEvent, if it is, call the turnEnterMonitorToSuspend

                                    var notDeleted: MutableList<Event> = mutableListOf()
                                    for (j in 0..<G2.deleted.size) {
                                        if (G2.deleted[j].type == EventType.ENTER_MONITOR) {
                                            val enterMonitor = G2.deleted[j] as EnterMonitorEvent
                                            if (enterMonitor.monitor!!.equals(findEnterMonitorEvent.monitor) &&
                                                enterMonitor.tid != findEnterMonitorEvent.tid
                                            ) {
                                                // Removing enter monitor (suspend) event and all PO-before events from the deleted set
                                                notDeleted.add(enterMonitor)
                                                for (k in 0..<G2.deleted.size) {
                                                    val threadEvent = G2.deleted[k] as ThreadEvent
                                                    if (threadEvent.tid == enterMonitor.tid && threadEvent.serial < enterMonitor.serial) {
                                                        notDeleted.add(threadEvent)
                                                    }
                                                }
                                                G2.turnEnterMonitorToSuspend(enterMonitor)
                                            }
                                        }
                                    }

                                    if (notDeleted.isNotEmpty()) {
                                        for (event in notDeleted) {
                                            G2.deleted.remove(event)
                                        }
                                    }

                                    // For each pair in G2.MCs where the second element is a suspendEvent and first element is an ExitMonitorEvent
                                    // find the suspendEvent in the root.children[exitMonitorEvent.tid] and add the pair of the founded suspendEvent and the given suspendEvent to the G2.MCs
                                    var newMC: MutableSet<Pair<Event, Event>> = mutableSetOf()
                                    for (pair in G2.MCs) {
                                        if (pair.second.type == EventType.SUSPEND && pair.first.type == EventType.EXIT_MONITOR) {
                                            //println("[Trust Kt Debugging] : The pair is : $pair")
                                            val exitMonitor = pair.first as ExitMonitorEvent
                                            val suspend = pair.second as SuspendEvent
                                            var eventNode = G2.root?.children?.get(exitMonitor.tid)
                                            while (eventNode != null) {
                                                //if (eventNode.value.equals(suspend)) {
                                                if (eventNode.value.type == EventType.SUSPEND) {
                                                    newMC.add(Pair(eventNode.value, suspend))
                                                    break
                                                } else {
                                                    eventNode = eventNode.child
                                                }
                                            }
                                        }
                                    }
                                    if (newMC.isNotEmpty()) {
                                        //println("[Trust Kt Debugging] : The newMC is : $newMC")
                                        G2.MCs.addAll(newMC)
                                    }
                                    //println("[Trust Kt Debugging] : The deleted set 2 is : ")
                                    G2.printDeleted()
                                    G2 = G2.restrictingGraph()
                                    val newAllEvents = deepCopyAllEvents(allEvents)
                                    visit(G2.deepCopy(), newAllEvents)
                                }
                            }
                        }
                    }
                }

                nextEvent.type == EventType.EXIT_MONITOR -> {
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.MAIN_START -> {
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.CON_ASSUME -> {
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.SYM_ASSUME -> {
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.ASSUME_BLOCKED -> {
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.SYM_EXECUTION -> {
                    // The following is for debugging purposes only
                    println("[Trust Message] : The next event is a SYM_EXECUTION event")
                    println("[Trust Message] : The SYM_EXECUTION event is : $nextEvent")
                    var nextSymEvent = nextEvent as SymExecutionEvent
                    if (nextSymEvent.isNegatable) {
                        val G1 = G.deepCopy()
                        val negatedSymEvent = nextSymEvent.deepCopy() as SymExecutionEvent
                        negatedSymEvent.result = !negatedSymEvent.result
                        //negatedSymEvent.formula = "(not (${negatedSymEvent.formula}))"
                        G1.addEvent(negatedSymEvent)
                        visit(G1, allEvents)
                    }
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.PARK -> {
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.UNPARK -> {
                    // Forward Revisits
                    println("[Trust Message] : The next event is a UNPARK event -> $nextEvent")
                    val G1 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val nextUnparkEvent = (nextEvent as UnparkEvent)
                    for (i in 0..<G.graphEvents.size) {
                        if (G.graphEvents[i].type == EventType.UNPARKING) {
                            var G2 = G1.deepCopy()
                            var findUnparkingEvent = G.graphEvents[i] as UnparkingEvent
                            val newNextEvent = nextUnparkEvent.deepCopy()
                            val newNextUnparkEvent = newNextEvent as UnparkEvent
                            if (findUnparkingEvent.unparkTid == nextUnparkEvent.tid && isUnparkingFree(
                                    G2,
                                    findUnparkingEvent
                                )
                            ) {
                                newNextUnparkEvent.unparkerTid = findUnparkingEvent.tid
                                G2.addPC(findUnparkingEvent, newNextUnparkEvent)
                                G2.addEvent(newNextUnparkEvent as Event)
                                val newAllEvents = deepCopyAllEvents(allEvents)
                                visit(G2, newAllEvents)
                            }
                        }
                    }
                }

                nextEvent.type == EventType.UNPARKING -> {
                    // Forward Revisits
                    println("[Trust Message] : The next event is a UNPARKING event -> $nextEvent")
                    val G1 = G.deepCopy()
                    var nextUnparkingEvent = nextEvent.deepCopy() as UnparkingEvent
                    G.addEvent(nextEvent)
                    val newAllEvents = deepCopyAllEvents(allEvents)
                    visit(G, newAllEvents)

                    // Backward Revisits
                    val G2 = G1.deepCopy()
                    G2.addEvent(nextEvent.deepCopy())
                    G2.computeProgramOrderReadFrom()
                    for (i in 0..<G2.graphEvents.size) {
                        val findUnparkEvent: UnparkEvent
                        if (G2.graphEvents[i].type == EventType.UNPARK) {
                            findUnparkEvent = G2.graphEvents[i] as UnparkEvent
                            if (findUnparkEvent.tid == nextUnparkingEvent.unparkTid &&
                                !G2.porf.contains(Pair(findUnparkEvent, nextUnparkingEvent))
                            ) {
                                var G3 = G2.deepCopy()
                                var unparkEvent: UnparkEvent
                                var unparkingEvent: UnparkingEvent
                                if (G3.graphEvents.contains(findUnparkEvent) && G3.graphEvents.contains(
                                        nextUnparkingEvent
                                    )
                                ) {
                                    unparkEvent = G3.graphEvents.find { it.equals(findUnparkEvent) } as UnparkEvent
                                    unparkingEvent =
                                        G3.graphEvents.find { it.equals(nextUnparkingEvent) } as UnparkingEvent
                                    unparkEvent.unparkerTid = nextUnparkingEvent.tid
                                    unparkingEvent.unparkTid = findUnparkEvent.tid

                                    val matchingPair = G3.PCs.find { it.second == unparkEvent }
                                    if (matchingPair != null) {
                                        G3.PCs.remove(matchingPair)
                                    }

                                    G3.addPC(unparkingEvent, unparkEvent)

                                    G3.computeDeleted(unparkEvent, unparkingEvent)
                                    G3.restrictingGraph()

                                    val newnewAllEvents = deepCopyAllEvents(allEvents)
                                    visit(G3.deepCopy(), newnewAllEvents)
                                }
                            }
                        }
                    }
                }

                else -> { // TODO() : For possible future extensions
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }
            }
        } else {
            println("[Trust Message] : The graph is not consistent")
            //G.printSc()
        }
    }

    private fun isExitMonitorEventFree(graph: ExecutionGraph, exitMonitorEvent: ExitMonitorEvent): Boolean {
        // In graph.MCS, check if there exists a pair which its first element is exitMonitorEvent, if exists return false, if not
        // check if the corresponding exitmonitor node in the root.childern has an enter monitor node in follows which its monitor is equal to the monitor of the exitMonitorEvent
        // if exists return false, if not return true
        val matchingPair = graph.MCs.find { it.first == exitMonitorEvent }
        if (matchingPair != null) {
            return false
        } else {
            var eventNode = graph.root?.children?.get(exitMonitorEvent.tid)
            var exitMonitorNode: EventNode? = null
            while (eventNode != null) {
                if (eventNode.value.equals(exitMonitorEvent)) {
                    exitMonitorNode = eventNode
                    break
                } else {
                    eventNode = eventNode.child
                }
            }
            eventNode = exitMonitorNode!!.child

            while (eventNode != null) {
                if (eventNode.value.type == EventType.ENTER_MONITOR) {
                    val enterMonitor = eventNode.value as EnterMonitorEvent
                    if (monitorEquals(enterMonitor.monitor!!, exitMonitorEvent.monitor!!)) {
                        return false
                    }
                }
                eventNode = eventNode.child
            }
            return true
        }
    }

    private fun isUnparkingFree(G: ExecutionGraph, unparkingEvent: UnparkingEvent): Boolean {
        for (pair in G.PCs) {
            if (pair.first.equals(unparkingEvent)) {
                return false
            }
        }
        return true
    }

    private fun findExitMonitorEvent(graph: ExecutionGraph, SuspendedEvent: SuspendEvent): ExitMonitorEvent? {
        // In graph.MCs, find the pair with its second element equal to the SuspendedEvent
        val matchingPair = graph.MCs.find { it.second == SuspendedEvent }
        //println("The matching pair is : $matchingPair")
        val enterMonitor = matchingPair?.first as EnterMonitorEvent
        //println("The enter monitor is : $enterMonitor")
        // In graph.root.children, find the value with its tid equal to the tid of the enterMonitor next in that branch, find the corresponding eventNode for the enterMonitor
        var EventNode = graph.root?.children?.get(enterMonitor.tid)
        var enterMonitorNode: EventNode? = null
        while (EventNode != null) {
            if (EventNode.value.equals(enterMonitor)) {
                enterMonitorNode = EventNode
                break
            } else {
                EventNode = EventNode.child
            }
        }
        //println("The enter monitor node is : $enterMonitorNode")
        // In the children of the enterMonitorNode, find the first ExitMonitorEvent
        var exitMonitorNode = enterMonitorNode!!.child
        while (exitMonitorNode != null) {
            if (exitMonitorNode.value.type == EventType.EXIT_MONITOR) {
                var exitMonitorEvent = exitMonitorNode.value as ExitMonitorEvent
                if (monitorEquals(exitMonitorEvent.monitor!!, enterMonitor.monitor!!) && isExitMonitorEventFree(
                        graph,
                        exitMonitorEvent
                    )
                ) {
                    return exitMonitorEvent
                } else {
                    exitMonitorNode = exitMonitorNode.child
                }
            } else {
                exitMonitorNode = exitMonitorNode.child
            }
        }
        return null
    }

    // TODO() : This function is not needed in the final version of the Trust algorithm
    private fun removeSuspend(graph: ExecutionGraph, suspendEvent: SuspendEvent): Boolean {
        // In graph.MCS, find the pair with its second element equal to the suspendEvent and remove it
        val matchingPair = graph.MCs.find { it.second == suspendEvent }
        graph.MCs.remove(matchingPair)
        // In graph.root.children, find the value with its tid equal to the tid of the suspendEvent next in that branch, find the eventNode which its next is the suspendEvent and make its next null
        var eventNode = graph.root?.children?.get(suspendEvent.tid)
        while (eventNode != null) {
            if (eventNode.child != null && eventNode.child!!.value.equals(suspendEvent)) {
                eventNode.child = null
                return true
            } else {
                eventNode = eventNode.child
            }
        }
        return false
    }

    private fun turnSuspendToEnterMonitor(
        graph: ExecutionGraph,
        suspendEvent: SuspendEvent,
        enterMonitorEvent: EnterMonitorEvent
    ) {
        //println("Entering the turnSuspendToEnterMonitor function")
        // In graph.MCS, find the pair with its second element equal to the suspendEvent and remove it
        var matchingPair = graph.MCs.find { it.second == suspendEvent }
        graph.MCs.remove(matchingPair)

        matchingPair = graph.MCs.find { it.first == suspendEvent }
        var second = matchingPair?.second
        if (second != null) {
            graph.MCs.remove(matchingPair)
            graph.MCs.add(Pair(enterMonitorEvent, second))
        }

        // In G.graphEvents, find the suspendEvent and remove it from the graphEvents.
        //println("Before removing the suspend event,")
        //graph.printEvents()
        val index = graph.graphEvents.indexOf(suspendEvent)
        graph.graphEvents.removeAt(index)
        graph.graphEvents.add(enterMonitorEvent)
        //println("After removing the suspend event, the graph events are,")
        //graph.printEvents()
        // In G.eventsOrder, find the suspendEvent and remove it from the eventsOrder.
        //println("Before removing the suspend event,")
        //graph.printEventsOrder()
        val index2 = graph.eventsOrder.indexOf(suspendEvent)
        graph.eventsOrder.removeAt(index2)
        graph.eventsOrder.add(enterMonitorEvent)
        //println("After removing the suspend event, the events order is,")
        //graph.printEventsOrder()

        // In the graph.porf, find the pair which its first or second element is the suspendEvent and replace that element with enterMonitorEvent
        val newPorf: MutableSet<Pair<Event, Event>> = mutableSetOf()
        for (pair in graph.porf) {
            if (pair.first.equals(suspendEvent)) {
                newPorf.add(Pair(enterMonitorEvent, pair.second))
            } else if (pair.second.equals(suspendEvent)) {
                newPorf.add(Pair(pair.first, enterMonitorEvent))
            } else {
                newPorf.add(pair)
            }
        }
        graph.porf = newPorf

        // In the graph.sc, find the pair which its first or second element is the suspendEvent and replace that element with enterMonitorEvent
        val newSc: MutableSet<Pair<Event, Event>> = mutableSetOf()
        for (pair in graph.sc) {
            if (pair.first.equals(suspendEvent)) {
                newSc.add(Pair(enterMonitorEvent, pair.second))
            } else if (pair.second.equals(suspendEvent)) {
                newSc.add(Pair(pair.first, enterMonitorEvent))
            } else {
                newSc.add(pair)
            }
        }

        // In the graph.deleted, find the suspendEvent and replace it with enterMonitorEvent
        if (graph.deleted.contains(suspendEvent)) {
            val index3 = graph.deleted.indexOf(suspendEvent)
            //println("The index of the suspend event in the deleted set is : $index3")
            graph.deleted[index3] = enterMonitorEvent
            //println("The deleted set is : ${graph.deleted}")
        }

        // In the graph.previous, find the suspendEvent and replace it with enterMonitorEvent
        if (graph.previous.contains(suspendEvent)) {
            val index4 = graph.previous.indexOf(suspendEvent)
            //println("The index of the suspend event in the previous set is : $index4")
            graph.previous[index4] = enterMonitorEvent
            //println("The previous set is : ${graph.previous}")
        }


        // In graph.root.children, find the value with its tid equal to the tid of the suspendEvent next in that branch, find the eventNode its value is equal to the suspendEvent and replace it with enterMonitorEvent
        var eventNode = graph.root?.children?.get(suspendEvent.tid)
        while (eventNode != null) {
            if (eventNode.value.equals(suspendEvent)) {
                eventNode.value = enterMonitorEvent
                return
            } else {
                eventNode = eventNode.child
            }
        }
    }

    private fun isLastEventSusspende(graph: ExecutionGraph, threadId: Int): SuspendEvent? {
        var EventNode = graph.root?.children?.get(threadId)
        while (EventNode != null) {
            if (EventNode.value.type == EventType.SUSPEND) {
                return EventNode.value as SuspendEvent
            } else {
                EventNode = EventNode.child
            }
        }
        return null
    }

    private fun isMaximallyAdded(graph: ExecutionGraph, firstEvent: Event, secondEvent: Event): Boolean {
        if (firstEvent.type == EventType.SYM_EXECUTION) {
            return isSatMaximal(firstEvent as SymExecutionEvent)
        }

        if (firstEvent.type == EventType.WRITE_EX) {
            val writeEx = firstEvent as WriteExEvent
            if (!writeEx.operationSuccess) {
                return true
            }
        }

        graph.computePrevious(firstEvent, secondEvent)
//        if (firstEvent is ReadsFrom) {
//            var isReadVisited = false
//            for (i in 0..<graph.previous.size) {
//                if (graph.previous[i].type == EventType.READ) {
//                    val read = graph.previous[i] as ReadEvent
//                    if (read.rf!!.equals(firstEvent)) {
//                        isReadVisited = true
//                    }
//                }
//                if (isReadVisited) break
//            }
//            if (isReadVisited) return false
//        }

        if (firstEvent is ReadsFrom) {
            for (i in 0..<graph.previous.size) {
                if (graph.previous[i].type == EventType.READ) {
                    val read = graph.previous[i] as ReadEvent
                    if (read.rf!!.equals(firstEvent)) {
                        return false
                    }
                } else if (graph.previous[i].type == EventType.READ_EX) {
                    val readEx = graph.previous[i] as ReadExEvent
                    if (readEx.rf!!.equals(firstEvent)) {
                        return false
                    }
                }
            }
        }

//        if (firstEvent is ExitMonitorEvent) {
//            var isEnterMonitorVisited = false
//            for (i in 0..<graph.previous.size) {
//                if (graph.previous[i].type == EventType.ENTER_MONITOR) {
//                    val enterMonitor = graph.previous[i] as EnterMonitorEvent
//                    if (graph.MCs.contains(Pair(firstEvent, enterMonitor))) {
//                        isEnterMonitorVisited = true
//                    }
//                }
//                if (isEnterMonitorVisited) break
//            }
//            if (isEnterMonitorVisited) return false
//        }

        // The "eventPrime" is the e' in the ISMAXIMALLYADDED procedure of the Trust algorithm
        val eventPrime: Event = when (firstEvent.type) {
            EventType.READ -> {
                ((firstEvent as ReadEvent).rf as Event).deepCopy()
            }

            EventType.READ_EX -> {
                ((firstEvent as ReadExEvent).rf as Event).deepCopy()
            }

//            EventType.ENTER_MONITOR -> {
//                val matchingPair = graph.MCs.find { it.second == firstEvent }
//                matchingPair?.first?.deepCopy() ?: firstEvent.deepCopy() // handle the case where no match is found
//            }

            else -> {
                firstEvent.deepCopy()
            }
        }

        if (graph.previous.contains(eventPrime)) {
            if (eventPrime.type == EventType.WRITE) {
                var isCoVisited = false
                for (i in 0..<graph.previous.size) {
                    if (graph.previous[i].type == EventType.WRITE) {
                        if (graph.COs.contains(CO(eventPrime as WriteEvent, graph.previous[i] as WriteEvent))) {
                            isCoVisited = true
                        }
                    } else if (graph.previous[i].type == EventType.WRITE_EX) {
                        if (graph.COs.contains(CO(eventPrime as WriteEvent, graph.previous[i] as WriteExEvent))) {
                            isCoVisited = true
                        }
                    }
                    if (isCoVisited) {
                        break
                    }
                }
                return !isCoVisited
            } else if (eventPrime.type == EventType.WRITE_EX) {
                var isCoVisited = false
                for (i in 0..<graph.previous.size) {
                    if (graph.previous[i].type == EventType.WRITE) {
                        if (graph.COs.contains(CO(eventPrime as WriteExEvent, graph.previous[i] as WriteEvent))) {
                            isCoVisited = true
                        }
                    } else if (graph.previous[i].type == EventType.WRITE_EX) {
                        if (graph.COs.contains(CO(eventPrime as WriteExEvent, graph.previous[i] as WriteExEvent))) {
                            isCoVisited = true
                        }
                    }
                    if (isCoVisited) {
                        break
                    }
                }
                return !isCoVisited
            } else {
                return true
            }

        } else {
            return false
        }
    }

    /*
     In this part, "visitCOs" is just the same as it has been described in Trust algorithm
     The SetCO(G,w_{p},a) has been implemented within the following function
     */

    private fun visitCOs(G: ExecutionGraph, exWrite: WriteExEvent, allEvents: MutableList<Event>) {
        //System.out.println("[Trust Message] : Visiting COs started for the write event -> $exWrite")
        for (i in 0..<G.graphEvents.size) {
            if (G.graphEvents[i].type == EventType.WRITE) {
                val findWriteEvent = G.graphEvents[i] as WriteEvent
                if (locEquals(findWriteEvent.loc!!, exWrite.loc!!)) {
                    val newWriteEvent = exWrite.deepCopy() as WriteExEvent
                    val newG = G.deepCopy()
                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].secondWrite.equals(findWriteEvent)) {
                            val newCo = CO(firstWrite = G.COs[j].firstWrite, secondWrite = newWriteEvent)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.COs.add(CO(firstWrite = findWriteEvent, secondWrite = newWriteEvent))

                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].firstWrite.equals(findWriteEvent)) {
                            val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.addEvent(newWriteEvent as Event)
                    visit(newG, deepCopyAllEvents(allEvents))
                }
            } else if (G.graphEvents[i].type == EventType.WRITE_EX) {
                val findWriteExEvent = G.graphEvents[i] as WriteExEvent
                if (findWriteExEvent.operationSuccess && locEquals(findWriteExEvent.loc!!, exWrite.loc!!)) {
                    val newWriteEvent = exWrite.deepCopy() as WriteExEvent
                    val newG = G.deepCopy()
                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].secondWrite.equals(findWriteExEvent)) {
                            val newCo = CO(firstWrite = G.COs[j].firstWrite, secondWrite = newWriteEvent)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.COs.add(CO(firstWrite = findWriteExEvent, secondWrite = newWriteEvent))

                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].firstWrite.equals(findWriteExEvent)) {
                            val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.addEvent(newWriteEvent as Event)
                    visit(newG, deepCopyAllEvents(allEvents))
                } else if (G.graphEvents[i].type == EventType.INITIAL) {
                    val findInitEvent = G.graphEvents[i] as InitializationEvent
                    val newWriteEvent = exWrite.deepCopy() as WriteExEvent
                    val newG = G.deepCopy()

                    newG.COs.add(CO(firstWrite = findInitEvent, secondWrite = newWriteEvent))

                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].firstWrite.equals(findInitEvent)) {
                            if (G.COs[j].secondWrite is WriteEvent) {
                                val secondHandWrite = G.COs[j].secondWrite
                                if (locEquals(newWriteEvent.loc!!, secondHandWrite.loc!!)) {
                                    val newCo = CO(firstWrite = newWriteEvent, secondWrite = secondHandWrite)
                                    newG.COs.add(newCo)
                                }
                            } else if (G.COs[j].secondWrite is WriteExEvent) {
                                val secondHandWrite = G.COs[j].secondWrite as WriteExEvent
                                if (locEquals(newWriteEvent.loc!!, secondHandWrite.loc!!)) {
                                    val newCo = CO(firstWrite = newWriteEvent, secondWrite = secondHandWrite)
                                    newG.COs.add(newCo)
                                }
                            }
                        }
                    }

                    newG.addEvent(newWriteEvent as Event)
                    visit(newG, deepCopyAllEvents(allEvents))
                }
            } else if (G.graphEvents[i].type == EventType.INITIAL) {
                val findInitEvent = G.graphEvents[i] as InitializationEvent
                val newWriteEvent = exWrite.deepCopy() as WriteExEvent
                val newG = G.deepCopy()

                newG.COs.add(CO(firstWrite = findInitEvent, secondWrite = newWriteEvent))

                for (j in 0..<G.COs.size) {
                    if (G.COs[j].firstWrite.equals(findInitEvent)) {
                        if (G.COs[j].secondWrite is WriteEvent) {
                            val secondHandWrite = G.COs[j].secondWrite
                            if (locEquals(newWriteEvent.loc!!, secondHandWrite.loc!!)) {
                                val newCo = CO(firstWrite = newWriteEvent, secondWrite = secondHandWrite)
                                newG.COs.add(newCo)
                            }
                        } else if (G.COs[j].secondWrite is WriteExEvent) {
                            val secondHandWrite = G.COs[j].secondWrite as WriteExEvent
                            if (locEquals(newWriteEvent.loc!!, secondHandWrite.loc!!)) {
                                val newCo = CO(firstWrite = newWriteEvent, secondWrite = secondHandWrite)
                                newG.COs.add(newCo)
                            }
                        }
                    }
                }

                newG.addEvent(newWriteEvent as Event)
                visit(newG, deepCopyAllEvents(allEvents))
            }
        }
    }

    private fun visitCOs(G: ExecutionGraph, writeEvent: WriteEvent, allEvents: MutableList<Event>) {
        //System.out.println("[Trust Message] : Visiting COs started for the write event -> $writeEvent")
        for (i in 0..<G.graphEvents.size) {
            if (G.graphEvents[i].type == EventType.WRITE) {
                val findWriteEvent = G.graphEvents[i] as WriteEvent

                if (locEquals(findWriteEvent.loc!!, writeEvent.loc!!)) {
                    val newWriteEvent = writeEvent.deepCopy() as WriteEvent
                    val newG = G.deepCopy()
                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].secondWrite.equals(findWriteEvent)) {
                            val newCo = CO(firstWrite = G.COs[j].firstWrite, secondWrite = newWriteEvent)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.COs.add(CO(firstWrite = findWriteEvent, secondWrite = newWriteEvent))

                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].firstWrite.equals(findWriteEvent)) {
                            val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.addEvent(newWriteEvent as Event)
                    visit(newG, deepCopyAllEvents(allEvents))
                }
            } else if (G.graphEvents[i].type == EventType.WRITE_EX) {
                val findWriteExEvent = G.graphEvents[i] as WriteExEvent
                if (findWriteExEvent.operationSuccess && locEquals(findWriteExEvent.loc!!, writeEvent.loc!!)) {
                    val newWriteEvent = writeEvent.deepCopy() as WriteEvent
                    val newG = G.deepCopy()
                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].secondWrite.equals(findWriteExEvent)) {
                            val newCo = CO(firstWrite = G.COs[j].firstWrite, secondWrite = newWriteEvent)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.COs.add(CO(firstWrite = findWriteExEvent, secondWrite = newWriteEvent))

                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].firstWrite.equals(findWriteExEvent)) {
                            val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.addEvent(newWriteEvent as Event)
                    visit(newG, deepCopyAllEvents(allEvents))
                }
            } else if (G.graphEvents[i].type == EventType.INITIAL) {
                val findInitEvent = G.graphEvents[i] as InitializationEvent
                val newWriteEvent = writeEvent.deepCopy() as WriteEvent
                val newG = G.deepCopy()

                newG.COs.add(CO(firstWrite = findInitEvent, secondWrite = newWriteEvent))

                for (j in 0..<G.COs.size) {
                    if (G.COs[j].firstWrite.equals(findInitEvent)) {
                        if (G.COs[j].secondWrite is WriteEvent) {
                            val secondHandWrite = G.COs[j].secondWrite
                            if (locEquals(newWriteEvent.loc!!, secondHandWrite.loc!!)) {
                                val newCo = CO(firstWrite = newWriteEvent, secondWrite = secondHandWrite)
                                newG.COs.add(newCo)
                            }
                        } else if (G.COs[j].secondWrite is WriteExEvent) {
                            val secondHandWrite = G.COs[j].secondWrite as WriteExEvent
                            if (locEquals(newWriteEvent.loc!!, secondHandWrite.loc!!)) {
                                val newCo = CO(firstWrite = newWriteEvent, secondWrite = secondHandWrite)
                                newG.COs.add(newCo)
                            }
                        }
                    }
                }

                newG.addEvent(newWriteEvent as Event)
                visit(newG, deepCopyAllEvents(allEvents))
            }
        }
    }

    private fun findNext(allEvents: MutableList<Event>): Event? {
        return if (allEvents.isNotEmpty()) allEvents.removeFirst()
        else null
    }

    private fun deepCopyAllEvents(allEvents: MutableList<Event>): MutableList<Event> {
        val newAllEvents: MutableList<Event> = mutableListOf()
        for (i in 0..<allEvents.size) {
            newAllEvents.add(allEvents[i].deepCopy())
        }
        return newAllEvents
    }

    fun locEquals(loc1: Location, loc2: Location): Boolean {
        if (loc1.isPrimitive() && loc2.isPrimitive()) {
            return loc1.instance == loc2.instance && loc1.field == loc2.field && loc1.type == loc2.type &&
                    loc1.clazz == loc2.clazz
        } else if (!loc1.isPrimitive() && !loc2.isPrimitive()) {
            // TODO() : Right now, we assume that it is not needed to cover the case of non-primitive types for model checking
            return false
        } else {
            return false
        }
    }

    fun monitorEquals(monitor1: Monitor, monitor2: Monitor): Boolean {
        return monitor1.clazz == monitor2.clazz && monitor1.instance == monitor2.instance
    }

    fun printLocationReferences(location: Location) {
        println("Location :")
        location.clazz?.let {
            println("${it.name}@${it.hashCode().toString(16)}")
        }
        location.instance?.let {
            println("${it}@${it.hashCode().toString(16)}")
        }
        location.field?.let {
            println("${it.name}@${it.hashCode().toString(16)}")
        }
    }
}