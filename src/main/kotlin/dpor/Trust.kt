package dpor


import consistencyChecking.SequentialConsistency
import executionGraph.CO
import executionGraph.ExecutionGraph
import programStructure.*

/*
 In This Version of the Trust, we assume that there is no any error in threads execution
 Moreover, we assume that threads will not be blocked in their execution
 TODO() : Extends this algorithm by discharging the above-mentioned assumptions
 */

class Trust(path: String) {
    private var graph: ExecutionGraph = ExecutionGraph()
    var allJMCThread: MutableMap<Int, JMCThread>? = mutableMapOf()
    var allEvents: MutableList<Event> = mutableListOf()
    var graphCounter: Int = 0
    var allGraphs: MutableList<ExecutionGraph> = mutableListOf()
    var graphsPath: String = path


    init {
        val init: Event = InitializationEvent()
        this.allEvents.add(init)
    }

    /*
     * TODO() : This function is not needed in the final version of the Trust algorithm
     */
    private fun makeAllEvents() {
        for (i in this.allJMCThread!!.keys)
            for (e in this.allJMCThread?.get(i)?.instructions!!) {
                this.allEvents.add(e)
                println("[MC Message] : the event : $e")
            }
    }

    fun setThreads(trds: MutableMap<Int, JMCThread>?) {
        this.allJMCThread = trds
        makeAllEvents()
    }

    fun printEvents() {
        for (i in this.allEvents) {
            when (i.type) {
                EventType.READ -> {
                    val read: ReadEvent? = i as ReadEvent?
                    println(read)
                }

                EventType.WRITE -> {
                    val write: WriteEvent = i as WriteEvent
                    println(write)
                }

                EventType.INITIAL -> {
                    val init: InitializationEvent = i as InitializationEvent
                    println(init)
                }

                EventType.START -> {
                    val start: StartEvent = i as StartEvent
                    println(start)
                }

                EventType.JOIN -> {
                    val join: JoinEvent = i as JoinEvent
                    println(join)
                }

                EventType.FINISH -> {
                    val finish: FinishEvent = i as FinishEvent
                    println(finish)
                }

                EventType.ENTER_MONITOR -> {
                    val enterMonitor: EnterMonitorEvent = i as EnterMonitorEvent
                    println(enterMonitor)
                }

                EventType.EXIT_MONITOR -> {
                    val exitMonitor: ExitMonitorEvent = i as ExitMonitorEvent
                    println(exitMonitor)
                }

                EventType.DEADLOCK -> {
                    val deadlock: DeadlockEvent = i as DeadlockEvent
                    println(deadlock)
                }

                EventType.MONITOR_REQUEST -> {
                    val monitorRequestEvent: MonitorRequestEvent = i as MonitorRequestEvent
                    println(monitorRequestEvent)
                }

                EventType.FAILURE -> {
                    val failureEvent: FailureEvent = i as FailureEvent
                    println(failureEvent)
                }

                EventType.OTHER -> TODO()
            }
        }

    }

    // This is the 'procedure VERIFY(P) in Algorithm 1'
    fun verify() {
        graph.addRoot(this.findNext(allEvents)!!)
        visit(graph, allEvents)
    }

    /*
     This is the 'procedure VISIT(P,G) in Algorithm 1
     Where 'P' is "allEvents"
     */
    fun visit(G: ExecutionGraph, allEvents: MutableList<Event>) {
        /*
         To use consistency checking of Kater's Algorithm use the following
         */
        val graphConsistency = SequentialConsistency.scAcyclicity(G)

        /*
         To use consistency checking of Trust's Algorithm use the following
         */
        // val graphConsistency = SequentialConsistency.porfAcyclicity(G)

        if (graphConsistency) {
            println("[Model Checker Message] : The graph is consistent")
            val nextEvent = findNext(allEvents)
            when {
                nextEvent == null -> {
                    this.graphCounter++
                    G.id = this.graphCounter
                    println("[Model Checker Message] : Visited full execution graph G_$graphCounter")
                    G.visualizeGraph(this.graphCounter, this.graphsPath)
                    allGraphs.add(G)
                    //G.printEvents()
                    //G.printPorf()
                    G.printSc()
                    /*
                     For printing the resulted graph in text-based version,
                     use the following

                     G.printEvents()
                     G.printGraph()
                     G.printPorf()
                     */
                }

                nextEvent.type == EventType.READ -> {
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
                                visit(G2, newAllEvents)
                            }
                        } else if (G.graphEvents[i].type == EventType.INITIAL) {
                            var G3 = G1.deepCopy()
                            val newNextEvent = nextReadEvent.deepCopy()
                            val newNextReadEvent = newNextEvent as ReadEvent
                            newNextReadEvent.rf = (G.graphEvents[i].deepCopy()) as InitializationEvent
                            G3.addEvent(newNextReadEvent as Event)
                            val newAllEvents = deepCopyAllEvents(allEvents)
                            visit(G3, newAllEvents)
                        }
                    }
                }

