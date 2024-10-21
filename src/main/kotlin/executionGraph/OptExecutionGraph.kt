package executionGraph

import programStructure.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

data class OptExecutionGraph(
    var eventOrder: ArrayList<ThreadEvent> = ArrayList(),
    var programOrder: HashMap<Int, ArrayList<ThreadEvent>> = HashMap(),
    var reads: HashMap<Location, ArrayList<ReadEvent>> = HashMap(),
    var writes: HashMap<Location, ArrayList<WriteEvent>> = HashMap(),
    var st: ArrayList<Pair<ThreadEvent, ThreadEvent>> = ArrayList(),
    var tc: ArrayList<ThreadEvent> = ArrayList(),
    var jt: ArrayList<Pair<ThreadEvent, ThreadEvent>> = ArrayList(),
    var rf: HashMap<ReadEvent, WriteEvent> = HashMap(),
    var id: Int = 0
) {

    fun addEvent(event: ThreadEvent) {
        eventOrder.add(event)
    }

    fun addProgramOrder(event: ThreadEvent) {
        programOrder.computeIfAbsent(event.tid) { ArrayList() }.add(event)
    }

    fun addRead(event: ReadEvent) {
        reads.computeIfAbsent(event.loc!!) { ArrayList() }.add(event)
    }

    fun addWrite(event: WriteEvent) {
        writes.computeIfAbsent(event.loc!!) { ArrayList() }.add(event)
    }

    fun addST(event1: ThreadEvent, event2: ThreadEvent) {
        st.add(Pair(event1, event2))
    }

    fun addTC(event: StartEvent) {
        tc.add(event)
    }

    fun addJT(event1: ThreadEvent, event2: ThreadEvent) {
        jt.add(Pair(event1, event2))
    }

    fun addRF(event1: ReadEvent, event2: WriteEvent) {
        if (rf.containsKey(event1)) {
            //println("[debugging] RF already contains $event1")

            rf[event1] = event2

        } else {
            //println("[debugging] RF does not contain $event1")
            rf[event1] = event2
        }
    }

    fun existsSameLocationWriteEvent(location: Location): Boolean {
        return writes.containsKey(location)
    }

    fun existsSameLocationReadEvent(location: Location): Boolean {
        return reads.containsKey(location)
    }

    fun restrictStrictAfterEvent(event: ThreadEvent) {
        val index = eventOrder.indexOf(event)
        val toRemove = eventOrder.subList(index + 1, eventOrder.size).toHashSet()
        eventOrder = ArrayList(eventOrder.subList(0, index + 1))
        if (toRemove.isNotEmpty()) {
            restrictRelations(toRemove)
        }
    }

    fun restrictStrictFromTo(from: ThreadEvent, to: ThreadEvent) {
        val indexFrom = eventOrder.indexOf(from)
        val indexTo = eventOrder.indexOf(to)
        val toRemove = eventOrder.subList(indexFrom + 1, indexTo).toHashSet()
        eventOrder = eventOrder.subList(0, indexFrom + 1) as ArrayList<ThreadEvent>
        if (toRemove.isNotEmpty()) {
            restrictRelations(toRemove)
        }
    }

    private fun restrictRelations(set: HashSet<ThreadEvent>) {
        val eventsToRemoveByTid = HashMap<Int, HashSet<ThreadEvent>>()
        for (e in set) {
            eventsToRemoveByTid.computeIfAbsent(e.tid) { HashSet() }.add(e)
            when (e) {
                is ReadEvent -> {
                    reads[e.loc]?.remove(e)
                    rf.remove(e)
                }

                is WriteEvent -> {
                    writes[e.loc]?.remove(e)
                    rf.entries.removeIf { it.value == e }
                }

                is StartEvent -> {
                    tc.remove(e)
                    st.removeIf { it.second == e }
                }

                is JoinEvent -> {
                    jt.removeIf { it.second == e }
                }
            }
        }

        reads.entries.removeIf { it.value.isEmpty() }
        writes.entries.removeIf { it.value.isEmpty() }

        for ((tid, events) in eventsToRemoveByTid) {
            programOrder[tid]?.removeAll(events)
        }
    }

    fun printPO() {
        for (k in programOrder.keys) {
            if (programOrder[k]!!.size > 0) {
                print("Thread $k: ")
                for (i in 0 until this.programOrder[k]!!.size) {
                    print("${this.programOrder[k]!![i].type}(${this.programOrder[k]!![i].tid}:${this.programOrder[k]!![i].serial}) -> ")
                }
                println()
            }
        }
    }

    fun printEventOrder() {
        for (i in 0 until this.eventOrder.size) {
            print("${this.eventOrder[i].type}(${this.eventOrder[i].tid}:${this.eventOrder[i].serial}) -> ")
        }
        println()
    }

    fun printRf() {
        for (entry in rf) {
            println("${entry.key.hashCode()}(${entry.key.tid}:${entry.key.serial}) -> ${entry.value.type}(${entry.value.tid}:${entry.value.serial})")
        }
    }

    fun printCO() {
        for (k in writes.keys) {
            if (writes[k]!!.size > 0) {
                for (i in 0 until this.writes[k]!!.size) {
                    print("${this.writes[k]!![i].type}(${this.writes[k]!![i].tid}:${this.writes[k]!![i].serial}) -> ")
                }
                println()
            }
        }
    }

    fun printTC() {
        for (i in 0 until this.tc.size - 1) {
            println("${this.tc[i].type}(${this.tc[i].tid}:${this.tc[i].serial}) -> ${this.tc[i + 1].type}(${this.tc[i + 1].tid}:${this.tc[i + 1].serial})")
        }
    }

    fun printST() {
        for (pair in st) {
            println("${pair.first.type}(${pair.first.tid}:${pair.first.serial}) -> ${pair.second.type}(${pair.second.tid}:${pair.second.serial}) -> ")
        }
    }

    fun printJT() {
        for (pair in jt) {
            println("${pair.first.type}(${pair.first.tid}:${pair.first.serial}) -> ${pair.second.type}(${pair.second.tid}:${pair.second.serial}) -> ")
        }
    }

    fun printReads() {
        for (k in reads.keys) {
            if (reads[k]!!.size > 0) {
                for (i in 0 until this.reads[k]!!.size) {
                    print("${this.reads[k]!![i].type}(${this.reads[k]!![i].tid}:${this.reads[k]!![i].serial}) -> ")
                }
                println()
            }
        }
    }

    fun restrictAfterEvent(event: ThreadEvent) {
        val index = eventOrder.indexOf(event)
        val toRemove = eventOrder.subList(index, eventOrder.size).toHashSet()
        eventOrder = eventOrder.subList(0, index) as ArrayList<ThreadEvent>
        if (toRemove.isNotEmpty()) {
            restrictRelations(toRemove)
        }
    }

    fun restrictGraph(set: HashSet<ThreadEvent>) {
        eventOrder.removeIf { it in set }
        restrictRelations(set)
    }

    fun removeLastWriteEvent(location: Location): Boolean {
        if (writes.containsKey(location)) {
            if (writes[location]!!.isNotEmpty()) {
                writes[location]!!.removeLast()
                return true
            } else {
                return false
            }
        } else {
            return false
        }
    }

    fun removeWrite(write: WriteEvent): Boolean {
        return writes[write.loc]!!.remove(write)
    }

    fun addWriteAfter(element: WriteEvent, after: WriteEvent) {
        val index = writes[element.loc]!!.indexOf(after)
        writes[element.loc]!!.add(index + 1, element)
    }

    fun addWriteBefore(element: WriteEvent, before: WriteEvent) {
        val index = writes[element.loc]!!.indexOf(before)
        writes[element.loc]!!.add(index, element)
    }

    fun deepCopy(oldDeleted: LinkedHashSet<ThreadEvent>, newDeleted: LinkedHashSet<ThreadEvent>): OptExecutionGraph {
        val newExecutionGraph = OptExecutionGraph()
        val eventMap = eventOrder.associateBy({ it }, { it.deepCopy() as ThreadEvent })

        newExecutionGraph.eventOrder.addAll(eventMap.values)

        programOrder.forEach { (key, value) ->
            newExecutionGraph.programOrder[key] = value.map { event ->
                val mappedEvent = eventMap[event]!!
                if (oldDeleted.contains(event)) {
                    newDeleted.add(mappedEvent)
                }
                mappedEvent
            } as ArrayList<ThreadEvent>
        }

        reads.forEach { (key, value) ->
            newExecutionGraph.reads[key] = value.map { eventMap[it]!! } as ArrayList<ReadEvent>
        }

        writes.forEach { (key, value) ->
            newExecutionGraph.writes[key] = value.map { eventMap[it]!! } as ArrayList<WriteEvent>
        }

        st.forEach { (first, second) ->
            newExecutionGraph.st.add(Pair(eventMap[first]!!, eventMap[second]!!))
        }

        tc.forEach { event ->
            newExecutionGraph.tc.add(eventMap[event]!!)
        }

        jt.forEach { (first, second) ->
            newExecutionGraph.jt.add(Pair(eventMap[first]!!, eventMap[second]!!))
        }

        rf.forEach { (key, value) ->
            newExecutionGraph.rf[eventMap[key] as ReadEvent] = eventMap[value] as WriteEvent
        }

        return newExecutionGraph
    }

    fun deepCopy(): OptExecutionGraph {
        val newExecutionGraph = OptExecutionGraph()
        val eventMap = eventOrder.associateBy({ it }, { it.deepCopy() as ThreadEvent })

        newExecutionGraph.eventOrder.addAll(eventMap.values)

        programOrder.forEach { (key, value) ->
            newExecutionGraph.programOrder[key] = value.map { eventMap[it]!! } as ArrayList<ThreadEvent>
        }

        reads.forEach { (key, value) ->
            newExecutionGraph.reads[key] = value.map { eventMap[it]!! } as ArrayList<ReadEvent>
        }

        writes.forEach { (key, value) ->
            newExecutionGraph.writes[key] = value.map { eventMap[it]!! } as ArrayList<WriteEvent>
        }

        st.forEach { (first, second) ->
            newExecutionGraph.st.add(Pair(eventMap[first]!!, eventMap[second]!!))
        }

        tc.forEach { event ->
            newExecutionGraph.tc.add(eventMap[event]!!)
        }

        jt.forEach { (first, second) ->
            newExecutionGraph.jt.add(Pair(eventMap[first]!!, eventMap[second]!!))
        }

        rf.forEach { (key, value) ->
            newExecutionGraph.rf[eventMap[key] as ReadEvent] = eventMap[value] as WriteEvent
        }

        return newExecutionGraph
    }

    fun findReadEvent(readEvent: ReadEvent): ReadEvent? {
        return reads[readEvent.loc]?.find { it.tid == readEvent.tid && it.serial == readEvent.serial }
    }

    fun findWriteEvent(writeEvent: WriteEvent): WriteEvent? {
        return writes[writeEvent.loc]?.find { it.tid == writeEvent.tid && it.serial == writeEvent.serial }
    }

    fun removeRf(readEvent: ReadEvent) {
        rf.remove(readEvent)
    }

    fun areExReadsConsistent(write: WriteEvent): Boolean {
        if (reads[write.loc]!!.size > 1) {
            var counter = 0
            for (i in reads[write.loc]!!.size - 1 downTo 0) {
                val readEvent = reads[write.loc]!![i]
                val rf = rf[readEvent]
                if (rf == write) {
                    counter++
                }
                if (counter > 1) {
                    return false
                }
            }
            return true
        } else {
            return true
        }
    }

    fun areExReadsConsistent(loc: Location): Boolean {
        val wr: HashSet<WriteEvent> = HashSet()
//        println("[debugging] does reads contains the location : ${reads.containsKey(loc)}")
//        println("[debugging] the rf is :")
//        for (entry in rf) {
//            println("[debugging] key :${entry.key})")
//            println("[debugging] value :${entry.value})")
//
//        }
        for (i in 0 until reads[loc]!!.size) {
//            println("[debugging] rf-wr is : ${rf.contains(reads[loc]!![i])}")
//            println("[debugging] read : ${reads[loc]!![i]}")
            val readEvent = reads[loc]!![i]
            val writeEvent = rf[readEvent]
            if (wr.contains(writeEvent)) {
                return false
            } else {
                wr.add(writeEvent!!)
            }
        }
        return true
    }

    fun visualizeGraph(graphID: Int, path: String) {
        val dotFile = File("${path}Execution_Graph_${graphID}.dot")
        val fileWriter = FileWriter(dotFile)
        val bufferedWriter = BufferedWriter(fileWriter)
        bufferedWriter.write("digraph {")

        // This part prints the children of the root node
        for (k in programOrder.keys) {
            val event = programOrder[k]!![0]
            visEvent(event, bufferedWriter)
            visRootToEventEdge(event, bufferedWriter)
        }

        // This part prints each thread of the root's children
        for (k in programOrder.keys) {
            val threadEvents = programOrder[k]!!
            for (i in 0 until threadEvents.size - 1) {
                visEvent(threadEvents[i + 1], bufferedWriter)
                visEventToEventEdge(threadEvents[i], threadEvents[i + 1], bufferedWriter)
            }
        }

        if (this.writes.isNotEmpty()) {
            visCOs(bufferedWriter)
        }

        if (this.st.isNotEmpty()) {
            visSTs(bufferedWriter)
        }

        if (this.jt.isNotEmpty()) {
            visJTs(bufferedWriter)
        }

        if (this.tc.isNotEmpty()) {
            visTCs(bufferedWriter)
        }

        if (this.rf.isNotEmpty()) {
            for (entry in rf) {
                bufferedWriter.newLine()
                bufferedWriter.write("${entry.value.tid}${entry.value.serial} -> ${entry.key.tid}${entry.key.serial}[color=red, label=\"rf\"];")
            }
        }

        bufferedWriter.newLine()
        bufferedWriter.write("}")
        bufferedWriter.close()

        dot2png(path, "Execution_Graph_${graphID}")
    }

    private fun visCOs(bufferedWriter: BufferedWriter) {
        for (k in writes.keys) {
            for (i in 0 until this.writes[k]!!.size - 1) {
                val firstTid = this.writes[k]!![i].tid
                val firstSerial = this.writes[k]!![i].serial
                val secondTid = this.writes[k]!![i + 1].tid
                val secondSerial = this.writes[k]!![i + 1].serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=blue, label=\"co\"];")
            }
        }
    }

    private fun visTCs(bufferedWriter: BufferedWriter) {

        for (i in 0 until this.tc.size - 1) {
            val firstTid = this.tc[i].tid
            val firstSerial = this.tc[i].serial
            val secondTid = this.tc[i + 1].tid
            val secondSerial = this.tc[i + 1].serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=turquoise4, label=\"tc\"];")
        }
    }

    private fun visSTs(bufferedWriter: BufferedWriter) {
        for (i in this.st.indices) {
            val firstTid = this.st.elementAt(i).first.tid
            val firstSerial = this.st.elementAt(i).first.serial
            val secondTid = this.st.elementAt(i).second.tid
            val secondSerial = this.st.elementAt(i).second.serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=green, label=\"st\"];")
        }
    }

    private fun visJTs(bufferedWriter: BufferedWriter) {
        for (i in this.jt.indices) {
            val firstTid = this.jt.elementAt(i).first.tid
            val firstSerial = this.jt.elementAt(i).first.serial
            val secondTid = this.jt.elementAt(i).second.tid
            val secondSerial = this.jt.elementAt(i).second.serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=orange, label=\"jt\"];")
        }
    }

    private fun visEventToEventEdge(firstEvent: ThreadEvent, secondEvent: ThreadEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${firstEvent.tid}${firstEvent.serial} -> ${secondEvent.tid}${secondEvent.serial};")
    }

    private fun visRootToEventEdge(threadEvent: ThreadEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("root -> ${threadEvent.tid}${threadEvent.serial};")
    }

    private fun visEvent(event: Event, bufferedWriter: BufferedWriter) {
        when (event.type) {
            EventType.READ -> {
                visReadEvent(event as ReadEvent, bufferedWriter)
//                if (event.rf != null) {
//                    visReadFromEdge(event, bufferedWriter)
//                }
            }

            EventType.WRITE -> {
                visWriteEvent(event as WriteEvent, bufferedWriter)
            }

            EventType.RECEIVE -> {
                visReceiveEvent(event as ReceiveEvent, bufferedWriter)
            }

            EventType.BLOCKED_RECV -> {
                visBlockedReceiveEvent(event as BlockedRecvEvent, bufferedWriter)
            }

            EventType.UNBLOCKED_RECV -> {
                visUnblockedReceiveEvent(event as UnblockedRecvEvent, bufferedWriter)
            }

            EventType.BLOCK_RECV_REQ -> {
                visBlockingReceiveRequestEvent(event as BlockingRecvReq, bufferedWriter)
            }

            EventType.SEND -> {
                visSendEvent(event as SendEvent, bufferedWriter)
            }

            EventType.START -> {
                visStartEvent(event as StartEvent, bufferedWriter)
            }

            EventType.JOIN -> {
                visJoinEvent(event as JoinEvent, bufferedWriter)
            }

            EventType.FINISH -> {
                visFinishEvent(event as FinishEvent, bufferedWriter)
            }

            EventType.ENTER_MONITOR -> {
                visEnterMonitorEvent(event as EnterMonitorEvent, bufferedWriter)
            }

            EventType.EXIT_MONITOR -> {
                visExitMonitorEvent(event as ExitMonitorEvent, bufferedWriter)
            }

            EventType.DEADLOCK -> {
                visDeadlockEvent(event as DeadlockEvent, bufferedWriter)
            }

            EventType.MONITOR_REQUEST -> {
                visMonitorReqEvent(event as MonitorRequestEvent, bufferedWriter)
            }

            EventType.FAILURE -> {
                visFailureEvent(event as FailureEvent, bufferedWriter)
            }

            EventType.SUSPEND -> {
                visSuspendEvent(event as SuspendEvent, bufferedWriter)
            }

            EventType.UNSUSPEND -> {
                visUnsuspendEvent(event as UnsuspendEvent, bufferedWriter)
            }

            EventType.SYM_EXECUTION -> {
                visSymbolicExeEvent(event as SymExecutionEvent, bufferedWriter)
            }

            EventType.PARK -> {
                visParkEvent(event as ParkEvent, bufferedWriter)
            }

            EventType.UNPARK -> {
                visUnparkEvent(event as UnparkEvent, bufferedWriter)
            }

            EventType.UNPARKING -> {
                visUnparkingEvent(event as UnparkingEvent, bufferedWriter)
            }

            EventType.MAIN_START -> {
                visMainStartEvent(event as MainStartEvent, bufferedWriter)
            }

            EventType.ASSIGNED_TASK -> {
                visAssignedTaskEvent(event as AssignedTaskEvent, bufferedWriter)
            }

            EventType.CON_ASSUME -> {
                visConAssumeEvent(event as ConAssumeEvent, bufferedWriter)
            }

            EventType.SYM_ASSUME -> {
                visSymAssumeEvent(event as SymAssumeEvent, bufferedWriter)
            }

            EventType.ASSUME_BLOCKED -> {
                visAssumeBlockedEvent(event as AssumeBlockedEvent, bufferedWriter)
            }

            EventType.READ_EX -> {
                visReadExEvent(event as ReadExEvent, bufferedWriter)
//                if (event.rf != null) {
//                    visReadFromEdge(event, bufferedWriter)
//                }
            }

            EventType.WRITE_EX -> {
                visWriteExEvent(event as WriteExEvent, bufferedWriter)
            }

            EventType.AWAIT_TASK -> {
                visAwaitTaskEvent(event as AwaitTaskEvent, bufferedWriter)
            }

            EventType.NEW_RUN -> {
                visNewRunEvent(event as NewRunEvent, bufferedWriter)
            }

            EventType.NEW_TASK -> {
                visNewTaskEvent(event as NewTaskEvent, bufferedWriter)
            }

            EventType.OTHER -> TODO()
            EventType.INITIAL -> TODO()
        }
    }

    private fun visReadExEvent(readEx: ReadExEvent, bufferedWriter: BufferedWriter) {
        var param = locOfEvent(readEx.loc!!)
        bufferedWriter.newLine()
        bufferedWriter.write("${readEx.tid}${readEx.serial} [label=\"${readEx.tid}:${readEx.serial}.RdEx(${param},v=${readEx.intValue})\"]")
    }

    private fun visWriteExEvent(writeEx: WriteExEvent, bufferedWriter: BufferedWriter) {
        var param = locOfEvent(writeEx.loc!!)
        bufferedWriter.newLine()
        bufferedWriter.write("${writeEx.tid}${writeEx.serial} [label=\"${writeEx.tid}:${writeEx.serial}.WEx(${param},r=${writeEx.operationSuccess})\"]")
    }

    private fun visConAssumeEvent(conAssume: ConAssumeEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${conAssume.tid}${conAssume.serial} [label=\"${conAssume.tid}:${conAssume.serial}.Assume(${conAssume.result})\"]")
    }

    private fun visSymAssumeEvent(symAssume: SymAssumeEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${symAssume.tid}${symAssume.serial} [label=\"${symAssume.tid}:${symAssume.serial}.Assume(${symAssume.formula},${symAssume.result})\"]")
    }

    private fun visAssumeBlockedEvent(assumeBlocked: AssumeBlockedEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${assumeBlocked.tid}${assumeBlocked.serial} [label=\"${assumeBlocked.tid}:${assumeBlocked.serial}.AssumeBlocked()\"]")
    }

    private fun visNewTaskEvent(newTaskEvent: NewTaskEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${newTaskEvent.tid}${newTaskEvent.serial} [label=\"${newTaskEvent.tid}:${newTaskEvent.serial}.New Task\"]")
    }

    private fun visNewRunEvent(newRunEvent: NewRunEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${newRunEvent.tid}${newRunEvent.serial} [label=\"${newRunEvent.tid}:${newRunEvent.serial}.New Run\"]")
    }

    private fun visAwaitTaskEvent(awaitTaskEvent: AwaitTaskEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${awaitTaskEvent.tid}${awaitTaskEvent.serial} [label=\"${awaitTaskEvent.tid}:${awaitTaskEvent.serial}.Await Task\"]")
    }

    private fun visAssignedTaskEvent(assignedTaskEvent: AssignedTaskEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${assignedTaskEvent.tid}${assignedTaskEvent.serial} [label=\"${assignedTaskEvent.tid}:${assignedTaskEvent.serial}.Assigned Task\"]")
    }

    private fun visWriteEvent(write: WriteEvent, bufferedWriter: BufferedWriter) {
        var param = locOfEvent(write.loc!!)
        bufferedWriter.newLine()
        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
    }

    private fun visReadEvent(read: ReadEvent, bufferedWriter: BufferedWriter) {
        var param = locOfEvent(read.loc!!)
        bufferedWriter.newLine()
        bufferedWriter.write(
            "${read.tid}${read.serial} [label=\"${read.tid}:" +
                    "${read.serial}.Rd(${param})\"]"
        )
    }

    private fun visStartEvent(start: StartEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${start.tid}${start.serial} [label=\"${start.tid}:${start.serial}.Thread Started\"]")
    }

    private fun visMainStartEvent(mainStart: MainStartEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${mainStart.tid}${mainStart.serial} [label=\"${mainStart.tid}:${mainStart.serial}.Main Thread Started\"]")
    }

    private fun visJoinEvent(join: JoinEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
    }

    private fun visFinishEvent(finish: FinishEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write(
            "${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}" +
                    ".Thread Finished\"]"
        )
    }

    private fun visFailureEvent(failure: FailureEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write(
            "${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}" +
                    ".Thread Failure\"]"
        )
    }

    private fun visDeadlockEvent(deadlock: DeadlockEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
    }

    private fun visMonitorReqEvent(monitorRequest: MonitorRequestEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write(
            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}" +
                    ".Monitor Request@${monitorRequest.monitor.hashCode().toString(16)}\"]"
        )
    }

    private fun visEnterMonitorEvent(enterMonitor: EnterMonitorEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write(
            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                enterMonitor.monitor.hashCode().toString(16)
            }\"]"
        )
    }

    private fun visExitMonitorEvent(exitMonitor: ExitMonitorEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write(
            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                exitMonitor.monitor.hashCode().toString(16)
            }\"]"
        )
    }

    private fun visSuspendEvent(suspend: SuspendEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
    }

    private fun visUnsuspendEvent(unsuspend: UnsuspendEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
    }

    private fun visSymbolicExeEvent(symExecution: SymExecutionEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write(
            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}" +
                    ".Sym Exe:(${symExecution.formula}, ${symExecution.result})\"]"
        )
    }

    private fun visParkEvent(park: ParkEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
    }

    private fun visUnparkEvent(unpark: UnparkEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
    }

    private fun visUnparkingEvent(unparking: UnparkingEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
    }

    private fun visReceiveEvent(receive: ReceiveEvent, bufferedWriter: BufferedWriter) {
        var message = messageOfEvent(receive.value)
        bufferedWriter.newLine()
        bufferedWriter.write("${receive.tid}${receive.serial} [label=\"${receive.tid}:${receive.serial}.Rcv(${message})\"]")
    }

    private fun visBlockedReceiveEvent(blockedReceive: BlockedRecvEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${blockedReceive.tid}${blockedReceive.serial} [label=\"${blockedReceive.tid}:${blockedReceive.serial}.Blocked_Recv()\"]")
    }

    private fun visUnblockedReceiveEvent(unblockedReceive: UnblockedRecvEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${unblockedReceive.tid}${unblockedReceive.serial} [label=\"${unblockedReceive.tid}:${unblockedReceive.serial}.Unblocked_Recv()\"]")
    }

    private fun visBlockingReceiveRequestEvent(
        blockingReceiveRequest: BlockingRecvReq,
        bufferedWriter: BufferedWriter
    ) {
        bufferedWriter.newLine()
        bufferedWriter.write("${blockingReceiveRequest.tid}${blockingReceiveRequest.serial} [label=\"${blockingReceiveRequest.tid}:${blockingReceiveRequest.serial}.Bloking_Recv_Req()\"]")
    }

    private fun visSendEvent(send: SendEvent, bufferedWriter: BufferedWriter) {
        var message = messageOfEvent(send.value!!)
        bufferedWriter.newLine()
        bufferedWriter.write("${send.tid}${send.serial} [label=\"${send.tid}:${send.serial}.S(${message})\"]")
    }

    private fun locOfEvent(loc: Location): String {
        return if (loc.instance == null) {
            loc.field?.name + " : ${loc.type} "
        } else {
            loc.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                    loc.instance.hashCode().toString(16) + ":" +
                    loc.field?.name + "@" + loc.field?.hashCode()?.toString(16) +
                    " : ${loc.type.toString().substringAfterLast('/')} "
        }
    }

    private fun messageOfEvent(message: Message?): String {
        if (message == null) {
            return "NULL"
        }
        return message.toString()
    }

    private fun visReadFromEdge(read: ReadEvent, bufferedWriter: BufferedWriter) {
        if (read.rf is WriteEvent) {
            val readFrom = read.rf as WriteEvent
            bufferedWriter.newLine()
            bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rdf\"];")
        } else if (read.rf is WriteExEvent) {
            val readFrom = read.rf as WriteExEvent
            bufferedWriter.newLine()
            bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rdf\"];")
        } else if (read.rf is InitializationEvent) {
            bufferedWriter.newLine()
            bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rdf\"];")
        }
    }

    private fun visReadFromEdge(read: ReadExEvent, bufferedWriter: BufferedWriter) {
        if (read.rf is WriteEvent) {
            val readFrom = read.rf as WriteEvent
            bufferedWriter.newLine()
            bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rdf\"];")
        } else if (read.rf is WriteExEvent) {
            val readFrom = read.rf as WriteExEvent
            bufferedWriter.newLine()
            bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rdf\"];")
        } else if (read.rf is InitializationEvent) {
            bufferedWriter.newLine()
            bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rdf\"];")
        }
    }

    private fun dot2png(dotPath: String, dotName: String) {
        val processBuilder =
            ProcessBuilder("dot", "-Tpng", "-o", "${dotPath}/${dotName}.png", "${dotPath}/${dotName}.dot")
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        process.waitFor()
    }

