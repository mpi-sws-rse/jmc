package dpor

import consistencyChecking.memoryConsistency.SequentialConsistency
import executionGraph.ClosureGraph
import executionGraph.OptExecutionGraph
import executionGraph.operations.GraphOp
import executionGraph.operations.GraphOpType
import programStructure.*
import kotlin.system.exitProcess

class OptTrust(path: String, verbose: Boolean) {

    var graphCounter: Int = 0
    var graphsPath: String = path
    var nextOperations: ArrayList<GraphOp> = ArrayList()
    var extendedGraph: OptExecutionGraph? = null
    var topoSort: ArrayList<ThreadEvent>? = null
    var verbose: Boolean = verbose

    fun visit(g: OptExecutionGraph, allEvents: ArrayList<ThreadEvent>) {
        val nextEvent = findNext(allEvents)

        when {
            nextEvent == null -> {
                println("[OPT-Trust Message] : No more events to explore")
                this.graphCounter++
                g.id = this.graphCounter
                println("[Trust Message] : Graph ${g.id} with size of ${g.eventOrder.size} is visited")
                if (verbose) {
                    g.visualizeGraph(this.graphCounter, this.graphsPath)
                }
                extendedGraph = g
            }

            nextEvent.type == EventType.READ -> {
                println("[OPT-Trust Message] : Next event is a read event - $nextEvent")
                var nextRead = nextEvent as ReadEvent
                if (g.existsSameLocationWriteEvent(nextRead.loc!!)) {
                    batching_fR_read_write(g, nextRead)
                    fR_read_last_write(g, nextRead)
                } else {
                    println("[Trust Message] : No same location write event exists in the graph for the read event $nextRead")
                    exitProcess(0)
                }
            }

            nextEvent.type == EventType.READ_EX -> {
                println("[OPT-Trust Message] : Next event is a read ex event - $nextEvent")
                var nextReadEx = nextEvent as ReadExEvent
                if (g.existsSameLocationWriteEvent(nextReadEx.loc!!)) {
                    val newAllEvents = ArrayList<ThreadEvent>()
                    newAllEvents.add(allEvents[0])
                    batching_fR_exRead_write(g, nextReadEx, allEvents)
                    fR_readEx_last_write(g, nextReadEx, newAllEvents)
                } else {
                    println("[Trust Message] : No same location write event exists in the graph for the read ex event $nextReadEx")
                    exitProcess(0)
                }
            }

            nextEvent.type == EventType.WRITE -> {
                println("[OPT-Trust Message] : Next event is a write event - $nextEvent")
                val nextWrite = nextEvent as WriteEvent
                g.addEvent(nextWrite)
                g.addProgramOrder(nextWrite)
                // Forward Revisits
                if (g.writes.containsKey(nextWrite.loc)) {
                    batching_fR_write_write(g, nextWrite, allEvents)
                }
                if (g.existsSameLocationReadEvent(nextWrite.loc!!)) {
//                    val porf = HashSet<ThreadEvent>()
//                    val notPorf = HashSet<ThreadEvent>()
//                    // Backward Revisits
//                    batching_bR_write_first_read(g, nextWrite, porf, notPorf)
//                    if (g.reads[nextWrite.loc]!!.size > 1) {
//                        batching_bR_write_other_reads(g, nextWrite, porf, notPorf)
//                    }
                    batching_bR_write_read(g, nextWrite)
                }
                fR_write_last_write(g, nextWrite)
            }

            nextEvent.type == EventType.WRITE_EX -> {
                println("[Trust Message] : Next event is a write ex event - $nextEvent")
                val nextWriteEx = nextEvent as WriteExEvent
                val last = g.programOrder[nextEvent.tid]!!.last()
                if (last.type != EventType.READ_EX) {
                    println("[Trust Message] : No read ex event exists in the program order for the write ex event $nextWriteEx")
                    exitProcess(0)
                }

                val readEx = last as ReadExEvent
                nextWriteEx.loc = readEx.loc
                if (readEx.intValue != nextWriteEx.conditionValue) {
                    nextWriteEx.operationSuccess = false
                    g.addEvent(nextWriteEx)
                    g.addProgramOrder(nextWriteEx)
                    visit(g, allEvents)
                } else {
                    nextWriteEx.operationSuccess = true
                    val isConsistent = g.areExReadsConsistent(g.rf[readEx]!!)
                    g.addEvent(nextWriteEx)
                    g.addProgramOrder(nextWriteEx)
                    if (isConsistent) {
                        // Forward Revisits
                        if (g.writes.containsKey(nextWriteEx.loc)) {
                            batching_fR_write_write(g, nextWriteEx, allEvents)
                        }
                    } else {
                        println("[Trust Message] : Ex reads are not consistent for the write ex event $nextWriteEx")
                        topoSort = null
                    }

                    if (g.existsSameLocationReadEvent(nextWriteEx.loc!!)) {
//                            val porf = HashSet<ThreadEvent>()
//                            val notPorf = HashSet<ThreadEvent>()
//                            // Backward Revisits
//                            batching_bR_write_first_read(g, nextEvent, porf, notPorf)
//                            if (g.reads[nextEvent.loc]!!.size > 1) {
//                                batching_bR_write_other_reads(g, nextEvent, porf, notPorf)
//                            }
                        batching_bR_write_read(g, nextEvent)
                    }
                    if (isConsistent) {
                        fR_write_last_write(g, nextWriteEx)
                    }
                }
            }

            nextEvent.type == EventType.JOIN -> {
                println("[Trust Message] : Next event is a join event - $nextEvent")
                val joinTid = (nextEvent as JoinEvent).joinTid
                val finishEvent = g.programOrder[joinTid]!!.last()
                if (finishEvent.type != EventType.FINISH) {
                    println("[Trust Message] : No finish event exists in the program order for the join event $nextEvent")
                    exitProcess(0)
                }
                g.addEvent(nextEvent)
                g.addProgramOrder(nextEvent)
                g.addJT(finishEvent, nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.START -> {
                println("[Trust Message] : Next event is a start event - $nextEvent")
                g.addEvent(nextEvent as StartEvent)
                g.addProgramOrder(nextEvent)
                g.addTC(nextEvent)

                val callerTid = nextEvent.callerThread
                val lastEvent = g.programOrder[callerTid]!!.last()
                g.addST(lastEvent, nextEvent)

                visit(g, allEvents)
            }

            nextEvent.type == EventType.FINISH -> {
                println("[Trust Message] : Next event is a finish event - $nextEvent")
                g.addEvent(nextEvent as FinishEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.FAILURE -> {
                println("[Trust Message] : Next event is a failure event - $nextEvent")
                g.addEvent(nextEvent as FailureEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.DEADLOCK -> {
                println("[Trust Message] : Next event is a deadlock event - $nextEvent")
                g.addEvent(nextEvent as DeadlockEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.MAIN_START -> {
                println("[Trust Message] : Next event is a main start event - $nextEvent")
                g.addEvent(nextEvent as MainStartEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.CON_ASSUME -> {
                println("[Trust Message] : Next event is a con assume event - $nextEvent")
                g.addEvent(nextEvent as ConAssumeEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.SYM_ASSUME -> {
                println("[Trust Message] : Next event is a sym assume event - $nextEvent")
                g.addEvent(nextEvent as SymAssumeEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.ASSUME_BLOCKED -> {
                println("[Trust Message] : Next event is a assume blocked event - $nextEvent")
                g.addEvent(nextEvent as AssumeBlockedEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.SYM_EXECUTION -> {
                println("[Trust Message] : Next event is a symbolic execution event - $nextEvent")
                g.addEvent(nextEvent as SymExecutionEvent)
                g.addProgramOrder(nextEvent)
                if (nextEvent.isNegatable) {
                    batching_fR_neg_sym(g, nextEvent)
                }
                visit(g, allEvents)
            }

            else -> {
                println("[Trust Message] : Next event is not supported - $nextEvent")
                exitProcess(0)
            }
        }
    }

    private fun batching_fR_read_write(g: OptExecutionGraph, nextRead: ReadEvent) {
        //println("[DEBUGGING] The size of the writes is: ${g.writes[nextRead.loc]!!.size}")
        if (g.writes[nextRead.loc]!!.size > 1) {
            for (i in 0 until g.writes[nextRead.loc]!!.size - 1) {
                println("[OPT-Trust] batching frw ${nextRead.type}(${nextRead.tid}:${nextRead.serial}) ${g.writes[nextRead.loc]!![i].type}(${g.writes[nextRead.loc]!![i].tid}:${g.writes[nextRead.loc]!![i].serial})")
                val writeEvent = g.writes[nextRead.loc]!![i]
                val op = GraphOp(nextRead, writeEvent, GraphOpType.FR_R_W, g)
                nextOperations.add(op)
            }
        }
    }

    private fun batching_fR_exRead_write(
        g: OptExecutionGraph,
        nextRead: ReadExEvent,
        allEvents: ArrayList<ThreadEvent>
    ) {
        for (i in 0 until g.writes[nextRead.loc]!!.size - 1) {
            println("[OPT-Trust] batching frw ${nextRead.type}(${nextRead.tid}:${nextRead.serial}) ${g.writes[nextRead.loc]!![i].type}(${g.writes[nextRead.loc]!![i].tid}:${g.writes[nextRead.loc]!![i].serial})")
            val writeEvent = g.writes[nextRead.loc]!![i]
            val addList = ArrayList<ThreadEvent>()
            addList.add(allEvents[0])
            val op = GraphOp(nextRead, writeEvent, GraphOpType.FR_RX_W, g, addList)
            nextOperations.add(op)
            //println(allEvents)
        }
    }

    private fun fR_read_last_write(g: OptExecutionGraph, nextRead: ReadEvent) {
        val writeEvent = g.writes[nextRead.loc]!!.last()
        println("[OPT-Trust] frw ${nextRead.type}(${nextRead.tid}:${nextRead.serial}) last ${writeEvent.type}(${writeEvent.tid}:${writeEvent.serial})")
        g.addEvent(nextRead)
        g.addProgramOrder(nextRead)
        g.addRead(nextRead)
        g.addRF(nextRead, writeEvent)
        visit(g, ArrayList())
    }

    private fun fR_readEx_last_write(g: OptExecutionGraph, nextRead: ReadExEvent, allEvents: ArrayList<ThreadEvent>) {
        val writeEvent = g.writes[nextRead.loc]!!.last()
        println("[OPT-Trust] frw ${nextRead.type}(${nextRead.tid}:${nextRead.serial}) last ${writeEvent.type}(${writeEvent.tid}:${writeEvent.serial})")
        g.addEvent(nextRead)
        g.addProgramOrder(nextRead)
        g.addRead(nextRead)
        //val rd = g.findReadEvent(nextRead) as ReadExEvent

        //g.removeRf(nextRead)
        //rd.rf = writeEvent
        nextRead.intValue = writeEvent.value as Int
        g.addRF(nextRead, writeEvent)
        visit(g, allEvents)
    }


    private fun fR_write_last_write(g: OptExecutionGraph, nextWrite: WriteEvent) {
        println("[OPT-Trust] frw ${nextWrite.type}(${nextWrite.tid}:${nextWrite.serial}) last ${nextWrite.type}(${nextWrite.tid}:${nextWrite.serial})")
        g.addWrite(nextWrite)
        visit(g, ArrayList())
    }

    private fun batching_fR_last_write(g: OptExecutionGraph, write: WriteEvent, toBeAdded: ArrayList<ThreadEvent>) {
        println("[OPT-Trust] batching frw last ${write.type}(${write.tid}:${write.serial})")
        val newEvents = ArrayList<ThreadEvent>()
        if (toBeAdded.isNotEmpty()) {
            newEvents.add(toBeAdded[0])
        }
        val op = GraphOp(write, write, GraphOpType.FR_L_W, g, newEvents)
        //println("[debugging] fR Op: $op")
        nextOperations.add(op)
    }

    private fun batching_fR_write_write(
        g: OptExecutionGraph,
        nextWrite: WriteEvent,
        toBeAdded: ArrayList<ThreadEvent>
    ) {
        if (g.writes[nextWrite.loc]!!.size > 0) {
            for (i in 0 until g.writes[nextWrite.loc]!!.size) {
                println("[OPT-Trust] batching frw ${nextWrite.type}(${nextWrite.tid}:${nextWrite.serial}) ${g.writes[nextWrite.loc]!![i].type}(${g.writes[nextWrite.loc]!![i].tid}:${g.writes[nextWrite.loc]!![i].serial})")
                val writeEvent = g.writes[nextWrite.loc]!![i]
                val addList = ArrayList<ThreadEvent>()
                if (toBeAdded.isNotEmpty()) {
                    addList.add(toBeAdded[0])
                }
                val op = GraphOp(nextWrite, writeEvent, GraphOpType.FR_W_W, g, addList)
                //println("[debugging] fR Op: $op")
                nextOperations.add(op)
            }
        }
    }

    private fun batching_fR_write_write(g: OptExecutionGraph, nextWrite: WriteEvent) {
        if (g.writes[nextWrite.loc]!!.size > 0) {
            for (i in 0 until g.writes[nextWrite.loc]!!.size) {
                //println("[debugging] $i-write: ${g.writes[nextWrite.loc]!![i]}")
                val writeEvent = g.writes[nextWrite.loc]!![i]
                val op = GraphOp(nextWrite, writeEvent, GraphOpType.FR_W_W, g)
                nextOperations.add(op)
            }
        }
    }

    private fun batching_bR_write_first_read(
        g: OptExecutionGraph,
        nextWrite: WriteEvent,
        porf: HashSet<ThreadEvent>,
        notPorf: HashSet<ThreadEvent>
    ) {
        val closureGraph = ClosureGraph()
        computePorf(g, closureGraph)
        val readEvent = g.reads[nextWrite.loc]!!.first()
        if (/*(readEvent.tid != nextWrite.tid) &&*/ !closureGraph.pathExists(readEvent, nextWrite)) {
            //println("[debugging] The read event for processing BR_W_R is : $readEvent")
            val deleted = LinkedHashSet<ThreadEvent>()
            deleted.addAll(closureGraph.visited)
            val index = g.eventOrder.indexOf(readEvent)
            for (i in index + 1 until g.eventOrder.size - 1) {
                if (!deleted.contains(g.eventOrder[i]) &&
                    g.eventOrder[i].tid != nextWrite.tid &&
                    !closureGraph.pathExists(
                        g.eventOrder[i],
                        nextWrite
                    )
                ) {
                    deleted.addAll(closureGraph.visited)
                }
            }
            notPorf.addAll(deleted)
            val porfPrefix = computePorfPrefix(g, deleted, readEvent)
            porf.addAll(porfPrefix)
            revisitIfIsMaximal(g, porfPrefix, deleted, nextWrite, readEvent)
        }
    }

    private fun batching_bR_write_other_reads(
        g: OptExecutionGraph,
        nextWrite: WriteEvent,
        porf: HashSet<ThreadEvent>,
        notPorf: HashSet<ThreadEvent>
    ) {
        for (i in 1 until g.reads[nextWrite.loc]!!.size) {
            val readEvent = g.reads[nextWrite.loc]!![i]
            if (notPorf.contains(readEvent)) {
                //println("[debugging] The read event for processing BR_W_R is : $readEvent")
                val deleted = LinkedHashSet<ThreadEvent>()
                val index = g.eventOrder.indexOf(readEvent)
                for (j in index + 1 until g.eventOrder.size - 1) {
                    if (notPorf.contains(g.eventOrder[j])) {
                        deleted.add(g.eventOrder[j])
                    }
                }
                revisitIfIsMaximal(g, porf, deleted, nextWrite, readEvent)
            }
        }
    }

    private fun batching_bR_write_read(g: OptExecutionGraph, nextWrite: WriteEvent) {
        if (g.reads[nextWrite.loc]!!.isNotEmpty()) {
            val closureGraph = ClosureGraph()
            computePorf(g, closureGraph)
            for (readEvent in g.reads[nextWrite.loc]!!) {
                println("[OPT-TRUST] The ${readEvent.type}(${readEvent.tid}:${readEvent.serial}) event for processing BR_W_R is of ${nextWrite.type}(${nextWrite.tid}:${nextWrite.serial})")
                if ((readEvent.tid != nextWrite.tid) && !closureGraph.pathExists(readEvent, nextWrite)) {
                    println("[OPT-TRUST] The read event was not in porf")
                    val deleted = LinkedHashSet<ThreadEvent>()
                    //deleted.addAll(closureGraph.visited)
                    val visited = LinkedHashSet<ThreadEvent>()
                    visited.addAll(closureGraph.visited)
                    val index = g.eventOrder.indexOf(readEvent)
                    for (i in index + 1 until g.eventOrder.size - 1) {
                        if (g.eventOrder[i].tid != nextWrite.tid) {
                            if (visited.contains(g.eventOrder[i])) {
                                deleted.add(g.eventOrder[i])
                            } else if (!closureGraph.pathExists(g.eventOrder[i], nextWrite)) {
                                deleted.add(g.eventOrder[i])
                                visited.addAll(closureGraph.visited)
                            }
                        }
                        deleted.add(readEvent)
//                        if (!deleted.contains(g.eventOrder[i]) &&
//                            g.eventOrder[i].tid != nextWrite.tid &&
//                            !closureGraph.pathExists(
//                                g.eventOrder[i],
//                                nextWrite
//                            )
//                        ) {
//                            deleted.addAll(closureGraph.visited)
//                        }
                    }
                    val porfPrefix = computePorfPrefix(g, deleted, readEvent)
                    revisitIfIsMaximal(g, porfPrefix, deleted, nextWrite, readEvent)
                } else {
                    println("[OPT-TRUST] The read event was in porf!")
                }
            }
        }
    }

    private fun revisitIfIsMaximal(
        g: OptExecutionGraph,
        porfPrefix: HashSet<ThreadEvent>,
        deleted: LinkedHashSet<ThreadEvent>,
        nextWrite: WriteEvent,
        readEvent: ReadEvent
    ) {
        if (isMaximal(g, porfPrefix, deleted, nextWrite)) {
            println("[OPT-TRUST] The deleted events are maximal")

            val toBeAdded = ArrayList<ThreadEvent>()
//            if (readEvent is ReadExEvent) {
//                val index = deleted.indexOf(readEvent)
//                val wrEx = deleted.elementAt(index + 1) as WriteExEvent
//                toBeAdded.add(wrEx)
//            }
            deleted.remove(readEvent)
            val copy_deleted = LinkedHashSet<ThreadEvent>()
            val g_copy = g.deepCopy(deleted, copy_deleted)
            val rd = g_copy.findReadEvent(readEvent)
            if (rd is ReadExEvent) {
                val index = g_copy.programOrder[rd.tid]!!.indexOf(rd)
                val wrEx = g_copy.programOrder[rd.tid]!!.elementAt(index + 1) as WriteExEvent
                toBeAdded.add(wrEx)
            }
            //copy_deleted.remove(rd!!)
            g_copy.restrictGraph(copy_deleted)
            val wr = g_copy.eventOrder.get(g_copy.eventOrder.size - 1) as WriteEvent
            g_copy.removeRf(rd!!)
            //rd.rf = wr
            if (rd is ReadExEvent) {
                rd.intValue = wr.value as Int
            }
            g_copy.addRF(rd, wr)
//            batching_fR_write_write(g_copy, wr, toBeAdded)
//            batching_fR_last_write(g_copy, wr, toBeAdded)
            if (wr is WriteExEvent) {
                val rd_wr =
                    g_copy.programOrder[wr.tid]!!.elementAt(g_copy.programOrder[wr.tid]!!.size - 2) as ReadExEvent
                if (g_copy.areExReadsConsistent(g_copy.rf[rd_wr]!!)) {
                    batching_fR_write_write(g_copy, wr, toBeAdded)
                    batching_fR_last_write(g_copy, wr, toBeAdded)
                } else {
                    println("[OPT-Trust Message] : Ex reads are not consistent for the write ex event $wr")
                }
            } else {
                batching_fR_write_write(g_copy, wr, toBeAdded)
                batching_fR_last_write(g_copy, wr, toBeAdded)
            }
        }
    }

    private fun computePorfPrefix(
        g: OptExecutionGraph,
        set: LinkedHashSet<ThreadEvent>,
        read: ReadEvent
    ): HashSet<ThreadEvent> {
        val porfPrefix: HashSet<ThreadEvent> = HashSet()
        val index = g.eventOrder.indexOf(read)
        for (i in index + 1 until g.eventOrder.size - 1) {
            if (!set.contains(g.eventOrder[i])) {
                porfPrefix.add(g.eventOrder[i])
            }
        }
        return porfPrefix
    }

    private fun computePorf(g: OptExecutionGraph, closureGraph: ClosureGraph) {
        for (e in g.eventOrder) {
            closureGraph.addVertex(e)
        }

        for (value in g.programOrder.values) {
            for (i in 0 until value.size - 1) {
                closureGraph.addEdge(value[i], value[i + 1])
            }
        }

        for (entry in g.rf) {
            closureGraph.addEdge(entry.value, entry.key)
        }

        for (pair in g.st) {
            closureGraph.addEdge(pair.first, pair.second)
        }

        for (i in 0 until g.tc.size - 1) {
            closureGraph.addEdge(g.tc[i], g.tc[i + 1])
        }

        for (pair in g.jt) {
            closureGraph.addEdge(pair.first, pair.second)
        }
    }

    private fun batching_fR_neg_sym(g: OptExecutionGraph, event: SymExecutionEvent) {
        println("[OPT-Trust] batching frw neg sym ${event.type}(${event.tid}:${event.serial})")
        val op = GraphOp(event, event, GraphOpType.FR_NEG_SYM, g)
        nextOperations.add(op)
    }

    fun processFR_R_W(g: OptExecutionGraph, nextRead: ReadEvent, writeEvent: WriteEvent) {
        println("[OPT-Trust] processing frw ${nextRead.type}(${nextRead.tid}:${nextRead.serial}) ${writeEvent.type}(${writeEvent.tid}:${writeEvent.serial})")
//        println("[debugging] The Graph number is: ${g.id}")
//        println("nextRead: $nextRead")
//        println("writeEvent: $writeEvent")
//        println("[debugging] The Graph before restricting is: ")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
        val rd = g.findReadEvent(nextRead)
        val wr = g.findWriteEvent(writeEvent)
        g.restrictStrictAfterEvent(rd!!)
        g.removeRf(rd)
        //rd.rf = wr
        g.addRF(rd, wr!!)

//        println("[debugging] The Graph after restricting is: ")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            visit(g, ArrayList())
        } else {
            println("[Trust Message] : Graph G_${g.id} is not sequentially consistent")
        }
    }

    fun processFR_RX_W(
        g: OptExecutionGraph,
        nextReadEx: ReadExEvent,
        writeEvent: WriteEvent,
        toBeAddedEvents: ArrayList<ThreadEvent>
    ) {
        println("[OPT-Trust] processing frw ${nextReadEx.type}(${nextReadEx.tid}:${nextReadEx.serial}) ${writeEvent.type}(${writeEvent.tid}:${writeEvent.serial})")
//        println("nextReadEx: $nextReadEx")
//        println("writeEvent: $writeEvent")
//        println("[debugging] The Graph before restricting is: ")
//        println("[debugging] The Graph number is: ${g.id}")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
        val rd = g.findReadEvent(nextReadEx) as ReadExEvent
        val wr = g.findWriteEvent(writeEvent) as WriteEvent
        g.restrictStrictAfterEvent(rd)
        g.removeRf(rd)
        //rd.rf = wr
        rd.intValue = wr.value as Int
        g.addRF(rd, wr)
//        println("[debugging] The Graph after restricting is: ")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
//        if (toBeAddedEvents.isNotEmpty()) {
//            g.addEvent(toBeAddedEvents[0])
//            g.addProgramOrder(toBeAddedEvents[0])
//            topoSort = SequentialConsistency.scAcyclicity(g)
//            g.eventOrder.removeLast()
//            g.programOrder[toBeAddedEvents[0].tid]!!.removeLast()
//        } else {
//            topoSort = SequentialConsistency.scAcyclicity(g)
//        }
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            if (toBeAddedEvents.isNotEmpty()) {
                //println("[DEBUGGING] The event for adding wrx after rdx is: ${toBeAddedEvents[0]}")
                addWrxAfterRdx(toBeAddedEvents[0] as WriteExEvent)
            }
            visit(g, toBeAddedEvents)
        } else {
            println("[Trust Message] : Graph G_${g.id} is not sequentially consistent")
        }
    }

    fun processFR_W_W(
        g: OptExecutionGraph,
        nextWrite: WriteEvent,
        writeEvent: WriteEvent,
        toBeAddedEvents: ArrayList<ThreadEvent>
    ) {
        println("[OPT-Trust] processing frw ${nextWrite.type}(${nextWrite.tid}:${nextWrite.serial}) ${writeEvent.type}(${writeEvent.tid}:${writeEvent.serial})")
//        println("nextWrite: $nextWrite")
//        println("writeEvent: $writeEvent")
//        println("[debugging] The Graph before restricting is: ")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
        g.restrictStrictAfterEvent(nextWrite)
        g.removeWrite(nextWrite)
        g.addWriteBefore(nextWrite, writeEvent)
//        println("[debugging] The Graph after restricting is: ")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
//        if (toBeAddedEvents.isNotEmpty()) {
//            g.addEvent(toBeAddedEvents[0])
//            g.addProgramOrder(toBeAddedEvents[0])
//            topoSort = SequentialConsistency.scAcyclicity(g)
//            g.eventOrder.removeLast()
//            g.programOrder[toBeAddedEvents[0].tid]!!.removeLast()
//        } else {
//            topoSort = SequentialConsistency.scAcyclicity(g)
//        }
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            if (toBeAddedEvents.isNotEmpty()) {
                addWrxAfterRdx(toBeAddedEvents[0] as WriteExEvent)
            }
            visit(g, toBeAddedEvents)
        } else {
            println("[Trust Message] : Graph G_${g.id} is not sequentially consistent")
        }
    }

    fun addWrxAfterRdx(wr: WriteExEvent) {
        // Start from the end of the topoSort and find the event which it has the same tid as the wr
        // Then add the wr after that event
        for (i in topoSort!!.size - 1 downTo 0) {
            if (topoSort!![i].tid == wr.tid && topoSort!![i].serial + 1 == wr.serial) {
                //println("[DEBUGGING] The event for adding wrx after rdx is: ${topoSort!![i]}")
                topoSort!!.add(i + 1, wr)
                break
            }
        }
    }

    fun processFR_L_W(g: OptExecutionGraph, writeEvent: WriteEvent, toBeAddedEvents: ArrayList<ThreadEvent>) {
        println("[OPT-Trust] processing frw last ${writeEvent.type}(${writeEvent.tid}:${writeEvent.serial})")
//        println("writeEvent: $writeEvent")
//        println("[debugging] The Graph before restricting is: ")
//        println("[debugging] The Graph number is: ${g.id}")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
        g.restrictStrictAfterEvent(writeEvent)
        g.removeWrite(writeEvent)
        g.addWrite(writeEvent)
//        println("[debugging] The Graph after restricting is: ")
//        println("[debugging] eventOrder:")
//        g.printEventOrder()
//        println("[debugging] po:")
//        g.printPO()
//        println("[debugging] rf:")
//        g.printRf()
//        println("[debugging] co:")
//        g.printCO()
//        println("[debugging] jt:")
//        g.printJT()
//        println("[debugging] st:")
//        g.printST()
//        println("[debugging] tc:")
//        g.printTC()
//        println("[debugging] reads:")
//        g.printReads()
//        if (toBeAddedEvents.isNotEmpty()) {
//            g.addEvent(toBeAddedEvents[0])
//            g.addProgramOrder(toBeAddedEvents[0])
//            topoSort = SequentialConsistency.scAcyclicity(g)
//            g.eventOrder.removeLast()
//            g.programOrder[toBeAddedEvents[0].tid]!!.removeLast()
//        } else {
//            topoSort = SequentialConsistency.scAcyclicity(g)
//        }
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            if (toBeAddedEvents.isNotEmpty()) {
                //println("[DEBUGGING] The event for adding wrx after rdx is: ${toBeAddedEvents[0]}")
                addWrxAfterRdx(toBeAddedEvents[0] as WriteExEvent)
            }
            visit(g, toBeAddedEvents)
        } else {
            println("[Trust Message] : Graph G_${g.id} is not sequentially consistent")
        }
    }

    fun processFR_neg_sym(g: OptExecutionGraph, event: SymExecutionEvent) {
        println("[OPT-Trust] processing frw neg sym ${event.type}(${event.tid}:${event.serial})")
        println("symbolicEvent: $event")
        g.restrictStrictAfterEvent(event)
        event.result = !event.result
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            visit(g, ArrayList())
        } else {
            println("[Trust Message] : Graph G_${g.id} is not sequentially consistent")
        }
    }

    private fun isMaximal(
        g: OptExecutionGraph,
        porfPrefix: HashSet<ThreadEvent>,
        set: LinkedHashSet<ThreadEvent>,
        write: WriteEvent
    ): Boolean {
        var maximal = true
        for (event in set) {
            //println("[debugging] The event for checking isMaximal is: $event")
            if (!isMaximallyAdded(g, porfPrefix, event, write)) {
                //println("[debugging] The event is not maximally added")
                maximal = false
                break
            }
        }
        return maximal
    }

    private fun isMaximallyAdded(
        g: OptExecutionGraph,
        porfPrefix: HashSet<ThreadEvent>,
        event: ThreadEvent,
        write: WriteEvent
    ): Boolean {
        if (event.type == EventType.SYM_EXECUTION) {
            return isSatMaximal(event as SymExecutionEvent)
        }

        if (event.type == EventType.WRITE_EX) {
            val writeEx = event as WriteExEvent
            val rdEx =
                g.programOrder[writeEx.tid]!!.find { it.type == EventType.READ_EX && it.tid == writeEx.tid && it.serial + 1 == writeEx.serial } as ReadExEvent
            writeEx.loc = rdEx.loc
            if (rdEx.intValue != writeEx.conditionValue) {
                return true
            }
        }

        val previous = HashSet<ThreadEvent>()
        computePrevious(g, porfPrefix, event, write, previous)
        if (event is ReadsFrom) {
            for (prev in previous) {
                if (prev is ReadEvent) {
                    val read = prev
//                    if (read.rf!! == event) {
//                        return false
//                    }
                    if (g.rf[read]!!.equals(event)) {
                        return false
                    }
                }
            }
        }

        val eventPrime: ThreadEvent = if (event is ReadEvent) {
//            event.rf!! as WriteEvent
            g.rf[event] as WriteEvent
        } else {
            event
        }

        if (previous.contains(eventPrime)) {
            // println("[debugging] the eventPrime is: $eventPrime is in the previous")
            if (eventPrime is WriteEvent) {
                if (eventPrime == g.writes[eventPrime.loc]!!.last()) {
                    return true
                } else {
                    var isCoVisited = false
                    for (i in g.writes[eventPrime.loc]!!.indexOf(eventPrime) + 1 until g.writes[eventPrime.loc]!!.size) {
                        if (previous.contains(g.writes[eventPrime.loc]!![i])) {
                            isCoVisited = true
                            break
                        }
                    }
                    return !isCoVisited
                }
            } else {
                return true
            }
        } else {
            //println("[debugging] Iam the bug")
            return false
        }
    }

    private fun computePrevious(
        g: OptExecutionGraph,
        porfPrefix: HashSet<ThreadEvent>,
        event: ThreadEvent,
        write: WriteEvent,
        previous: HashSet<ThreadEvent>
    ) {
        val index = g.eventOrder.indexOf(event)
        val subList = g.eventOrder.subList(0, index + 1)
        previous.addAll(subList)
        previous.addAll(porfPrefix)
    }

    private fun isSatMaximal(symbolicEvent: SymExecutionEvent): Boolean {
        return symbolicEvent.result || !symbolicEvent.isNegatable
    }

    private fun findNext(allEvents: ArrayList<ThreadEvent>): Event? {
        return if (allEvents.isNotEmpty()) allEvents.removeFirst()
        else null
    }
}