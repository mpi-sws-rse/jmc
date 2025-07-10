package dpor

import consistencyChecking.memoryConsistency.SequentialConsistency
import executionGraph.ClosureGraph
import executionGraph.OptExecutionGraph
import executionGraph.operations.GraphOp
import executionGraph.operations.GraphOpType
import programStructure.*
import kotlin.system.exitProcess

class NewTrust(path: String, verbose: Boolean) {

    var graphCounter: Int = 0
    var graphsPath: String = path
    var nextOperations: ArrayList<GraphOp> = ArrayList()
    var extendedGraph: OptExecutionGraph? = null
    var topoSort: ArrayList<ThreadEvent>? = null
    var verbose: Boolean = verbose
    var proverId: Int = 0

    fun visit(g: OptExecutionGraph, allEvents: ArrayList<ThreadEvent>) {
        val nextEvent = findNext(allEvents)

        when {
            nextEvent == null -> {
                this.graphCounter++
                g.id = this.graphCounter
                if (verbose) {
                    g.visualizeGraph(this.graphCounter, this.graphsPath)
                }
                extendedGraph = g
            }

            nextEvent.type == EventType.READ -> {
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
                val nextWrite = nextEvent as WriteEvent
                g.addEvent(nextWrite)
                g.addProgramOrder(nextWrite)
                // Forward Revisits
                if (g.writes.containsKey(nextWrite.loc)) {
                    batching_fR_write_write(g, nextWrite, allEvents)
                }
                if (g.existsSameLocationReadEvent(nextWrite.loc!!)) {
                    batching_bR_write_read(g, nextWrite)
                }
                fR_write_last_write(g, nextWrite)
            }

            nextEvent.type == EventType.WRITE_EX -> {
                val nextWriteEx = nextEvent as WriteExEvent
                val last = g.programOrder[nextEvent.tid]!!.last()
                if (last.type != EventType.READ_EX) {
                    println("[Trust Message] : No read ex event exists in the program order for the write ex event $nextWriteEx")
                    exitProcess(0)
                }

                val readEx = last as ReadExEvent
                nextWriteEx.loc = readEx.loc
                if (readEx.internalValue != nextWriteEx.conditionValue) {
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
                        topoSort = null
                    }

                    if (g.existsSameLocationReadEvent(nextWriteEx.loc!!)) {
                        batching_bR_write_read(g, nextEvent)
                    }
                    if (isConsistent) {
                        fR_write_last_write(g, nextWriteEx)
                    }
                }
            }

            nextEvent.type == EventType.JOIN -> {
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
                g.addEvent(nextEvent as StartEvent)
                g.addProgramOrder(nextEvent)
                g.addTC(nextEvent)

                val callerTid = nextEvent.callerThread
                val lastEvent = g.programOrder[callerTid]!!.last()
                g.addST(lastEvent, nextEvent)

                visit(g, allEvents)
            }

            nextEvent.type == EventType.FINISH -> {
                g.addEvent(nextEvent as FinishEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.FAILURE -> {
                g.addEvent(nextEvent as FailureEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.DEADLOCK -> {
                g.addEvent(nextEvent as DeadlockEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.MAIN_START -> {
                g.addEvent(nextEvent as MainStartEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.CON_ASSUME -> {
                g.addEvent(nextEvent as ConAssumeEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.SYM_ASSUME -> {
                g.addEvent(nextEvent as SymAssumeEvent)
                g.addProgramOrder(nextEvent)
                val symAssumeEvent = nextEvent as SymAssumeEvent
                if (symAssumeEvent.result) {
                    g.symEvents.add(symAssumeEvent)
                }
                visit(g, allEvents)
            }

            nextEvent.type == EventType.ASSERT -> {
                g.addEvent(nextEvent as AssertEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.ASSUME_BLOCKED -> {
                g.addEvent(nextEvent as AssumeBlockedEvent)
                g.addProgramOrder(nextEvent)
                visit(g, allEvents)
            }

            nextEvent.type == EventType.SYM_EXECUTION -> {
                g.addEvent(nextEvent as SymExecutionEvent)
                g.addProgramOrder(nextEvent)
                if (nextEvent.isNegatable) {
                    batching_fR_neg_sym(g, nextEvent)
                }
                g.symEvents.add(nextEvent)
                visit(g, allEvents)
            }

            else -> {
                exitProcess(0)
            }
        }
    }

    private fun batching_fR_read_write(g: OptExecutionGraph, nextRead: ReadEvent) {
        if (g.writes[nextRead.loc]!!.size > 1) {
            for (i in 0 until g.writes[nextRead.loc]!!.size - 1) {
                val writeEvent = g.writes[nextRead.loc]!![i]
                val op = GraphOp(nextRead, writeEvent, GraphOpType.FR_R_W, g, proverId)
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
            val writeEvent = g.writes[nextRead.loc]!![i]
            val addList = ArrayList<ThreadEvent>()
            addList.add(allEvents[0])
            val op = GraphOp(nextRead, writeEvent, GraphOpType.FR_RX_W, g, proverId, addList)
            nextOperations.add(op)
        }
    }

    private fun fR_read_last_write(g: OptExecutionGraph, nextRead: ReadEvent) {
        val writeEvent = g.writes[nextRead.loc]!!.last()
        g.addEvent(nextRead)
        g.addProgramOrder(nextRead)
        g.addRead(nextRead)
        g.addRF(nextRead, writeEvent)
        visit(g, ArrayList())
    }

    private fun fR_readEx_last_write(g: OptExecutionGraph, nextRead: ReadExEvent, allEvents: ArrayList<ThreadEvent>) {
        val writeEvent = g.writes[nextRead.loc]!!.last()
        g.addEvent(nextRead)
        g.addProgramOrder(nextRead)
        g.addRead(nextRead)
        nextRead.internalValue = writeEvent.value as Int
        g.addRF(nextRead, writeEvent)
        visit(g, allEvents)
    }


    private fun fR_write_last_write(g: OptExecutionGraph, nextWrite: WriteEvent) {
        g.addWrite(nextWrite)
        visit(g, ArrayList())
    }

    private fun batching_fR_last_write(g: OptExecutionGraph, write: WriteEvent, toBeAdded: ArrayList<ThreadEvent>) {
        val newEvents = ArrayList<ThreadEvent>()
        if (toBeAdded.isNotEmpty()) {
            newEvents.add(toBeAdded[0])
        }
        val op = GraphOp(write, write, GraphOpType.FR_L_W, g, proverId, newEvents)
        nextOperations.add(op)
    }

    private fun batching_fR_write_write(
        g: OptExecutionGraph,
        nextWrite: WriteEvent,
        toBeAdded: ArrayList<ThreadEvent>
    ) {
        if (g.writes[nextWrite.loc]!!.size > 0) {
            for (i in 0 until g.writes[nextWrite.loc]!!.size) {
                val writeEvent = g.writes[nextWrite.loc]!![i]
                val addList = ArrayList<ThreadEvent>()
                if (toBeAdded.isNotEmpty()) {
                    addList.add(toBeAdded[0])
                }
                val op = GraphOp(nextWrite, writeEvent, GraphOpType.FR_W_W, g, proverId, addList)
                nextOperations.add(op)
            }
        }
    }

    private fun batching_bR_write_read(g: OptExecutionGraph, nextWrite: WriteEvent) {
        if (g.reads[nextWrite.loc]!!.isNotEmpty()) {
            val op = GraphOp(nextWrite, nextWrite, GraphOpType.BR_W_R, g, proverId)
            nextOperations.add(op)
        }
    }

    private fun revisitIfIsMaximal(
        g: OptExecutionGraph,
        porfPrefix: HashSet<ThreadEvent>,
        deleted: LinkedHashSet<ThreadEvent>,
        nextWrite: WriteEvent,
        readEvent: ReadEvent,
        state: RevisitState?
    ) {
        if (isMaximal(g, porfPrefix, deleted, nextWrite, state)) {
            val toBeAdded = ArrayList<ThreadEvent>()
            deleted.remove(readEvent)
            val copy_deleted = LinkedHashSet<ThreadEvent>()
            val g_copy = g.deepCopy(deleted, copy_deleted)
            val rd = g_copy.findReadEvent(readEvent)
            if (rd is ReadExEvent) {
                val index = g_copy.programOrder[rd.tid]!!.indexOf(rd)
                val wrEx = g_copy.programOrder[rd.tid]!!.elementAt(index + 1) as WriteExEvent
                toBeAdded.add(wrEx)
            }
            g_copy.restrictGraph(copy_deleted)
            val wr = g_copy.eventOrder.get(g_copy.eventOrder.size - 1) as WriteEvent
            g_copy.removeRf(rd!!)
            if (rd is ReadExEvent) {
                rd.internalValue = wr.value as Int
            }
            g_copy.addRF(rd, wr)
            if (wr is WriteExEvent) {
                val rd_wr =
                    g_copy.programOrder[wr.tid]!!.elementAt(g_copy.programOrder[wr.tid]!!.size - 2) as ReadExEvent
                if (g_copy.areExReadsConsistent(g_copy.rf[rd_wr]!!)) {
                    if (proverId != 0) {
                        val graphOp1 = GraphOp(null, null, GraphOpType.REMOVE_PROVER, null, 0)
                        nextOperations.add(graphOp1)
                    }

                    batching_fR_write_write(g_copy, wr, toBeAdded)
                    batching_fR_last_write(g_copy, wr, toBeAdded)

                    if (proverId != 0) {
                        val graphOp2 = GraphOp(null, null, GraphOpType.CREATE_PROVER, null, 0)
                        nextOperations.add(graphOp2)
                    }

                } else {
                    if (state != null) {
                        state.deleted?.removeAt(state.deleted!!.size - 1)
                    }

                }
            } else {
                if (proverId != 0) {
                    val graphOp1 = GraphOp(null, null, GraphOpType.REMOVE_PROVER, null, 0)
                    nextOperations.add(graphOp1)
                }

                batching_fR_write_write(g_copy, wr, toBeAdded)
                batching_fR_last_write(g_copy, wr, toBeAdded)
                if (proverId != 0) {
                    val graphOp2 = GraphOp(null, null, GraphOpType.CREATE_PROVER, null, 0)
                    nextOperations.add(graphOp2)
                }
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
        val op = GraphOp(event, event, GraphOpType.FR_NEG_SYM, g, proverId)
        nextOperations.add(op)
    }

    fun processFR_R_W(g: OptExecutionGraph, nextRead: ReadEvent, writeEvent: WriteEvent, state: RevisitState?) {
        val rd = g.findReadEvent(nextRead)
        val wr = g.findWriteEvent(writeEvent)
        restrictStrictAfterEvent(g, rd!!, state)
        g.removeRf(rd)
        g.addRF(rd, wr!!)
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            visit(g, ArrayList())
        }
    }

    fun processFR_RX_W(
        g: OptExecutionGraph,
        nextReadEx: ReadExEvent,
        writeEvent: WriteEvent,
        toBeAddedEvents: ArrayList<ThreadEvent>,
        state: RevisitState?
    ) {
        val rd = g.findReadEvent(nextReadEx) as ReadExEvent
        val wr = g.findWriteEvent(writeEvent) as WriteEvent
        restrictStrictAfterEvent(g, rd, state)
        g.removeRf(rd)
        rd.internalValue = wr.value as Int
        g.addRF(rd, wr)
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            if (toBeAddedEvents.isNotEmpty()) {
                addWrxAfterRdx(toBeAddedEvents[0] as WriteExEvent)
            }
            visit(g, toBeAddedEvents)
        }
    }

    fun processBR_W_R(
        g: OptExecutionGraph,
        nextWrite: WriteEvent,
        state: RevisitState?
    ) {
        restrictStrictAfterEvent(g, nextWrite, state)
        g.removeWrite(nextWrite)
        backwardRevisit(g, nextWrite, state)
    }


    private fun backwardRevisit(g: OptExecutionGraph, nextWrite: WriteEvent, state: RevisitState?) {
        val closureGraph = ClosureGraph()
        computePorf(g, closureGraph)
        for (readEvent in g.reads[nextWrite.loc]!!) {
            if ((readEvent.tid != nextWrite.tid) && !closureGraph.pathExists(readEvent, nextWrite)) {
                val deleted = LinkedHashSet<ThreadEvent>()

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
                }
                val porfPrefix = computePorfPrefix(g, deleted, readEvent)
                revisitIfIsMaximal(g, porfPrefix, deleted, nextWrite, readEvent, state)
            }
        }
    }

    fun processFR_W_W(
        g: OptExecutionGraph,
        nextWrite: WriteEvent,
        writeEvent: WriteEvent,
        toBeAddedEvents: ArrayList<ThreadEvent>,
        state: RevisitState?
    ) {
        restrictStrictAfterEvent(g, nextWrite, state)
        g.removeWrite(nextWrite)
        g.addWriteBefore(nextWrite, writeEvent)
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            if (toBeAddedEvents.isNotEmpty()) {
                addWrxAfterRdx(toBeAddedEvents[0] as WriteExEvent)
            }
            visit(g, toBeAddedEvents)
        }
    }

    fun addWrxAfterRdx(wr: WriteExEvent) {
        // Start from the end of the topoSort and find the event which it has the same tid as the wr
        // Then add the wr after that event
        for (i in topoSort!!.size - 1 downTo 0) {
            if (topoSort!![i].tid == wr.tid && topoSort!![i].serial + 1 == wr.serial) {
                topoSort!!.add(i + 1, wr)
                break
            }
        }
    }

    fun processFR_L_W(
        g: OptExecutionGraph,
        writeEvent: WriteEvent,
        toBeAddedEvents: ArrayList<ThreadEvent>,
        state: RevisitState?
    ) {
        restrictStrictAfterEvent(g, writeEvent, state)
        g.removeWrite(writeEvent)
        g.addWrite(writeEvent)
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            if (toBeAddedEvents.isNotEmpty()) {
                addWrxAfterRdx(toBeAddedEvents[0] as WriteExEvent)
            }
            visit(g, toBeAddedEvents)
        }
    }

    fun processFR_neg_sym(g: OptExecutionGraph, event: SymExecutionEvent, state: RevisitState?) {
        restrictStrictAfterEvent(g, event, state)
        event.result = !event.result
        topoSort = SequentialConsistency.scAcyclicity(g)
        if (topoSort!!.isNotEmpty()) {
            visit(g, ArrayList())
        }
    }

    private fun isMaximal(
        g: OptExecutionGraph,
        porfPrefix: HashSet<ThreadEvent>,
        set: LinkedHashSet<ThreadEvent>,
        write: WriteEvent,
        state: RevisitState?
    ): Boolean {
        var maximal = true
        var symEvents: HashSet<SymExecutionEvent> = HashSet()
        for (event in set) {
            if (event is SymExecutionEvent) {
                symEvents.add(event)
                continue
            }
            if (!isMaximallyAdded(g, porfPrefix, event, write)) {
                maximal = false
                break
            }
        }
        if (maximal) {
            state?.deleted?.add(symEvents)
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
            return true
        }

        if (event.type == EventType.WRITE_EX) {
            val writeEx = event as WriteExEvent
            val rdEx =
                g.programOrder[writeEx.tid]!!.find { it.type == EventType.READ_EX && it.tid == writeEx.tid && it.serial + 1 == writeEx.serial } as ReadExEvent
            writeEx.loc = rdEx.loc
            if (rdEx.internalValue != writeEx.conditionValue) {
                return true
            }
        }

        val previous = HashSet<ThreadEvent>()
        computePrevious(g, porfPrefix, event, write, previous)
        if (event is ReadsFrom) {
            for (prev in previous) {
                if (prev is ReadEvent) {
                    val read = prev
                    if (g.rf[read]!!.equals(event)) {
                        return false
                    }
                }
            }
        }

        val eventPrime: ThreadEvent = if (event is ReadEvent) {
            g.rf[event] as WriteEvent
        } else {
            event
        }

        if (previous.contains(eventPrime)) {
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

    private fun findNext(allEvents: ArrayList<ThreadEvent>): Event? {
        return if (allEvents.isNotEmpty()) allEvents.removeFirst()
        else null
    }

    private fun restrictStrictAfterEvent(g: OptExecutionGraph, event: ThreadEvent, state: RevisitState?) {
        val index = g.eventOrder.indexOf(event)
        val toRemove = g.eventOrder.subList(index + 1, g.eventOrder.size).toHashSet()
        g.eventOrder = ArrayList(g.eventOrder.subList(0, index + 1))
        if (toRemove.isNotEmpty()) {
            restrictRelations(g, toRemove, state)
        }
    }

    private fun restrictRelations(g: OptExecutionGraph, set: HashSet<ThreadEvent>, state: RevisitState?) {
        val eventsToRemoveByTid = HashMap<Int, HashSet<ThreadEvent>>()
        for (e in set) {
            eventsToRemoveByTid.computeIfAbsent(e.tid) { HashSet() }.add(e)
            when (e) {
                is ReadEvent -> {
                    g.reads[e.loc]?.remove(e)
                    g.rf.remove(e)
                }

                is WriteEvent -> {
                    g.writes[e.loc]?.remove(e)
                    g.rf.entries.removeIf { it.value == e }
                }

                is StartEvent -> {
                    g.tc.remove(e)
                    g.st.removeIf { it.second == e }
                }

                is JoinEvent -> {
                    g.jt.removeIf { it.second == e }
                }

                is SymExecutionEvent -> {
                    g.symEvents.remove(e)
                    if (state != null) {
                        state.numOfPop++
                    }
                }

                is SymAssumeEvent -> {
                    if (e.result) {
                        g.symEvents.remove(e)
                        if (state != null) {
                            state.numOfPop++
                        }
                    }
                }
            }
        }

        g.reads.entries.removeIf { it.value.isEmpty() }
        g.writes.entries.removeIf { it.value.isEmpty() }

        for ((tid, events) in eventsToRemoveByTid) {
            g.programOrder[tid]?.removeAll(events)
            if (g.programOrder[tid]!!.isEmpty()) {
                g.programOrder.remove(tid)
            }
        }
    }
}