                nextEvent.type == EventType.WRITE -> {
                    val G3 = G.deepCopy()
                    G.addEvent(nextEvent)
                    val newAllEvents = deepCopyAllEvents(allEvents)
                    visitCOs(G3.deepCopy(), nextEvent.deepCopy() as WriteEvent, newAllEvents)
                    G3.addEvent(nextEvent.deepCopy())
                    G3.computePorf()
                    for (i in 0..<G3.graphEvents.size) {
                        println("Inside Backward Revisit : " + G3.graphEvents[i])
                        if (G3.graphEvents[i].type == EventType.READ) {
                            val findReadEvent = G3.graphEvents[i] as ReadEvent
                            val nextWriteEvent = nextEvent as WriteEvent
                            println("Inside Backward Revisit : Type is Read")
                            println(
                                "Inside Backward Revisit : The location is equal? : " + locEquals(
                                    findReadEvent.loc!!,
                                    nextWriteEvent.loc!!
                                )
                            )
                            println(
                                "Inside Backward Revisit : The pair is in PORF? : " + G3.porf.contains(
                                    Pair(
                                        findReadEvent,
                                        nextEvent
                                    )
                                )
                            )
                            G3.printPorf()
                            if (locEquals(findReadEvent.loc!!, nextWriteEvent.loc!!) && !G3.porf.contains(
                                    Pair(
                                        findReadEvent,
                                        nextEvent
                                    )
                                )
                            ) {
                                println("Inside Backward Revisit : The location is equal and the pair is not in PORF")
                                println("Inside Backward Revisit : The pair is : " + Pair(findReadEvent, nextEvent))
                                G3.computeDeleted(findReadEvent, nextEvent)
                                /*
                                 For debugging

                                  G3.printPorf()
                                  G3.printDeleted()
                                  G3.printEventsOrder()
                                 */
                                var allIsMaximal = true
                                G3.deleted.add(findReadEvent)
                                G3.printDeletedEvents()
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
                                    println("The deleted set is maximally added")
                                    G3.deleted.remove(findReadEvent)
                                    var G4 = G3.deepCopy()
                                    println("The graph before restriction is :")
                                    G4.printGraph()
                                    G4 = G4.restrictingGraph()
                                    println("The graph after restriction is :")
                                    G4.printGraph()
                                    val read: ReadEvent
                                    if (G4.graphEvents.contains(findReadEvent)) {
                                        read = G4.graphEvents.find { it.equals(findReadEvent) } as ReadEvent
                                        read.rf = nextEvent.deepCopy() as WriteEvent
                                        println("The rf has been set to the $read")
                                    }
                                    //val newnewAllEvents = (deepCopyAllEvents(G3.deleted) + newAllEvents) as MutableList<Event>
                                    println("The new All event is : ")
                                    println(newAllEvents)
                                    visitCOs(G4.deepCopy(), nextEvent.deepCopy() as WriteEvent, newAllEvents)
                                }
                            }
                        }
                    }
                }

                nextEvent.type == EventType.JOIN -> {

                    // The following is for debugging purposes only
                    println("[Model Checker Message] : The next event is a JOIN event")
                    println("[Model Checker Message] : The JOIN event is : $nextEvent")

                    val threadId = (nextEvent as JoinEvent).joinTid
                    val finishEvent = findFinishEvent(G, threadId)
                    if (finishEvent != null) {
                        println("[Model Checker Message] : The finish event is found")
                        println("[Model Checker Message] : The finish event is : $finishEvent")
                        G.addEvent(nextEvent)
                        G.addJT(finishEvent, nextEvent)
                        visit(G, allEvents)
                    }
                }

                nextEvent.type == EventType.START -> {

                    // The following is for debugging purposes only
                    println("[Model Checker Message] : The next event is a START event")
                    println("[Model Checker Message] : The START event is : $nextEvent")

                    val threadId = (nextEvent as StartEvent).callerThread
                    val threadEvent = findLastEvent(G, threadId)
                    if (threadEvent != null) {
                        println("[Model Checker Message] : The thread event is found")
                        println("[Model Checker Message] : The thread event is : $threadEvent")
                        G.addEvent(nextEvent)
                        G.addST(threadEvent, nextEvent)
                        visit(G, allEvents)
                    }
                }

                nextEvent.type == EventType.FINISH -> {
                    // The following is for debugging purposes only
                    println("[Model Checker Message] : The next event is a FINISH event")
                    println("[Model Checker Message] : The FINISH event is : $nextEvent")

                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.FAILURE -> {
                    // The following is for debugging purposes only
                    println("[Model Checker Message] : The next event is a FAILURE event")
                    println("[Model Checker Message] : The FAILURE event is : $nextEvent")

                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                nextEvent.type == EventType.DEADLOCK -> {
                    // The following is for debugging purposes only
                    println("[Model Checker Message] : The next event is a DEADLOCK event")
                    println("[Model Checker Message] : The DEADLOCK event is : $nextEvent")

                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }

                else -> { // TODO() : For possible future extensions
                    G.addEvent(nextEvent)
                    visit(G, allEvents)
                }
            }

        } else {
            println("[Model Checker Message] : The graph is not consistent")
        }
    }

    private fun findFinishEvent(graph: ExecutionGraph, threadId: Int): FinishEvent? {
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

    private fun findLastEvent(graph: ExecutionGraph, threadId: Int): Event? {
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

    private fun isMaximallyAdded(graph: ExecutionGraph, firstEvent: Event, secondEvent: Event): Boolean {
        graph.computePrevious(firstEvent, secondEvent)
        if (firstEvent is ReadsFrom) {
            var isReadVisited = false
            for (i in 0..<graph.previous.size) {
                if (graph.previous[i].type == EventType.READ) {
                    val read = graph.previous[i] as ReadEvent
                    if (read.rf!!.equals(firstEvent)) {
                        isReadVisited = true
                    }
                }
                if (isReadVisited) break
            }
            if (isReadVisited) return false
        }

        // The "eventPrime" is the e' in the ISMAXIMALLYADDED procedure of the Trust algorithm
        val eventPrime: Event
        eventPrime = if (firstEvent.type == EventType.READ) {
            ((firstEvent as ReadEvent).rf as Event).deepCopy()
        } else {
            firstEvent.deepCopy()
        }

        if (graph.previous.contains(eventPrime)) {
            if (eventPrime.type == EventType.WRITE) {
                var isCoVisited = false
                for (i in 0..<graph.previous.size) {
                    if (graph.previous[i].type == EventType.WRITE) {
                        if (graph.COs.contains(CO(eventPrime as WriteEvent, graph.previous[i] as WriteEvent))) {
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

    private fun visitCOs(G: ExecutionGraph, writeEvent: WriteEvent, allEvents: MutableList<Event>) {
        System.out.println("[Trust Message] : Visiting COs")
        System.out.println("[Trust Message] : The graph events are : ${G.graphEvents}")
        for (i in 0..<G.graphEvents.size) {
            //println(G.graphEvents[i].type)
            if (G.graphEvents[i].type == EventType.WRITE) {
                val findWriteEvent = G.graphEvents[i] as WriteEvent
                printLocationReferences(findWriteEvent.loc!!)
                println("is value of the location " + findWriteEvent.loc!!.value + " primitive? : ${findWriteEvent.loc!!.isPrimitive()}")
                printLocationReferences(writeEvent.loc!!)
                println("is value of the location " + findWriteEvent.loc!!.value + " primitive? : ${writeEvent.loc!!.isPrimitive()}")

                if (locEquals(findWriteEvent.loc!!, writeEvent.loc!!)) {
                    val newWriteEvent = writeEvent.deepCopy() as WriteEvent
                    val newG = G.deepCopy()
                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].secondWrite.equals(findWriteEvent)) {
                            val newCo = CO(firstWrite = G.COs[j].firstWrite, secondWrite = newWriteEvent)
                            newG.COs.add(newCo)
                            println(
                                "[Trust Message] : new CO added : ${
                                    CO(
                                        firstWrite = G.COs[j].firstWrite,
                                        secondWrite = newWriteEvent
                                    )
                                }"
                            )
                            println("[Trust Message] : COs are : ${newG.COs}")
                        }
                    }

                    newG.COs.add(CO(firstWrite = findWriteEvent, secondWrite = newWriteEvent))
                    println(
                        "[Trust Message] : new CO added : ${
                            CO(
                                firstWrite = findWriteEvent,
                                secondWrite = newWriteEvent
                            )
                        }"
                    )
                    println("[Trust Message] : COs are : ${newG.COs}")

                    for (j in 0..<G.COs.size) {
                        if (G.COs[j].firstWrite.equals(findWriteEvent)) {
                            val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                            newG.COs.add(newCo)
                            println(
                                "[Trust Message] : new CO added : ${
                                    CO(
                                        firstWrite = newWriteEvent,
                                        secondWrite = G.COs[j].secondWrite
                                    )
                                }"
                            )
                            println("[Trust Message] : COs are : ${newG.COs}")
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
                println(
                    "[Trust Message] : new CO added : ${
                        CO(
                            firstWrite = findInitEvent,
                            secondWrite = newWriteEvent
                        )
                    }"
                )
                println("[Trust Message] : COs are : ${newG.COs}")
                for (j in 0..<G.COs.size) {
                    if (G.COs[j].firstWrite.equals(findInitEvent)) {
                        val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                        newG.COs.add(newCo)
                        println(
                            "[Trust Message] : new CO added : ${
                                CO(
                                    firstWrite = newWriteEvent,
                                    secondWrite = G.COs[j].secondWrite
                                )
                            }"
                        )
                        println("[Trust Message] : COs are : ${newG.COs}")
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
            return loc1.instance == loc2.instance && loc1.field == loc2.field && loc1.type == loc2.type
        } else if (!loc1.isPrimitive() && !loc2.isPrimitive()) {
            // TODO() : Right now, we assume that it is not needed to cover the case of non-primitive types for model checking
            return false
        } else {
            return false
        }
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
