package executionGraph

// import com.google.gson.Gson
import programStructure.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.Serializable

data class ExecutionGraph(

    /**
     * @property root The root of the execution graph
     */
    var root: RootNode? = null,

    /**
     * @property graphEvents The list of all events in the graph (Represents G.E in Trust paper)
     */
    var graphEvents: MutableList<Event> = mutableListOf(),

    /**
     * @property eventsOrder The ordered list of added events to trust algorithm (Represents $\leq_G$ in Trust paper)
     */
    var eventsOrder: MutableList<Event> = mutableListOf(),

    /**
     * @property COs The list of CO relations in the graph
     */
    var COs: MutableList<CO> = mutableListOf(),

    /**
     * @property STs The list of start thread (ST) relations in the graph
     */
    var STs: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    /**
     * @property JTs The list of join thread (JT) relations in the graph
     */
    var JTs: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    /**
     * @property MCs The list of monitor coherency (MC) relations in the graph
     */
    var MCs: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    /**
     * @property TCs The list of Thread Coherency (TC) relations in the graph
     */
    var TCs: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    /**
     * @property PCs The list of Thread park coherency (PC) relations in the graph
     */
    var PCs: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    /**
     * @property porf The relation of program order and reads from (porf) in the graph
     */
    var porf: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    /**
     * @property sc The relation of sequential consistency (sc) in the graph
     */
    var sc: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    var recvFrom: MutableSet<Pair<Event, Event>> = mutableSetOf(),

    /**
     * @property deleted The list of deleted events in the graph (Represents Deleted set in Trust algorithm)
     */
    var deleted: MutableList<Event> = mutableListOf(),

    /**
     * @property previous The list of previous events of a given event in the graph with respect to its relation with
     * a given write event (Represents previous set in Trust algorithm)
     */
    var previous: MutableList<Event> = mutableListOf(),

    /**
     * @property id The id of the graph
     */
    var id: Int = 0
) : Serializable {

    /**
     * Adds an event to the execution graph if it's not already present.
     *
     * This method adds the event to the [graphEvents] list and the [eventsOrder] list if it's not already present.
     * Additionally, it updates the children of the [root] node if the event is not an initial event.
     *
     * @param event The event to be added to the execution graph.
     */
    fun addEvent(event: Event) {
        if (event !in graphEvents) {
            graphEvents.add(event)
            eventsOrder.add(event)
            if (event.type != EventType.INITIAL) {
                updateRootChildren(event)
            }
        }
    }

    /**
     * Updates the children of the [root] node with respect to the given event.
     *
     * This method is called by the [addEvent] method to update the children of the [root] node with respect to the
     * given event.
     */
    private fun updateRootChildren(event: Event) {
        val threadEvent = event as ThreadEvent
        if (root?.children?.keys?.contains(threadEvent.tid) == true) {
            var nextNode = root?.children!![threadEvent.tid]
            while (nextNode?.child != null) {
                nextNode = nextNode.child
            }
            nextNode?.child = EventNode(threadEvent)
        } else {
            root?.children?.put(threadEvent.tid, EventNode(threadEvent))
        }
    }

    /**
     * Adds a ST relation between two given events.
     *
     * @param firstEvent The first event in the ST relation.
     * @param secondEvent The second event in the ST relation.
     */
    fun addST(firstEvent: Event, secondEvent: Event) {
        STs.add(Pair(firstEvent, secondEvent))
    }

    fun addRecvFrom(firstEvent: Event, secondEvent: Event) {
        recvFrom.add(Pair(firstEvent, secondEvent))
    }

    fun removeRecvFrom(recv: Event) {
        // remove any pair that has the recv as the second element
        for (pair in recvFrom.toList()) {
            recvFrom.removeIf({ it.second == recv })
        }
    }

    /**
     * Adds a JT relation between two given events.
     *
     * @param firstEvent The first event in the JT relation.
     * @param secondEvent The second event in the JT relation.
     */
    fun addJT(firstEvent: Event, secondEvent: Event) {
        JTs.add(Pair(firstEvent, secondEvent))
    }

    /**
     * Adds a MC relation between two given events.
     *
     * @param firstEvent The first event in the MC relation.
     * @param secondEvent The second event in the MC relation.
     */
    fun addMC(firstEvent: Event, secondEvent: Event) {
        MCs.add(Pair(firstEvent, secondEvent))
    }

    /**
     * Adds a TC relation between two given start events.
     *
     * @param firstEvent The first event in the TC relation.
     * @param secondEvent The second event in the TC relation.
     */
    fun addTC(firstEvent: Event, secondEvent: Event) {
        TCs.add(Pair(firstEvent, secondEvent))
    }

    /**
     * Adds a PC relation between two given events.
     *
     * @param firstEvent The first event in the PC relation.
     * @param secondEvent The second event in the PC relation.
     */
    fun addPC(firstEvent: Event, secondEvent: Event) {
        PCs.add(Pair(firstEvent, secondEvent))
    }

    /**
     * Adds the root node to the execution graph.
     *
     * @param event The root event to be added to the execution graph.
     */
    fun addRoot(event: Event) {
        if (root == null) {
            root = RootNode(event)
            graphEvents.add(event)
            eventsOrder.add(event)
        }
    }

    /**
     * Computes the SC relation of the graph.
     *
     * This method computes the SC relation of the graph. First, it clears the [sc] set. Then, it adds pairs of the
     * initialization and each graph event to the [sc] set. Next, it computes the PO, RF, and FR relations of the graph.
     * After that, it adds the [COs] edges to the [sc] set. Then, it adds the [STs] edges to the [sc] set. Finally, it
     * adds the [JTs] edges to the [sc] set. Finally, it computes the transitive closure of the [sc] relation.
     */
    fun computeSc() {
        // First, clearing the sc of the graph
        this.sc = mutableSetOf()

        // Adding pairs of initialization event to each graph event
        addInitializationPairsToGraph()

        // The whole following part computes the po, rf, and fr relations
        computeRelations()

        //This part adds the co edges
        addCoEdges()

        // This part adds the st edges
        addStEdges()

        // This part adds the jt edges
        addJtEdges()

        // This part adds the mc edges
        addMcEdges()

        // This part adds the tc edges
        addTcEdges()

        // This part adds the pc edges
        addPcEdges()

        // Next, computing the transitive closure of sc relation
        computeTransitiveClosure()
    }

    /**
     * Adds the relation of the initialization event and each graph event to the [sc] set.
     */
    private fun addInitializationPairsToGraph() {
        for (i in 1 until this.graphEvents.size) {
            this.sc.add(Pair(this.graphEvents[0], this.graphEvents[i]))
        }
    }

    /**
     * Computes the PO, RF, and FR relations of the graph.
     *
     * This method computes the relations of the graph. It first iterates over the children of the [root] node and adds
     * the RF relation if the event is a read event. Also, it adds the FR relation if the read event has an RF relation.
     * Then, it iterates over the children of each thread and adds the PO relation between the events. Finally, it adds
     * the RF relation if the event is a read event and adds the FR relation if the read event has an RF relation.
     */
    private fun computeRelations() {
        for (i in this.root?.children!!.keys) {
            var node = this.root?.children!![i]!!
            // Adding rf
            if (node.value is ReadEvent) {
                val read = node.value as ReadEvent
                if (read.rf != null) {
                    this.sc.add(Pair(read.rf as Event, read as Event))

                    //Adding fr = rf^{-1} ; co
                    // rf^{-1} = Pair(read,read.rf)
                    for (j in 0 until this.COs.size) {
                        if (read.rf!!.equals(this.COs[j].firstWrite as Event)) {
                            if (read.rf!! is WriteEvent) {
                                this.sc.add(Pair(read, this.COs[j].secondWrite as Event))
                            } else { // read.rf is Init
                                if (locEquals(read.loc!!, this.COs[j].secondWrite.loc!!)) {
                                    this.sc.add(Pair(read, this.COs[j].secondWrite as Event))
                                }
                            }
                        }
                    }
                }
            }

            // Adding po
            var next = node.child
            while (next != null) {
                this.sc.add(Pair(node.value, next.value))
                node = next
                next = node.child
                // Adding rf
                if (node.value is ReadEvent) {
                    val read = node.value as ReadEvent
                    if (read.rf != null) {
                        this.sc.add(Pair(read.rf as Event, read as Event))

                        //Adding fr = rf^{-1} ; co
                        // rf^{-1} = Pair(read,read.rf)
                        for (j in 0 until this.COs.size) {
                            if (read.rf!!.equals(this.COs[j].firstWrite as Event)) {
                                if (read.rf!! is WriteEvent) {
                                    this.sc.add(Pair(read, this.COs[j].secondWrite as Event))
                                } else { // read.rf is Init
                                    if (locEquals(read.loc!!, this.COs[j].secondWrite.loc!!)) {
                                        this.sc.add(Pair(read, this.COs[j].secondWrite as Event))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun locEquals(loc1: Location, loc2: Location): Boolean {
        if (loc1.isPrimitive() && loc2.isPrimitive()) {
            return loc1.instance == loc2.instance && loc1.field == loc2.field && loc1.type == loc2.type
        } else if (!loc1.isPrimitive() && !loc2.isPrimitive()) {
            // TODO() : Right now, we assume that it is not needed to cover the case of non-primitive types for model checking
            return false
        } else {
            return false
        }
    }

    /**
     * Adds the [COs] edges to the [sc] set.
     */
    private fun addCoEdges() {
        for (i in 0 until this.COs.size) {
            this.sc.add(Pair(this.COs[i].firstWrite as Event, this.COs[i].secondWrite as Event))
        }
    }

    /**
     * Adds the [STs] edges to the [sc] set.
     */
    private fun addStEdges() {
        for (i in this.STs.indices) {
            this.sc.add(Pair(this.STs.elementAt(i).first, this.STs.elementAt(i).second))
        }
    }

    /**
     * Adds the [JTs] edges to the [sc] set.
     */
    private fun addJtEdges() {
        for (i in this.JTs.indices) {
            this.sc.add(Pair(this.JTs.elementAt(i).first, this.JTs.elementAt(i).second))
        }
    }

    /**
     * Adds the [MCs] edges to the [sc] set.
     */
    private fun addMcEdges() {
        for (i in this.MCs.indices) {
            this.sc.add(Pair(this.MCs.elementAt(i).first, this.MCs.elementAt(i).second))
        }
    }

    /**
     * Adds the [TCs] edges to the [sc] set.
     */
    private fun addTcEdges() {
        for (i in this.TCs.indices) {
            this.sc.add(Pair(this.TCs.elementAt(i).first, this.TCs.elementAt(i).second))
        }
    }

    /**
     * Adds the [PCs] edges to the [sc] set.
     */
    private fun addPcEdges() {
        for (i in this.PCs.indices) {
            this.sc.add(Pair(this.PCs.elementAt(i).first, this.PCs.elementAt(i).second))
        }
    }

    /**
     * Computes the transitive closure of the [sc] relation.
     */
    private fun computeTransitiveClosure() {
        var addedNewPairs = true
        while (addedNewPairs) {
            addedNewPairs = false
            for (pair in this.sc.toList()) {
                val (a, b) = pair
                for (otherPair in this.sc.toList()) {
                    val (c, d) = otherPair
                    if (b.equals(c) && !this.sc.contains(Pair(a, d))) {
                        this.sc.add(Pair(a, d))
                        addedNewPairs = true
                    }
                }
            }
        }
    }

    fun computeProgramOrderReceiveFrom() {

        // To make sure that a new porf is constructed
        this.porf = mutableSetOf()

        // This parts adds all the pairs of the initialization event and each graph event to the porf
        for (i in 1..<this.graphEvents.size) {
            this.porf.add(Pair(this.graphEvents[0], this.graphEvents[i]))
        }

        // This part computes the primitives porf elements ( adding program order + receive from )
        for (i in this.root?.children!!.keys) {
            var node = this.root?.children!![i]!!
            if (node.value is ReceiveEvent) {
                val recv = node.value as ReceiveEvent
                if (recv.rf != null) {
                    this.porf.add(Pair(recv.rf as Event, recv as Event))
                }
            }
            var next = node.child
            while (next != null) {
                this.porf.add(Pair(node.value, next.value))
                node = next
                next = node.child
                if (node.value is ReceiveEvent) {
                    val recv = node.value as ReceiveEvent
                    if (recv.rf != null) {
                        this.porf.add(Pair(recv.rf as Event, recv as Event))
                    }
                }
            }
        }

        // this part computes the complete transitive closure of porf
        var addedNewPairs = true
        while (addedNewPairs) {
            addedNewPairs = false
            for (pair in this.porf.toList()) {
                val (a, b) = pair
                for (otherPair in this.porf.toList()) {
                    val (c, d) = otherPair
                    if (b.equals(c) && !this.porf.contains(Pair(a, d))) {
                        this.porf.add(Pair(a, d))
                        addedNewPairs = true
                    }
                }
            }
        }
    }

    fun computeProgramOrderReadFrom() {

        // To make sure that a new porf is constructed
        this.porf = mutableSetOf()

        for (i in 1..<this.graphEvents.size) {
            this.porf.add(Pair(this.graphEvents[0], this.graphEvents[i]))
        }

        // This part computes the primitives porf elements
        for (i in this.root?.children!!.keys) {
            var node = this.root?.children!![i]!!
            if (node.value is ReadEvent) {
                val read = node.value as ReadEvent
                if (read.rf != null) {
                    this.porf.add(Pair(read.rf as Event, read as Event))
                }
            }
            var next = node.child
            while (next != null) {
                this.porf.add(Pair(node.value, next.value))
                node = next
                next = node.child
                if (node.value is ReadEvent) {
                    val read = node.value as ReadEvent
                    if (read.rf != null) {
                        this.porf.add(Pair(read.rf as Event, read as Event))
                    }
                }
            }
        }

        // This part computes the mc relation
        for (i in 0 until this.MCs.size) {
            this.porf.add(Pair(this.MCs.elementAt(i).first, this.MCs.elementAt(i).second))
        }

        // This part computes the tc relation
        for (i in 0 until this.TCs.size) {
            this.porf.add(Pair(this.TCs.elementAt(i).first, this.TCs.elementAt(i).second))
        }

        // This part computes the pc relation
        for (i in 0 until this.PCs.size) {
            this.porf.add(Pair(this.PCs.elementAt(i).first, this.PCs.elementAt(i).second))
        }

        // this part computes the complete transitive closure of porf
        var addedNewPairs = true
        while (addedNewPairs) {
            addedNewPairs = false
            for (pair in this.porf.toList()) {
                val (a, b) = pair
                for (otherPair in this.porf.toList()) {
                    val (c, d) = otherPair
                    if (b.equals(c) && !this.porf.contains(Pair(a, d))) {
                        this.porf.add(Pair(a, d))
                        addedNewPairs = true
                    }
                }
            }
        }
    }

    fun computeDeleted(pivotEvent: Event, newAddedEvent: Event) {
        // To make sure that a new deleted is constructed
        this.deleted = mutableListOf()

        val index = this.eventsOrder.indexOf(pivotEvent)
        for (i in index + 1 until this.eventsOrder.size - 1)
            if (!this.porf.contains(Pair(this.eventsOrder[i], newAddedEvent)))
                deleted.add(this.eventsOrder[i])
    }

    fun printSc() {
        println("SC:")
        if (this.sc.isEmpty())
            println("No sc exists")
        else {
            this.sc.forEach { pair ->
                println("t_${(pair.first as ThreadEvent).tid}:${(pair.first as ThreadEvent).serial} ${(pair.first as ThreadEvent).type} -> t_${(pair.second as ThreadEvent).tid}:${(pair.second as ThreadEvent).serial} ${(pair.second as ThreadEvent).type}")
            }
            println("End of SC")
        }
    }

    fun printPorf() {
        println("Porf is:")
        if (this.porf.isEmpty())
            println("No porf exists")
        else {
            this.porf.forEach { pair ->
                println("${pair.first} -> ${pair.second}")
            }
        }
    }

    fun printDeleted() {
        println("Deleted Events:")
        if (this.deleted.isEmpty())
            println("No deleted set exists")
        else {
            this.deleted.forEach { event ->
                println("$event")
            }
        }
    }

    fun computePrevious(firstEvent: Event, secondEvent: Event) {

        // To make sure that a new previous is constructed
        this.previous = mutableListOf()

        val index = this.eventsOrder.indexOf(firstEvent)
        for (i in 0 until this.graphEvents.size) {
            if (this.porf.contains(Pair(this.graphEvents[i], secondEvent))) {
                this.previous.add(this.graphEvents[i])
            } else if (this.eventsOrder.indexOf(this.graphEvents[i]) <= index) {
                this.previous.add(this.graphEvents[i])
            }
        }
    }

    fun restrictingGraph(): ExecutionGraph {
        val newGraph = ExecutionGraph()

        //println("Debugging Graph-" + this.id)
        //this.printDeleted()

        for (i in 0 until this.graphEvents.size) {
            if (!this.deleted.contains(this.graphEvents[i]))
                newGraph.graphEvents.add(this.graphEvents[i].deepCopy())
        }

        for (i in 0 until this.eventsOrder.size) {
            if (!this.deleted.contains(this.eventsOrder[i]))
                newGraph.eventsOrder.add(this.eventsOrder[i].deepCopy())
        }

        for (i in 0 until this.COs.size) {
            if (!this.deleted.contains(this.COs[i].secondWrite)) {
                if (this.COs[i].firstWrite is InitializationEvent) {
                    newGraph.COs.add(this.COs[i].deepCopy())
                } else {
                    val firstWrite = this.COs[i].firstWrite as WriteEvent
                    if (!this.deleted.contains(firstWrite)) {
                        newGraph.COs.add(this.COs[i].deepCopy())
                    }
                }
            }
        }

        for (i in 0 until this.STs.size) {
            if (!this.deleted.contains(this.STs.elementAt(i).first) &&
                !this.deleted.contains(this.STs.elementAt(i).second)
            ) {
                newGraph.STs.add(Pair(this.STs.elementAt(i).first.deepCopy(), this.STs.elementAt(i).second.deepCopy()))
            }
        }

        for (i in 0 until this.JTs.size) {
            if (!this.deleted.contains(this.JTs.elementAt(i).first) &&
                !this.deleted.contains(this.JTs.elementAt(i).second)
            ) {
                newGraph.JTs.add(Pair(this.JTs.elementAt(i).first.deepCopy(), this.JTs.elementAt(i).second.deepCopy()))
            }
        }

        for (i in 0 until this.MCs.size) {
            if (!this.deleted.contains(this.MCs.elementAt(i).first) &&
                !this.deleted.contains(this.MCs.elementAt(i).second)
            ) {
                newGraph.MCs.add(Pair(this.MCs.elementAt(i).first.deepCopy(), this.MCs.elementAt(i).second.deepCopy()))
            }
        }

        for (i in 0 until this.TCs.size) {
            if (!this.deleted.contains(this.TCs.elementAt(i).first) &&
                !this.deleted.contains(this.TCs.elementAt(i).second)
            ) {
                newGraph.TCs.add(Pair(this.TCs.elementAt(i).first.deepCopy(), this.TCs.elementAt(i).second.deepCopy()))
            }
        }

        for (i in 0 until this.PCs.size) {
            if (!this.deleted.contains(this.PCs.elementAt(i).first) &&
                !this.deleted.contains(this.PCs.elementAt(i).second)
            ) {
                newGraph.PCs.add(Pair(this.PCs.elementAt(i).first.deepCopy(), this.PCs.elementAt(i).second.deepCopy()))
            }
        }

        for (i in 0 until this.recvFrom.size) {
            if (!this.deleted.contains(this.recvFrom.elementAt(i).first) &&
                !this.deleted.contains(this.recvFrom.elementAt(i).second)
            ) {
                newGraph.recvFrom.add(
                    Pair(
                        this.recvFrom.elementAt(i).first.deepCopy(),
                        this.recvFrom.elementAt(i).second.deepCopy()
                    )
                )
            }
        }

        for (i in 0 until this.deleted.size) {
            val threadEvent = this.deleted[i] as ThreadEvent
            if (this.root?.children?.keys?.contains(threadEvent.tid) == true) {
                if (root?.children!![threadEvent.tid]?.value!!.equals(this.deleted[i])) {
                    root?.children!!.remove(threadEvent.tid)
                } else {
                    var node = root?.children!![threadEvent.tid]
                    while (node?.child != null && node.child?.value != this.deleted[i]) {
                        node = node.child
                    }
                    if (node?.child != null && node.child?.value!!.equals(this.deleted[i])) {
                        node.child = null
                    }
                }

            }
        }
        newGraph.root = this.root?.deepCopy() as RootNode?
        return newGraph.deepCopy()

    }

    fun turnEnterMonitorToSuspend(enterMonitorEvent: EnterMonitorEvent) {
        // Strat from the rootNode, find the child by the thread id of the enterMonitorEvent
        var node = this.root?.children?.get(enterMonitorEvent.tid)
        // traverse the child until the enterMonitorEvent is found
        while (node?.value != enterMonitorEvent) {
            node = node?.child
        }
        // create a new SuspendEvent with the same tid and serial number and monitor as the enterMonitorEvent
        val suspendEvent = SuspendEvent(
            tid = enterMonitorEvent.tid,
            serial = enterMonitorEvent.serial,
            monitor = enterMonitorEvent.monitor
        )
        // replace the enterMonitorEvent with the suspendEvent
        node.value = suspendEvent

        // In the graphEvents, fine the index of the enterMonitorEvent and replace it with the suspendEvent
        val index = this.graphEvents.indexOf(enterMonitorEvent)
        this.graphEvents[index] = suspendEvent

        // In the eventsOrder, fine the index of the enterMonitorEvent and replace it with the suspendEvent
        val index2 = this.eventsOrder.indexOf(enterMonitorEvent)
        this.eventsOrder[index2] = suspendEvent

        // for all pairs in the MCs set, if the second element is the enterMonitorEvent, replace it with the suspendEvent
        var firstEvent: Event?
        var matchpair = this.MCs.find { it.second == enterMonitorEvent }
        if (matchpair != null) {
            firstEvent = matchpair.first
            this.MCs.remove(matchpair)
            this.addMC(firstEvent, suspendEvent as Event)
        }
        var secondEvent: Event?
        matchpair = this.MCs.find { it.first == enterMonitorEvent }
        if (matchpair != null) {
            secondEvent = matchpair.second
            this.MCs.remove(matchpair)
            this.addMC(suspendEvent as Event, secondEvent)
        }
    }


    fun printEventsOrder() {
        for (e in eventsOrder) {
            println("The $e event has index of ${eventsOrder.indexOf(e)}")
        }
    }

    // prints the deleted events of the graph
    fun printDeletedEvents() {
        println("The deleted events are:")
        for (e in deleted) {
            println(e)
        }
    }

    fun printEvents() {
        for (e in this.graphEvents) {
            when (e.type) {
                EventType.READ -> {
                    val read: ReadEvent? = e as ReadEvent?
                    println(read)
                }

                EventType.WRITE -> {
                    val write: WriteEvent = e as WriteEvent
                    println(write)
                }

                EventType.RECEIVE -> {
                    val receive: ReceiveEvent? = e as ReceiveEvent?
                    println(receive)
                }

                EventType.BLOCKED_RECV -> {
                    val blockedReceive: BlockedRecvEvent? = e as BlockedRecvEvent?
                    println(blockedReceive)
                }

                EventType.UNBLOCKED_RECV -> {
                    val unblockedReceive: UnblockedRecvEvent? = e as UnblockedRecvEvent?
                    println(unblockedReceive)
                }

                EventType.BLOCK_RECV_REQ -> {
                    val blockReceiveRequest: BlockingRecvReq? = e as BlockingRecvReq?
                    println(blockReceiveRequest)
                }

                EventType.SEND -> {
                    val send: SendEvent? = e as SendEvent?
                    println(send)
                }

                EventType.INITIAL -> {
                    val init: InitializationEvent = e as InitializationEvent
                    println(init)
                }

                EventType.START -> {
                    val create: StartEvent = e as StartEvent
                    println(create)
                }

                EventType.JOIN -> {
                    val join: JoinEvent = e as JoinEvent
                    println(join)
                }

                EventType.FINISH -> {
                    val finish: FinishEvent = e as FinishEvent
                    println(finish)
                }

                EventType.ENTER_MONITOR -> {
                    val enter: EnterMonitorEvent = e as EnterMonitorEvent
                    println(enter)
                }

                EventType.EXIT_MONITOR -> {
                    val exit: ExitMonitorEvent = e as ExitMonitorEvent
                    println(exit)
                }

                EventType.DEADLOCK -> {
                    val deadlock: DeadlockEvent = e as DeadlockEvent
                    println(deadlock)
                }

                EventType.MONITOR_REQUEST -> {
                    val monitorRequestEvent: MonitorRequestEvent = e as MonitorRequestEvent
                    println(monitorRequestEvent)
                }

                EventType.FAILURE -> {
                    val failureEvent: FailureEvent = e as FailureEvent
                    println(failureEvent)
                }

                EventType.SUSPEND -> {
                    val suspendEvent: SuspendEvent = e as SuspendEvent
                    println(suspendEvent)
                }

                EventType.UNSUSPEND -> {
                    val unsuspendEvent: UnsuspendEvent = e as UnsuspendEvent
                    println(unsuspendEvent)
                }

                EventType.SYM_EXECUTION -> {
                    val symExecutionEvent: SymExecutionEvent = e as SymExecutionEvent
                    println(symExecutionEvent)
                }

                EventType.PARK -> {
                    val parkEvent: ParkEvent = e as ParkEvent
                    println(parkEvent)
                }

                EventType.UNPARK -> {
                    val unparkEvent: UnparkEvent = e as UnparkEvent
                    println(unparkEvent)
                }

                EventType.UNPARKING -> {
                    val unparkingEvent: UnparkingEvent = e as UnparkingEvent
                    println(unparkingEvent)
                }

                EventType.MAIN_START -> {
                    val mainStartEvent: MainStartEvent = e as MainStartEvent
                    println(mainStartEvent)
                }

                EventType.OTHER -> TODO()
            }
        }

    }

    fun printGraph() {
        println("------@@@----- Here is the execution graph ------@@@-----")
        println(root)
        for (i in root?.children!!.keys) {
            println("|")
            println("|")
            var next = root?.children!![i]
            while (next != null) {
                println(next)
                println("   |")
                println("   |")
                next = next.child
            }

        }
    }

    fun visualizeGraph(graphID: Int, path: String) {
        val dotFile = File("${path}Execution_Graph_${graphID}.dot")
        val fileWriter = FileWriter(dotFile)
        val bufferedWriter = BufferedWriter(fileWriter)
        bufferedWriter.write("digraph {")

        // This part prints the children of the root node
        for (i in root?.children!!.keys) {
            var next = root?.children!![i]
            visEvent(next?.value!!, bufferedWriter)
            visRootToEventEdge(next.value as ThreadEvent, bufferedWriter)
        }

        // This part prints each thread of the root's children
        for (i in root?.children!!.keys) {
            var event = root?.children!![i]?.value as ThreadEvent
            var next = root?.children!![i]?.child
            while (next != null) {
                visEvent(next.value, bufferedWriter)
                visEventToEventEdge(event, next.value as ThreadEvent, bufferedWriter)
                event = next.value as ThreadEvent
                next = next.child
            }
        }

        if (this.COs.isNotEmpty()) {
            visCOs(bufferedWriter)
        }

        if (this.STs.isNotEmpty()) {
            visSTs(bufferedWriter)
        }

        if (this.JTs.isNotEmpty()) {
            visJTs(bufferedWriter)
        }

        if (this.MCs.isNotEmpty()) {
            visMCs(bufferedWriter)
        }

        if (this.TCs.isNotEmpty()) {
            visTCs(bufferedWriter)
        }

        if (this.PCs.isNotEmpty()) {
            visPCs(bufferedWriter)
        }

        if (this.recvFrom.isNotEmpty()) {
            visRecvFrom(bufferedWriter)
        }

        bufferedWriter.newLine()
        bufferedWriter.write("}")
        bufferedWriter.close()

        dot2png(path, "Execution_Graph_${graphID}")
    }

    private fun visEvent(event: Event, bufferedWriter: BufferedWriter) {
        when (event.type) {
            EventType.READ -> {
                visReadEvent(event as ReadEvent, bufferedWriter)
                if (event.rf != null) {
                    visReadFromEdge(event, bufferedWriter)
                }
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

            EventType.OTHER -> TODO()
            EventType.INITIAL -> TODO()
        }
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
                    ".Sym Execution:${symExecution.formula}\"]"
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
        bufferedWriter.write("${blockedReceive.tid}${blockedReceive.serial} [label=\"${blockedReceive.tid}:${blockedReceive.serial}.BlockedRecv()\"]")
    }

    private fun visUnblockedReceiveEvent(unblockedReceive: UnblockedRecvEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${unblockedReceive.tid}${unblockedReceive.serial} [label=\"${unblockedReceive.tid}:${unblockedReceive.serial}.UnblkRcv()\"]")
    }

    private fun visBlockingReceiveRequestEvent(
        blockingReceiveRequest: BlockingRecvReq,
        bufferedWriter: BufferedWriter
    ) {
        bufferedWriter.newLine()
        bufferedWriter.write("${blockingReceiveRequest.tid}${blockingReceiveRequest.serial} [label=\"${blockingReceiveRequest.tid}:${blockingReceiveRequest.serial}.BlkRcvReq()\"]")
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

    private fun visRootToEventEdge(threadEvent: ThreadEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("root -> ${threadEvent.tid}${threadEvent.serial};")
    }

    private fun visEventToEventEdge(firstEvent: ThreadEvent, secondEvent: ThreadEvent, bufferedWriter: BufferedWriter) {
        bufferedWriter.newLine()
        bufferedWriter.write("${firstEvent.tid}${firstEvent.serial} -> ${secondEvent.tid}${secondEvent.serial};")
    }

    private fun visReadFromEdge(read: ReadEvent, bufferedWriter: BufferedWriter) {
        if (read.rf is WriteEvent) {
            val readFrom = read.rf as WriteEvent
            bufferedWriter.newLine()
            bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
        } else if (read.rf is InitializationEvent) {
            bufferedWriter.newLine()
            bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
        }
    }

    private fun visCOs(bufferedWriter: BufferedWriter) {
        for (i in 0 until this.COs.size) {
            if (this.COs[i].firstWrite is WriteEvent) {
                val firstTid = (this.COs[i].firstWrite as WriteEvent).tid
                val firstSerial = (this.COs[i].firstWrite as WriteEvent).serial
                val secondTid = this.COs[i].secondWrite.tid
                val secondSerial = this.COs[i].secondWrite.serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=blue, label=\"co\"];")
            } else {
                val secondTid = this.COs[i].secondWrite.tid
                val secondSerial = this.COs[i].secondWrite.serial
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${secondTid}${secondSerial}[color=blue, label=\"co\"];")
            }
        }
    }

    private fun visSTs(bufferedWriter: BufferedWriter) {
        for (i in this.STs.indices) {
            val firstTid = (this.STs.elementAt(i).first as ThreadEvent).tid
            val firstSerial = (this.STs.elementAt(i).first as ThreadEvent).serial
            val secondTid = (this.STs.elementAt(i).second as ThreadEvent).tid
            val secondSerial = (this.STs.elementAt(i).second as ThreadEvent).serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=green, label=\"st\"];")
        }
    }

    private fun visJTs(bufferedWriter: BufferedWriter) {
        for (i in this.JTs.indices) {
            val firstTid = (this.JTs.elementAt(i).first as ThreadEvent).tid
            val firstSerial = (this.JTs.elementAt(i).first as ThreadEvent).serial
            val secondTid = (this.JTs.elementAt(i).second as ThreadEvent).tid
            val secondSerial = (this.JTs.elementAt(i).second as ThreadEvent).serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=orange, label=\"jt\"];")
        }
    }

    private fun visMCs(bufferedWriter: BufferedWriter) {
        for (i in this.MCs.indices) {
            val firstTid = (this.MCs.elementAt(i).first as ThreadEvent).tid
            val firstSerial = (this.MCs.elementAt(i).first as ThreadEvent).serial
            val secondTid = (this.MCs.elementAt(i).second as ThreadEvent).tid
            val secondSerial = (this.MCs.elementAt(i).second as ThreadEvent).serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=darkorchid3, label=\"mc\"];")
        }
    }

    private fun visTCs(bufferedWriter: BufferedWriter) {
        for (i in this.TCs.indices) {
            val firstTid = (this.TCs.elementAt(i).first as ThreadEvent).tid
            val firstSerial = (this.TCs.elementAt(i).first as ThreadEvent).serial
            val secondTid = (this.TCs.elementAt(i).second as ThreadEvent).tid
            val secondSerial = (this.TCs.elementAt(i).second as ThreadEvent).serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=turquoise4, label=\"tc\"];")
        }
    }

    private fun visPCs(bufferedWriter: BufferedWriter) {
        for (i in this.PCs.indices) {
            val firstTid = (this.PCs.elementAt(i).first as ThreadEvent).tid
            val firstSerial = (this.PCs.elementAt(i).first as ThreadEvent).serial
            val secondTid = (this.PCs.elementAt(i).second as ThreadEvent).tid
            val secondSerial = (this.PCs.elementAt(i).second as ThreadEvent).serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=chartreuse, label=\"pc\"];")
        }
    }

    private fun visRecvFrom(bufferedWriter: BufferedWriter) {
        for (i in this.recvFrom.indices) {
            if (this.recvFrom.elementAt(i).first is InitializationEvent) {
                val secondTid = (this.recvFrom.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.recvFrom.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${secondTid}${secondSerial}[color=red, label=\"rf\"];")
                continue
            }
            val firstTid = (this.recvFrom.elementAt(i).first as ThreadEvent).tid
            val firstSerial = (this.recvFrom.elementAt(i).first as ThreadEvent).serial
            val secondTid = (this.recvFrom.elementAt(i).second as ThreadEvent).tid
            val secondSerial = (this.recvFrom.elementAt(i).second as ThreadEvent).serial
            bufferedWriter.newLine()
            bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=red, label=\"rf\"];")
        }
    }

    /*
     If you cannot read and understand the following code, please do not blame me :))
          Since, I was so exhausted when I was writing this code
     */
    fun disvisualizeGraph(graphID: Int, path: String) {
        val dotFile = File("${path}Execution_Graph_${graphID}.dot")
        val fileWriter = FileWriter(dotFile)
        val bufferedWriter = BufferedWriter(fileWriter)
        bufferedWriter.write("digraph {")

        // This part prints the children of the root node
        for (i in root?.children?.keys!!) {
            if (root?.children!![i]!!.value.type == EventType.WRITE) {
                val write = root?.children!![i]!!.value as WriteEvent
                var param: String
                if (write.loc?.instance == null) {
                    param = write.loc?.field?.name + " : ${write.loc?.type} "
                } else {
                    param = write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                            write.loc?.instance.hashCode().toString(16) + ":" +
                            write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                            " : ${write.loc?.type.toString().substringAfterLast('/')} "
                }
                bufferedWriter.newLine()
                bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${write.tid}${write.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.READ) {
                val read = root?.children!![i]!!.value as ReadEvent
                var param: String
                if (read.loc?.instance == null) {
                    param = read.loc?.field.toString() + " : ${read.loc?.type} "
                } else {
                    param = read.loc?.instance.toString().substringAfterLast('.')
                        .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                        .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                        ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                }
                bufferedWriter.newLine()
                bufferedWriter.write(
                    "${read.tid}${read.serial} [label=\"${read.tid}:" +
                            "${read.serial}.R(${param})\"]"
                )
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${read.tid}${read.serial};")
                if (read.rf != null) {
                    if (read.rf is WriteEvent) {
                        val readFrom = read.rf as WriteEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                    } else if (read.rf is InitializationEvent) {
                        bufferedWriter.newLine()
                        bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                    }
                }
            } else if (root?.children!![i]!!.value.type == EventType.START) {
                val create = root?.children!![i]!!.value as StartEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${create.tid}${create.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.JOIN) {
                val join = root?.children!![i]!!.value as JoinEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${join.tid}${join.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.FINISH) {
                val finish = root?.children!![i]!!.value as FinishEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${finish.tid}${finish.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.FAILURE) {
                val failure = root?.children!![i]!!.value as FailureEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${failure.tid}${failure.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.DEADLOCK) {
                val deadlock = root?.children!![i]!!.value as DeadlockEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${deadlock.tid}${deadlock.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.MONITOR_REQUEST) {
                val monitorRequest = root?.children!![i]!!.value as MonitorRequestEvent
                bufferedWriter.newLine()
                bufferedWriter.write(
                    "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                        monitorRequest.monitor.hashCode().toString(16)
                    }\"]"
                )
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${monitorRequest.tid}${monitorRequest.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.ENTER_MONITOR) {
                val enterMonitor = root?.children!![i]!!.value as EnterMonitorEvent
                bufferedWriter.newLine()
                bufferedWriter.write(
                    "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                        enterMonitor.monitor.hashCode().toString(16)
                    }\"]"
                )
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${enterMonitor.tid}${enterMonitor.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.EXIT_MONITOR) {
                val exitMonitor = root?.children!![i]!!.value as ExitMonitorEvent
                bufferedWriter.newLine()
                bufferedWriter.write(
                    "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                        exitMonitor.monitor.hashCode().toString(16)
                    }\"]"
                )
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${exitMonitor.tid}${exitMonitor.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.SUSPEND) {
                val suspend = root?.children!![i]!!.value as SuspendEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${suspend.tid}${suspend.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.UNSUSPEND) {
                val unsuspend = root?.children!![i]!!.value as UnsuspendEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${unsuspend.tid}${unsuspend.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.SYM_EXECUTION) {
                val symExecution = root?.children!![i]!!.value as SymExecutionEvent
                bufferedWriter.newLine()
                bufferedWriter.write(
                    "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                        symExecution.formula
                    }\"]"
                )
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${symExecution.tid}${symExecution.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.PARK) {
                val park = root?.children!![i]!!.value as ParkEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${park.tid}${park.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.UNPARK) {
                val unpark = root?.children!![i]!!.value as UnparkEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${unpark.tid}${unpark.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.UNPARKING) {
                val unparking = root?.children!![i]!!.value as UnparkingEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${unparking.tid}${unparking.serial};")
            }
        }

        // This part prints each thread of the root's children
        for (i in root?.children?.keys!!) {
            if (root?.children!![i]!!.value.type == EventType.READ) {
                val readParent = root?.children!![i]!!.value as ReadEvent
                var nextChild = root?.children!![i]!!.child
                var tid = readParent.tid
                var serial = readParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    //readParent = nextChild.value as ReadEvent
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.WRITE) {
                val writeParent = root?.children!![i]!!.value as WriteEvent
                var nextChild = root?.children!![i]!!.child
                var tid = writeParent.tid
                var serial = writeParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.START) {
                val createParent = root?.children!![i]!!.value as StartEvent
                var nextChild = root?.children!![i]!!.child
                var tid = createParent.tid
                var serial = createParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.JOIN) {
                val joinParent = root?.children!![i]!!.value as JoinEvent
                var nextChild = root?.children!![i]!!.child
                var tid = joinParent.tid
                var serial = joinParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    } else if (nextChild.value.type == EventType.BLOCKED_RECV) {
                        val blockedRecv = nextChild.value as BlockedRecvEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${blockedRecv.tid}${blockedRecv.serial} [label=\"${blockedRecv.tid}:${blockedRecv.serial}.Blocked Recv\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${blockedRecv.tid}${blockedRecv.serial};")
                        tid = blockedRecv.tid
                        serial = blockedRecv.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.MONITOR_REQUEST) {
                val monitorRequestParent = root?.children!![i]!!.value as MonitorRequestEvent
                var nextChild = root?.children!![i]!!.child
                var tid = monitorRequestParent.tid
                var serial = monitorRequestParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.ENTER_MONITOR) {
                val enterMonitorParent = root?.children!![i]!!.value as EnterMonitorEvent
                var nextChild = root?.children!![i]!!.child
                var tid = enterMonitorParent.tid
                var serial = enterMonitorParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.EXIT_MONITOR) {
                val exitMonitorParent = root?.children!![i]!!.value as ExitMonitorEvent
                var nextChild = root?.children!![i]!!.child
                var tid = exitMonitorParent.tid
                var serial = exitMonitorParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.SUSPEND) {
                val suspendParent = root?.children!![i]!!.value as SuspendEvent
                var nextChild = root?.children!![i]!!.child
                var tid = suspendParent.tid
                var serial = suspendParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.UNSUSPEND) {
                val unsuspendParent = root?.children!![i]!!.value as UnsuspendEvent
                var nextChild = root?.children!![i]!!.child
                var tid = unsuspendParent.tid
                var serial = unsuspendParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.SYM_EXECUTION) {
                val symExecutionParent = root?.children!![i]!!.value as SymExecutionEvent
                var nextChild = root?.children!![i]!!.child
                var tid = symExecutionParent.tid
                var serial = symExecutionParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.PARK) {
                val parkParent = root?.children!![i]!!.value as ParkEvent
                var nextChild = root?.children!![i]!!.child
                var tid = parkParent.tid
                var serial = parkParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.UNPARK) {
                val unparkParent = root?.children!![i]!!.value as UnparkEvent
                var nextChild = root?.children!![i]!!.child
                var tid = unparkParent.tid
                var serial = unparkParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.UNPARKING) {
                val unparkingParent = root?.children!![i]!!.value as UnparkingEvent
                var nextChild = root?.children!![i]!!.child
                var tid = unparkingParent.tid
                var serial = unparkingParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.UNPARK) {
                val unparkParent = root?.children!![i]!!.value as UnparkEvent
                var nextChild = root?.children!![i]!!.child
                var tid = unparkParent.tid
                var serial = unparkParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.UNPARKING) {
                val unparkingParent = root?.children!![i]!!.value as UnparkingEvent
                var nextChild = root?.children!![i]!!.child
                var tid = unparkingParent.tid
                var serial = unparkingParent.serial
                while (nextChild != null) {
                    if (nextChild.value.type == EventType.WRITE) {
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null) {
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else {
                            param =
                                write.loc?.instance.toString().substringAfterLast('.').substringBeforeLast('@') + "@" +
                                        write.loc?.instance.hashCode().toString(16) + ":" +
                                        write.loc?.field?.name + "@" + write.loc?.field?.hashCode()?.toString(16) +
                                        " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ) {
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null) {
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else {
                            param = read.loc?.instance.toString().substringAfterLast('.')
                                .substringBeforeLast('@') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + ":" + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if (read.rf != null) {
                            if (read.rf is WriteEvent) {
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent) {
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START) {
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN) {
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH) {
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    } else if (nextChild.value.type == EventType.FAILURE) {
                        val failure = nextChild.value as FailureEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${failure.tid}${failure.serial} [label=\"${failure.tid}:${failure.serial}.Thread Failure\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${failure.tid}${failure.serial};")
                        tid = failure.tid
                        serial = failure.serial
                    } else if (nextChild.value.type == EventType.DEADLOCK) {
                        val deadlock = nextChild.value as DeadlockEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${deadlock.tid}${deadlock.serial} [label=\"${deadlock.tid}:${deadlock.serial}.Deadlock\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${deadlock.tid}${deadlock.serial};")
                        tid = deadlock.tid
                        serial = deadlock.serial
                    } else if (nextChild.value.type == EventType.MONITOR_REQUEST) {
                        val monitorRequest = nextChild.value as MonitorRequestEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${monitorRequest.tid}${monitorRequest.serial} [label=\"${monitorRequest.tid}:${monitorRequest.serial}.Monitor Request@${
                                monitorRequest.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${monitorRequest.tid}${monitorRequest.serial};")
                        tid = monitorRequest.tid
                        serial = monitorRequest.serial
                    } else if (nextChild.value.type == EventType.ENTER_MONITOR) {
                        val enterMonitor = nextChild.value as EnterMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${enterMonitor.tid}${enterMonitor.serial} [label=\"${enterMonitor.tid}:${enterMonitor.serial}.Enter Monitor@${
                                enterMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${enterMonitor.tid}${enterMonitor.serial};")
                        tid = enterMonitor.tid
                        serial = enterMonitor.serial
                    } else if (nextChild.value.type == EventType.EXIT_MONITOR) {
                        val exitMonitor = nextChild.value as ExitMonitorEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${exitMonitor.tid}${exitMonitor.serial} [label=\"${exitMonitor.tid}:${exitMonitor.serial}.Exit Monitor@${
                                exitMonitor.monitor.hashCode().toString(16)
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${exitMonitor.tid}${exitMonitor.serial};")
                        tid = exitMonitor.tid
                        serial = exitMonitor.serial
                    } else if (nextChild.value.type == EventType.SUSPEND) {
                        val suspend = nextChild.value as SuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${suspend.tid}${suspend.serial} [label=\"${suspend.tid}:${suspend.serial}.Suspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${suspend.tid}${suspend.serial};")
                        tid = suspend.tid
                        serial = suspend.serial
                    } else if (nextChild.value.type == EventType.UNSUSPEND) {
                        val unsuspend = nextChild.value as UnsuspendEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unsuspend.tid}${unsuspend.serial} [label=\"${unsuspend.tid}:${unsuspend.serial}.Unsuspend\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unsuspend.tid}${unsuspend.serial};")
                        tid = unsuspend.tid
                        serial = unsuspend.serial
                    } else if (nextChild.value.type == EventType.SYM_EXECUTION) {
                        val symExecution = nextChild.value as SymExecutionEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write(
                            "${symExecution.tid}${symExecution.serial} [label=\"${symExecution.tid}:${symExecution.serial}.Sym Execution:${
                                symExecution.formula
                            }\"]"
                        )
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${symExecution.tid}${symExecution.serial};")
                        tid = symExecution.tid
                        serial = symExecution.serial
                    } else if (nextChild.value.type == EventType.PARK) {
                        val park = nextChild.value as ParkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${park.tid}${park.serial} [label=\"${park.tid}:${park.serial}.Park\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${park.tid}${park.serial};")
                        tid = park.tid
                        serial = park.serial
                    } else if (nextChild.value.type == EventType.UNPARK) {
                        val unpark = nextChild.value as UnparkEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unpark.tid}${unpark.serial} [label=\"${unpark.tid}:${unpark.serial}.Unpark\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unpark.tid}${unpark.serial};")
                        tid = unpark.tid
                        serial = unpark.serial
                    } else if (nextChild.value.type == EventType.UNPARKING) {
                        val unparking = nextChild.value as UnparkingEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${unparking.tid}${unparking.serial} [label=\"${unparking.tid}:${unparking.serial}.Unparking\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${unparking.tid}${unparking.serial};")
                        tid = unparking.tid
                        serial = unparking.serial
                    }
                    nextChild = nextChild.child
                }
            }
        }

        // This part prints the CO edges
        if (this.COs.isNotEmpty()) {
            for (i in 0 until this.COs.size) {
                if (this.COs[i].firstWrite is WriteEvent) {
                    val firstTid = (this.COs[i].firstWrite as WriteEvent).tid
                    val firstSerial = (this.COs[i].firstWrite as WriteEvent).serial
                    val secondTid = this.COs[i].secondWrite.tid
                    val secondSerial = this.COs[i].secondWrite.serial
                    bufferedWriter.newLine()
                    bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=blue, label=\"co\"];")
                } else {
                    val secondTid = this.COs[i].secondWrite.tid
                    val secondSerial = this.COs[i].secondWrite.serial
                    bufferedWriter.newLine()
                    bufferedWriter.write("root -> ${secondTid}${secondSerial}[color=blue, label=\"co\"];")
                }

            }
        }

        // This part prints the ST edges
        if (this.STs.isNotEmpty()) {
            for (i in this.STs.indices) {
                val firstTid = (this.STs.elementAt(i).first as ThreadEvent).tid
                val firstSerial = (this.STs.elementAt(i).first as ThreadEvent).serial
                val secondTid = (this.STs.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.STs.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=green, label=\"st\"];")
            }
        }

        // This part prints the JT edges
        if (this.JTs.isNotEmpty()) {
            for (i in this.JTs.indices) {
                val firstTid = (this.JTs.elementAt(i).first as ThreadEvent).tid
                val firstSerial = (this.JTs.elementAt(i).first as ThreadEvent).serial
                val secondTid = (this.JTs.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.JTs.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=orange, label=\"jt\"];")
            }
        }

        // This part prints the MC edges
        if (this.MCs.isNotEmpty()) {
            for (i in this.MCs.indices) {
                val firstTid = (this.MCs.elementAt(i).first as ThreadEvent).tid
                val firstSerial = (this.MCs.elementAt(i).first as ThreadEvent).serial
                val secondTid = (this.MCs.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.MCs.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=darkorchid3, label=\"mc\"];")
            }
        }

        // This part prints the TC edges
        if (this.TCs.isNotEmpty()) {
            for (i in this.TCs.indices) {
                val firstTid = (this.TCs.elementAt(i).first as ThreadEvent).tid
                val firstSerial = (this.TCs.elementAt(i).first as ThreadEvent).serial
                val secondTid = (this.TCs.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.TCs.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=turquoise4, label=\"tc\"];")
            }
        }

        // This part prints the PC edges
        if (this.PCs.isNotEmpty()) {
            for (i in this.PCs.indices) {
                val firstTid = (this.PCs.elementAt(i).first as ThreadEvent).tid
                val firstSerial = (this.PCs.elementAt(i).first as ThreadEvent).serial
                val secondTid = (this.PCs.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.PCs.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=chartreuse, label=\"pc\"];")
            }
        }

        bufferedWriter.newLine()
        bufferedWriter.write("}")
        bufferedWriter.close()

        dot2png(path, "Execution_Graph_${graphID}")
    }

    /*
     When you make a deepCopy from a graph, the reference dependencies between events
        will be preserved within the ExecutionGraph object
     */
    fun deepCopy(): ExecutionGraph {
        val newExecutionGraph = ExecutionGraph(
            root = null,
            graphEvents = mutableListOf(),
            eventsOrder = mutableListOf(),
            COs = mutableListOf(),
            porf = mutableSetOf(),
            sc = mutableSetOf(),
            deleted = mutableListOf(),
            previous = mutableListOf(),
            JTs = mutableSetOf(),
            STs = mutableSetOf(),
            MCs = mutableSetOf(),
            TCs = mutableSetOf(),
            PCs = mutableSetOf(),
            recvFrom = mutableSetOf(),
            id = this.id
        )
        for (i in 0 until this.graphEvents.size) {
            newExecutionGraph.graphEvents.add(this.graphEvents[i].deepCopy())
        }

        for (i in 0 until this.eventsOrder.size) {
            newExecutionGraph.eventsOrder.add(
                newExecutionGraph.graphEvents.find { it.equals(this.eventsOrder[i]) }!!
            )
        }
        for (i in 0 until this.COs.size) {
            if (this.COs[i].firstWrite is WriteEvent) {
                newExecutionGraph.COs.add(
                    CO(newExecutionGraph.graphEvents.find { it.equals(this.COs[i].firstWrite) } as WriteEvent,
                        newExecutionGraph.graphEvents.find { it.equals(this.COs[i].secondWrite) } as WriteEvent)
                )
            } else {
                newExecutionGraph.COs.add(
                    CO(newExecutionGraph.graphEvents.find { it.equals(this.COs[i].firstWrite) } as InitializationEvent,
                        newExecutionGraph.graphEvents.find { it.equals(this.COs[i].secondWrite) } as WriteEvent)
                )
            }

        }

        for (i in this.STs.indices) {
            newExecutionGraph.STs.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.STs.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.STs.elementAt(i).second) }!!
                )
            )
        }

        for (i in this.JTs.indices) {
            newExecutionGraph.JTs.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.JTs.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.JTs.elementAt(i).second) }!!
                )
            )
        }

        for (i in this.MCs.indices) {
            //println("MCs: ${this.MCs.elementAt(i).first} ${this.MCs.elementAt(i).second}")
            //this.printEvents()
            newExecutionGraph.MCs.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.MCs.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.MCs.elementAt(i).second) }!!
                )
            )
        }

        for (i in this.TCs.indices) {
            newExecutionGraph.TCs.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.TCs.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.TCs.elementAt(i).second) }!!
                )
            )
        }

        for (i in this.recvFrom.indices) {
            newExecutionGraph.recvFrom.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.recvFrom.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.recvFrom.elementAt(i).second) }!!
                )
            )
        }

        for (i in this.PCs.indices) {
            newExecutionGraph.PCs.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.PCs.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.PCs.elementAt(i).second) }!!
                )
            )
        }

        for (i in this.porf.indices) {
            newExecutionGraph.porf.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.porf.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.porf.elementAt(i).second) }!!
                )
            )
        }
        for (i in this.sc.indices) {
            newExecutionGraph.sc.add(
                Pair(
                    newExecutionGraph.graphEvents.find { it.equals(this.sc.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.sc.elementAt(i).second) }!!
                )
            )
        }
        for (i in 0 until this.deleted.size) {
            newExecutionGraph.deleted.add(
                newExecutionGraph.graphEvents.find { it.equals(this.deleted[i]) }!!
            )
        }
        for (i in 0 until this.previous.size) {
            newExecutionGraph.previous.add(
                newExecutionGraph.graphEvents.find { it.equals(this.previous[i]) }!!
            )
        }

        newExecutionGraph.root = RootNode(
            newExecutionGraph.graphEvents.find { it.equals(this.root?.value) }!!
        )

        for (i in this.root?.children?.keys!!) {
            newExecutionGraph.root?.children!!.put(
                i,
                EventNode(
                    newExecutionGraph.graphEvents.find { it.equals(this.root?.children!![i]!!.value) }!!,
                    null
                )
            )
            var node = this.root?.children!![i]!!
            var copyNode = newExecutionGraph.root?.children!![i]!!
            while (node.child != null) {
                copyNode.child = EventNode(
                    newExecutionGraph.graphEvents.find { it.equals(node.child!!.value) }!!,
                    null
                )
                node = node.child!!
                copyNode = copyNode.child!!
            }

        }

        return newExecutionGraph

    }

    /*
     When you make a deepestCopy from a graph, the reference dependencies between
        events will "NOT" be preserved within the ExecutionGraph object
     */
    fun deepestCopy(): ExecutionGraph {
        val newExecutionGraph = ExecutionGraph(
            root = (this.root?.deepCopy()) as RootNode,
            graphEvents = mutableListOf(),
            eventsOrder = mutableListOf(),
            COs = mutableListOf(),
            porf = mutableSetOf(),
            sc = mutableSetOf(),
            deleted = mutableListOf(),
            previous = mutableListOf(),
            JTs = mutableSetOf(),
            STs = mutableSetOf(),
            MCs = mutableSetOf(),
            TCs = mutableSetOf(),
            PCs = mutableSetOf(),
            recvFrom = mutableSetOf(),
            id = this.id
        )
        for (i in 0 until this.graphEvents.size) {
            newExecutionGraph.graphEvents.add(this.graphEvents[i].deepCopy())
        }
        for (i in 0 until this.eventsOrder.size) {
            newExecutionGraph.eventsOrder.add(this.eventsOrder[i].deepCopy())
        }
        for (i in 0 until this.COs.size) {
            newExecutionGraph.COs.add(this.COs[i].deepCopy())
        }
        for (i in this.STs.indices) {
            newExecutionGraph.STs.add(
                Pair(
                    this.STs.elementAt(i).first.deepCopy(),
                    this.STs.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in this.JTs.indices) {
            newExecutionGraph.JTs.add(
                Pair(
                    this.JTs.elementAt(i).first.deepCopy(),
                    this.JTs.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in this.MCs.indices) {
            newExecutionGraph.MCs.add(
                Pair(
                    this.MCs.elementAt(i).first.deepCopy(),
                    this.MCs.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in this.TCs.indices) {
            newExecutionGraph.TCs.add(
                Pair(
                    this.TCs.elementAt(i).first.deepCopy(),
                    this.TCs.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in this.PCs.indices) {
            newExecutionGraph.PCs.add(
                Pair(
                    this.PCs.elementAt(i).first.deepCopy(),
                    this.PCs.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in this.porf.indices) {
            newExecutionGraph.porf.add(
                Pair(
                    this.porf.elementAt(i).first.deepCopy(),
                    this.porf.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in this.sc.indices) {
            newExecutionGraph.sc.add(
                Pair(
                    this.sc.elementAt(i).first.deepCopy(),
                    this.sc.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in this.recvFrom.indices) {
            newExecutionGraph.recvFrom.add(
                Pair(
                    this.recvFrom.elementAt(i).first.deepCopy(),
                    this.recvFrom.elementAt(i).second.deepCopy()
                )
            )
        }
        for (i in 0 until this.deleted.size) {
            newExecutionGraph.deleted.add(this.deleted[i].deepCopy())
        }
        for (i in 0 until this.previous.size) {
            newExecutionGraph.previous.add(this.previous[i].deepCopy())
        }

        return newExecutionGraph
    }
    /*
     You may wonder that why I implemented deepCopy and deepestCopy techniques for copying graphs!
     Long and tedious story... But, there was an alternative for this approach.
     The following implementation was the easiest way for us,
     However, unfortunately it does not support interfaces
     :(

        fun deepCopy() : ExecutionGraph{
            return Gson().fromJson(Gson().toJson(this), this.javaClass)
        }
     */

    private fun dot2png(dotPath: String, dotName: String) {
        val processBuilder =
            ProcessBuilder("dot", "-Tpng", "-o", "${dotPath}/${dotName}.png", "${dotPath}/${dotName}.dot")
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        process.waitFor()
    }
}