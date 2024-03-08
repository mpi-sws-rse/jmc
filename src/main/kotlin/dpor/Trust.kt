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

class Trust {
    private var graph : ExecutionGraph = ExecutionGraph()
    var allJMCThread: MutableMap<Int, JMCThread>? = mutableMapOf()
    private var allEvents : MutableList<Event> = mutableListOf()
    private var graphCounter : Int = 0



    init {
        val init : Event = InitializationEvent()
        this.allEvents.add(init)
    }

    private fun makeAllEvents(){
        for (i in this.allJMCThread!!.keys)
           for (e in this.allJMCThread?.get(i)?.instructions!!) {
               this.allEvents.add(e)
               println("[MC Message] : the event : $e")
           }
    }
    fun setThreads(trds: MutableMap<Int, JMCThread>?){
        this.allJMCThread = trds
        makeAllEvents()
    }

    fun printEvents(){
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
                    val init : InitializationEvent = i as InitializationEvent
                    println(init)
                }

                EventType.OTHER -> TODO()
            }
        }

    }

    // This is the 'procedure VERIFY(P) in Algorithm 1'
    fun verify(){
        graph.addRoot(this.findNext(allEvents)!!)
        visit(graph,allEvents)
    }

    /*
     This is the 'procedure VISIT(P,G) in Algorithm 1
     Where 'P' is "allEvents"
     */
    private fun visit(G : ExecutionGraph, allEvents: MutableList<Event>){
        /*
         To use consistency checking of Kater's Algorithm use the following
         */
        val graphConsistency = SequentialConsistency.scAcyclicity(G)

        /*
         To use consistency checking of Trust's Algorithm use the following
         */
        // val graphConsistency = SequentialConsistency.porfAcyclicity(G)

        if(graphConsistency){
            val nextEvent = findNext(allEvents)
            when {
                nextEvent == null -> {
                    this.graphCounter++
                    println("Visited full execution graph G_$graphCounter")
                    G.visualizeGraph(this.graphCounter)
                    /*
                     For printing the resulted graph in text-based version,
                     use the following

                     G.printEvents()
                     G.printGraph()
                     G.printPorf()
                     */
                }
                nextEvent.type == EventType.READ -> {
                    val nextReadEvent = (nextEvent as ReadEvent)
                    for(i in 0 ..< G.graphEvents.size){
                        if (G.graphEvents[i].type == EventType.WRITE){
                            val G1 = G.deepCopy()
                            val newNextEvent = nextReadEvent.deepCopy()
                            val newNextReadEvent = newNextEvent as ReadEvent
                            newNextReadEvent.rf = (G.graphEvents[i].deepCopy()) as WriteEvent
                            G1.addEvent(newNextReadEvent as Event)
                            val newAllEvents = deepCopyAllEvents(allEvents)
                            visit(G1,newAllEvents)
                        } else if (G.graphEvents[i].type == EventType.INITIAL){
                            val G2 = G.deepCopy()
                            val newNextEvent = nextReadEvent.deepCopy()
                            val newNextReadEvent = newNextEvent as ReadEvent
                            newNextReadEvent.rf = (G.graphEvents[i].deepCopy()) as InitializationEvent
                            G2.addEvent(newNextReadEvent as Event)
                            val newAllEvents = deepCopyAllEvents(allEvents)
                            visit(G2,newAllEvents)
                        }
                    }

                }
                nextEvent.type == EventType.WRITE -> {
                    val G3 = G.deepCopy()
                    val newAllEvents = deepCopyAllEvents(allEvents)
                    visitCOs(G3.deepCopy(),nextEvent.deepCopy() as WriteEvent,newAllEvents)
                    G3.addEvent(nextEvent.deepCopy())
                    G3.computePorf()
                    for(i in 0 ..< G3.graphEvents.size){
                        if(G3.graphEvents[i].type == EventType.READ){
                            val findReadEvent = G3.graphEvents[i] as ReadEvent
                            val nextWriteEvent = nextEvent as WriteEvent
                            if(findReadEvent.loc!!.equals(nextWriteEvent.loc) && !G3.porf.contains(Pair(findReadEvent,nextEvent))){
                                G3.computeDeleted(findReadEvent,nextEvent)
                                /*
                                 For debugging

                                  G3.printPorf()
                                  G3.printDeleted()
                                  G3.printEventsOrder()
                                 */
                                var allIsMaximal = true
                                G3.deleted.add(findReadEvent)
                                for (j in 0 ..< G3.deleted.size){
                                    if (!isMaximallyAdded(G3.deepCopy(),G3.deleted[j].deepCopy(),nextEvent.deepCopy())){
                                        allIsMaximal =false
                                        break
                                    }
                                }
                                if (allIsMaximal){
                                    G3.deleted.remove(findReadEvent)
                                    var G4 = G3.deepCopy()
                                    G4 = G4.restrictingGraph()
                                    val read : ReadEvent
                                    if (G4.graphEvents.contains(findReadEvent)){
                                        read = G4.graphEvents.find { it.equals(findReadEvent) } as ReadEvent
                                        read.rf = nextEvent
                                    }
                                    val newnewAllEvents = (deepCopyAllEvents(G3.deleted) + (newAllEvents ?: emptyList<Event>())) as MutableList<Event>
                                    visitCOs(G4.deepCopy(),nextEvent.deepCopy() as WriteEvent, newnewAllEvents)
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    private fun isMaximallyAdded(graph: ExecutionGraph,firstEvent: Event,secondEvent: Event) : Boolean{
        graph.computePrevious(firstEvent,secondEvent)
        if (firstEvent is ReadsFrom){
            var isReadVisited = false
            for (i in 0 ..< graph.previous.size){
                if (graph.previous[i].type == EventType.READ){
                    val read = graph.previous[i] as ReadEvent
                    if (read.rf!!.equals(firstEvent)){
                        isReadVisited = true
                    }
                }
                if (isReadVisited) break
            }
            if (isReadVisited) return false
        }

        // The "eventPrime" is the e' in the ISMAXIMALLYADDED procedure of the Trust algorithm
        val eventPrime : Event
        eventPrime = if (firstEvent.type == EventType.READ){
            ((firstEvent as ReadEvent).rf as Event).deepCopy()
        } else {
            firstEvent.deepCopy()
        }

        if (graph.previous.contains(eventPrime)){
            if (eventPrime.type == EventType.WRITE){
                var isCoVisited = false
                for (i in 0 ..< graph.previous.size){
                    if (graph.previous[i].type == EventType.WRITE){
                        if (graph.COs.contains(CO(eventPrime as WriteEvent,graph.previous[i] as WriteEvent))){
                            isCoVisited = true
                        }
                    }
                    if (isCoVisited){
                        break
                    }
                }
                return !isCoVisited
            }else{
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

    private fun visitCOs(G : ExecutionGraph, writeEvent: WriteEvent, allEvents: MutableList<Event>){
        for(i in 0 ..< G.graphEvents.size) {
            if (G.graphEvents[i].type == EventType.WRITE) {
                val findWriteEvent = G.graphEvents[i] as WriteEvent
                if (findWriteEvent.loc!!.equals(writeEvent.loc)){
                    val newWriteEvent = writeEvent.deepCopy() as WriteEvent
                    val newG = G.deepCopy()
                    for (j in 0 ..< G.COs.size){
                        if (G.COs[j].secondWrite.equals(findWriteEvent)){
                            val newCo = CO(firstWrite = G.COs[j].firstWrite , secondWrite = newWriteEvent)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.COs.add(CO(firstWrite = findWriteEvent, secondWrite = newWriteEvent))

                    for (j in 0 ..< G.COs.size){
                        if (G.COs[j].firstWrite.equals(findWriteEvent)){
                            val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                            newG.COs.add(newCo)
                        }
                    }

                    newG.addEvent(newWriteEvent as Event)
                    visit(newG,deepCopyAllEvents(allEvents))
                }
            } else if (G.graphEvents[i].type == EventType.INITIAL){
                val findInitEvent = G.graphEvents[i] as InitializationEvent
                val newWriteEvent = writeEvent.deepCopy() as WriteEvent
                val newG = G.deepCopy()

                newG.COs.add(CO(firstWrite = findInitEvent, secondWrite = newWriteEvent))

                for (j in 0 ..< G.COs.size){
                    if (G.COs[j].firstWrite.equals(findInitEvent)){
                        val newCo = CO(firstWrite = newWriteEvent, secondWrite = G.COs[j].secondWrite)
                        newG.COs.add(newCo)
                    }
                }

                newG.addEvent(newWriteEvent as Event)
                visit(newG,deepCopyAllEvents(allEvents))
            }
        }
    }

    private fun findNext(allEvents: MutableList<Event>) : Event? {
            return if (allEvents.isNotEmpty()) allEvents.removeFirst()
            else  null
    }

    private fun deepCopyAllEvents(allEvents: MutableList<Event>) : MutableList<Event>{
        val newAllEvents : MutableList<Event> = mutableListOf()
        for (i in 0 ..< allEvents.size){
            newAllEvents.add(allEvents[i].deepCopy())
        }
        return newAllEvents
    }


}