//    fun deepCopy(): ExecutionGraphI {
//        val newExecutionGraph = ExecutionGraphI(
//            eventOrder = ArrayList(),
//            programOrder = HashMap(),
//            reads = HashMap(),
//            writes = HashMap(),
//            st = ArrayList(),
//            tc = ArrayList(),
//            jt = ArrayList(),
//            rf = HashMap()
//        )
//        // For each element in eventOrder, iterate over the elements and add the deep copy of the element to the newExecutionGraph
//        for (event in eventOrder) {
//            newExecutionGraph.eventOrder.add(event.deepCopy() as ThreadEvent)
//        }
//
//        for (entry in programOrder) {
//            // In the eventOrder, all the events of a specific thread are located orderly. We need to project these events
//            // and add them to the programOrder[entry.key] with the same order. We need to do this in the most efficient way.
//            val threadEvents =
//                entry.value.map { event -> newExecutionGraph.eventOrder.find { it.tid == event.tid && it.serial == event.serial } as ThreadEvent }
//            newExecutionGraph.programOrder[entry.key] = threadEvents as ArrayList<ThreadEvent>
//        }
//        for (entry in reads) {
//            val readEvents =
//                entry.value.map { event -> newExecutionGraph.eventOrder.find { it.tid == event.tid && it.serial == event.serial } as ReadEvent }
//            newExecutionGraph.reads[entry.key] = readEvents as ArrayList<ReadEvent>
//        }
//
//        for (entry in writes) {
//            val writeEvents =
//                entry.value.map { event -> newExecutionGraph.eventOrder.find { it.tid == event.tid && it.serial == event.serial } as WriteEvent }
//            newExecutionGraph.writes[entry.key] = writeEvents as ArrayList<WriteEvent>
//        }
//
//        for (pair in st) {
//            // find the values of the pair among the eventOrder elements and add it to the newExecutionGraph
//            newExecutionGraph.st.add(
//                Pair(
//                    newExecutionGraph.eventOrder.find {
//                        it.tid == pair.first.tid
//                                && it.serial == pair.first.serial
//                    }!!,
//                    newExecutionGraph.eventOrder.find {
//                        it.tid == pair.second.tid
//                                && it.serial == pair.second.serial
//                    }!!
//                )
//            )
//        }
//
//        for (pair in tc) {
//            // find the values of the pair among the eventOrder elements and add it to the newExecutionGraph
//            newExecutionGraph.tc.add(
//                Pair(
//                    newExecutionGraph.eventOrder.find {
//                        it.tid == pair.first.tid
//                                && it.serial == pair.first.serial
//                    }!!,
//                    newExecutionGraph.eventOrder.find {
//                        it.tid == pair.second.tid
//                                && it.serial == pair.second.serial
//                    }!!
//                )
//            )
//        }
//
//        for (pair in jt) {
//            // find the values of the pair among the eventOrder elements and add it to the newExecutionGraph
//            newExecutionGraph.jt.add(
//                Pair(
//                    newExecutionGraph.eventOrder.find {
//                        it.tid == pair.first.tid
//                                && it.serial == pair.first.serial
//                    }!!,
//                    newExecutionGraph.eventOrder.find {
//                        it.tid == pair.second.tid
//                                && it.serial == pair.second.serial
//                    }!!
//                )
//            )
//        }
//
//        for (entry in rf) {
//            // find the values of the entry among the eventOrder elements and add it to the newExecutionGraph
//            newExecutionGraph.rf[newExecutionGraph.eventOrder.find {
//                it.tid == entry.key.tid
//                        && it.serial == entry.key.serial
//            } as ReadEvent] = newExecutionGraph.eventOrder.find {
//                it.tid == entry.value.tid
//                        && it.serial == entry.value.serial
//            } as WriteEvent
//        }
//
//        return newExecutionGraph
//    }
}