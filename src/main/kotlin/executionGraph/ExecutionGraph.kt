package executionGraph

// import com.google.gson.Gson
import programStructure.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.Serializable

/*
 Here the 'GraphEvents' class property represents the 'G.E' in Trust paper
 The 'eventOrder' represents $\leq_G$ on G.E
 */

data class ExecutionGraph(var root : RootNode? = null,
                          var graphEvents : MutableList<Event> = mutableListOf(),
                          var eventsOrder : MutableList<Event> = mutableListOf(),
                          var COs : MutableList<CO> = mutableListOf(),
                          var STs : MutableSet<Pair<Event,Event>> = mutableSetOf(),
                          var JTs : MutableSet<Pair<Event,Event>> = mutableSetOf(),
                          var porf : MutableSet<Pair<Event,Event>> = mutableSetOf(),
                          var sc : MutableSet<Pair<Event,Event>> = mutableSetOf(),
                          var deleted : MutableList<Event> = mutableListOf(),
                          var previous : MutableList<Event> = mutableListOf(),
                          var id : Int = 0
) : Serializable {

    fun addEvent(event : Event) {
        if (event !in graphEvents) {
            graphEvents.add(event)
            if (event !in eventsOrder) {
                eventsOrder.add(event)
                if (event.type != EventType.INITIAL) {
                    val threadEvent = event as ThreadEvent
                    if (root?.children?.keys?.contains(threadEvent.tid) == true) {
                        var nextNode = root?.children!![threadEvent.tid]
                        while (nextNode?.child != null) {
                            nextNode = nextNode.child
                        }
                        nextNode?.child = EventNode(threadEvent)
                        /*
                         for debugging

                         println("Reached Inside if: $event")
                         println("Reached Inside if: ${nextNode?.child}")
                         */
                    }else {
                        root?.children?.put(threadEvent.tid,EventNode(threadEvent))
                        /*
                         for debugging
                         println("Reached Inside else: $event")
                         */
                    }
                }
            }
        }
        /*
         for debugging
         println("Reached Here : $event")
         printEvents()
         printEventsOrder()
         */

    }

    fun addST(firstEvent: Event, secondEvent: Event) {
        STs.add(Pair(firstEvent,secondEvent))
    }

    fun addJT(firstEvent: Event, secondEvent: Event) {
        JTs.add(Pair(firstEvent,secondEvent))
    }

    fun addRoot(event: Event) {
        if (root == null) {
            root = RootNode(event)
            graphEvents.add(event)
            eventsOrder.add(event)
        }
    }

    fun computeSc() {
        // First, computing the sc of graph
        this.sc = mutableSetOf()

        // Adding pairs of initialization event to each graph event
        for (i in 1..<this.graphEvents.size) {
            this.porf.add(Pair(this.graphEvents[0],this.graphEvents[i]))
        }

        // The whole following part computes the po, rf, and fr relations
        for (i in this.root?.children!!.keys) {
            var node = this.root?.children!![i]!!
            // Adding rf
            if (node.value is ReadEvent) {
                val read = node.value as ReadEvent
                if (read.rf != null) {
                    this.sc.add(Pair(read.rf as Event , read as Event))

                    //Adding fr = rf^{-1} ; co
                    // rf^{-1} = Pair(read,read.rf)
                    for (j in 0..(this.COs.size-1)) {
                        if (read.rf!!.equals(this.COs[j].firstWrite as Event)) {
                            this.sc.add(Pair(read,this.COs[j].secondWrite as Event))
                        }
                    }
                }
            }

            // Adding po
            var next = node.child
            while (next != null) {
                this.sc.add(Pair(node.value ,next.value))
                node = next
                next = node.child
                // Adding rf
                if (node.value is ReadEvent) {
                    val read = node.value as ReadEvent
                    if (read.rf != null) {
                        this.sc.add(Pair(read.rf as Event , read as Event))

                        //Adding fr = rf^{-1} ; co
                        // rf^{-1} = Pair(read,read.rf)
                        for (j in 0..(this.COs.size-1)) {
                            if (read.rf!!.equals(this.COs[j].firstWrite as Event)) {
                                this.sc.add(Pair(read,this.COs[j].secondWrite as Event))
                            }
                        }
                    }
                }
            }
        }

        //This part adds the co edges
        for (i in 0..(this.COs.size-1)) {
            this.sc.add(Pair(this.COs[i].firstWrite as Event,this.COs[i].secondWrite as Event))
        }

        // This part adds the st edges
        for (i in this.STs.indices) {
            this.sc.add(Pair(this.STs.elementAt(i).first,this.STs.elementAt(i).second))
        }

        // This part adds the jt edges
        for (i in this.JTs.indices) {
            this.sc.add(Pair(this.JTs.elementAt(i).first,this.JTs.elementAt(i).second))
        }

        // Next, computing the transitive closure of sc relation
        var addedNewPairs = true
        while (addedNewPairs) {
            addedNewPairs = false
            for (pair in this.sc.toList()) {
                val (a,b) = pair
                for (otherPair in this.sc.toList()) {
                    val (c,d) = otherPair
                    if (b.equals(c) && !this.sc.contains(Pair(a,d))) {
                        this.sc.add(Pair(a,d))
                        addedNewPairs = true
                    }
                }
            }
        }
    }

    fun computePorf() {

        // To make sure that a new porf is constructed
        this.porf = mutableSetOf()

        for (i in 1..< this.graphEvents.size) {
            this.porf.add(Pair(this.graphEvents[0],this.graphEvents[i]))
        }

        // This part computes the primitives porf elements
        for (i in this.root?.children!!.keys) {
            var node = this.root?.children!![i]!!
            if (node.value is ReadEvent) {
                val read = node.value as ReadEvent
                if (read.rf != null){
                    this.porf.add(Pair(read.rf as Event , read as Event))
                }
            }
            var next = node.child
            while (next != null) {
                this.porf.add(Pair(node.value ,next.value))
                node = next
                next = node.child
                if (node.value is ReadEvent) {
                    val read = node.value as ReadEvent
                    if (read.rf != null) {
                        this.porf.add(Pair(read.rf as Event , read as Event))
                    }
                }
            }
        }

        // this part computes the complete transitive closure of porf
        var addedNewPairs = true
        while (addedNewPairs) {
            addedNewPairs = false
            for (pair in this.porf.toList()) {
                val (a,b) = pair
                for (otherPair in this.porf.toList()) {
                    val (c,d) = otherPair
                    if (b.equals(c) && !this.porf.contains(Pair(a,d))) {
                        this.porf.add(Pair(a,d))
                        addedNewPairs = true
                    }
                }
            }
        }
    }

    fun computeDeleted(pivotEvent: Event,writeEvent: Event) {
        // To make sure that a new Porf is constructed
        this.deleted = mutableListOf()

        val index = this.eventsOrder.indexOf(pivotEvent)
        for (i in index+1 until this.eventsOrder.size - 1)
            if (!this.porf.contains(Pair(this.eventsOrder[i] , writeEvent)))
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
        println("Transitive Closure:")
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

    fun computePrevious(firstEvent: Event,secondEvent: Event) {

        // To make sure that a new previous is constructed
        this.previous = mutableListOf()

        val index = this.eventsOrder.indexOf(firstEvent)
        for (i in 0 until this.graphEvents.size) {
            if(this.porf.contains(Pair(this.graphEvents[i],secondEvent))) {
                this.previous.add(this.graphEvents[i])
            }else if (this.eventsOrder.indexOf(this.graphEvents[i]) <= index) {
                this.previous.add(this.graphEvents[i])
            }
        }
    }

    fun restrictingGraph() : ExecutionGraph {
        val newGraph = ExecutionGraph()

        for (i in 0 until this.graphEvents.size) {
            if (!this.deleted.contains(this.graphEvents[i]))
                newGraph.graphEvents.add(this.graphEvents[i].deepCopy())
        }

        for (i in 0 until this.eventsOrder.size) {
            if (!this.deleted.contains(this.eventsOrder[i]))
                newGraph.eventsOrder.add(this.eventsOrder[i].deepCopy())
        }

        for (i in 0 until this.COs.size) {
            if(!this.deleted.contains(this.COs[i].secondWrite)) {
                if(this.COs[i].firstWrite is InitializationEvent) {
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
                !this.deleted.contains(this.STs.elementAt(i).second)) {
                newGraph.STs.add(Pair(this.STs.elementAt(i).first.deepCopy(),this.STs.elementAt(i).second.deepCopy()))
            }
        }

        for (i in 0 until this.JTs.size) {
            if (!this.deleted.contains(this.JTs.elementAt(i).first) &&
                !this.deleted.contains(this.JTs.elementAt(i).second)) {
                newGraph.JTs.add(Pair(this.JTs.elementAt(i).first.deepCopy(),this.JTs.elementAt(i).second.deepCopy()))
            }
        }

        for (i in 0 until this.deleted.size) {
            val threadEvent = this.deleted[i] as ThreadEvent
            if (this.root?.children?.keys?.contains(threadEvent.tid) == true) {
                if(root?.children!![threadEvent.tid]?.value!!.equals(this.deleted[i])) {
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
                EventType.INITIAL -> {
                    val init : InitializationEvent = e as InitializationEvent
                    println(init)
                }
                EventType.START -> {
                    val create : StartEvent = e as StartEvent
                    println(create)
                }
                EventType.JOIN -> {
                    val join : JoinEvent = e as JoinEvent
                    println(join)
                }
                EventType.FINISH -> {
                    val finish : FinishEvent = e as FinishEvent
                    println(finish)
                }
                EventType.ENTER_MONITOR -> {
                    val enter : EnterMonitorEvent = e as EnterMonitorEvent
                    println(enter)
                }
                EventType.EXIT_MONITOR -> {
                    val exit : ExitMonitorEvent = e as ExitMonitorEvent
                    println(exit)
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

    /*
     If you cannot read and understand the following code, please do not blame me :))
          Since, I was so exhausted when I was writing this code
     */
    fun visualizeGraph(graphID : Int) {
        val dotFile = File("src/main/resources/Visualized_Graphs/Execution_Graph_${graphID}.dot")
        val fileWriter = FileWriter(dotFile)
        val bufferedWriter = BufferedWriter(fileWriter)
        bufferedWriter.write("digraph {")

        // This part prints the children of the root node
        for (i in root?.children?.keys!!) {
            if(root?.children!![i]!!.value.type == EventType.WRITE) {
                val write = root?.children!![i]!!.value as WriteEvent
                var param: String
                if (write.loc?.instance == null) {
                    param = write.loc?.field?.name + " : ${write.loc?.type} "
                } else {
                    param = write.loc?.instance.toString().substringAfterLast('.') +"@" +
                            write.loc?.instance.hashCode().toString(16) +"." +
                            write.loc?.field?.name+ "@" + write.loc?.field?.hashCode()?.toString(16) +
                            " : ${write.loc?.type.toString().substringAfterLast('/')} "
                }
                bufferedWriter.newLine()
                bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${write.tid}${write.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.READ){
                val read = root?.children!![i]!!.value as ReadEvent
                var param: String
                if (read.loc?.instance == null){
                    param = read.loc?.field.toString() + " : ${read.loc?.type} "
                } else{
                    param = read.loc?.instance.toString().substringAfterLast('.')+"@"+ read.loc?.instance.hashCode().toString(16) +"." + read.loc?.field?.name+ "@" + read.loc?.field?.hashCode()?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                }
                bufferedWriter.newLine()
                bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:" +
                        "${read.serial}.R(${param})\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${read.tid}${read.serial};")
                if(read.rf != null){
                    if(read.rf is WriteEvent){
                        val readFrom = read.rf as WriteEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                    } else if (read.rf is InitializationEvent){
                        bufferedWriter.newLine()
                        bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                    }
                }
            } else if (root?.children!![i]!!.value.type == EventType.START){
                val create = root?.children!![i]!!.value as StartEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${create.tid}${create.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.JOIN){
                val join = root?.children!![i]!!.value as JoinEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${join.tid}${join.serial};")
            } else if (root?.children!![i]!!.value.type == EventType.FINISH){
                val finish = root?.children!![i]!!.value as FinishEvent
                bufferedWriter.newLine()
                bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                bufferedWriter.newLine()
                bufferedWriter.write("root -> ${finish.tid}${finish.serial};")
            }

        }

        // This part prints each thread of the root's children
        for (i in root?.children?.keys!!){
            if(root?.children!![i]!!.value.type == EventType.READ){
                val readParent = root?.children!![i]!!.value as ReadEvent
                var nextChild = root?.children!![i]!!.child
                var tid = readParent.tid
                var serial = readParent.serial
                while (nextChild != null){
                    if(nextChild.value.type == EventType.WRITE){
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null){
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else{
                            param = write.loc?.instance.toString().substringAfterLast('.')+"@"+ write.loc?.instance.hashCode().toString(16) +"." + write.loc?.field?.name+ "@" + write.loc?.field?.hashCode()?.toString(16) + " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ){
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null){
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else{
                            param = read.loc?.instance.toString().substringAfterLast('.')+"@"+ read.loc?.instance.hashCode().toString(16) +"." + read.loc?.field?.name+ "@" + read.loc?.field?.hashCode()?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if(read.rf != null){
                            if(read.rf is WriteEvent){
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent){
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START){
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN){
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH){
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    }
                    //readParent = nextChild.value as ReadEvent
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.WRITE){
                val writeParent = root?.children!![i]!!.value as WriteEvent
                var nextChild = root?.children!![i]!!.child
                var tid = writeParent.tid
                var serial = writeParent.serial
                while (nextChild != null){
                    if(nextChild.value.type == EventType.WRITE){
                        val write = nextChild.value as WriteEvent
                        var param: String
                        if (write.loc?.instance == null){
                            param = write.loc?.field?.name + " : ${write.loc?.type} "
                        } else{
                            param = write.loc?.instance.toString().substringAfterLast('.')+"@"+ write.loc?.instance.hashCode().toString(16) +"." + write.loc?.field?.name+ "@" + write.loc?.field?.hashCode()?.toString(16) + " : ${write.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${write.tid}${write.serial} [label=\"${write.tid}:${write.serial}.W(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${write.tid}${write.serial};")
                        tid = write.tid
                        serial = write.serial
                    } else if (nextChild.value.type == EventType.READ){
                        val read = nextChild.value as ReadEvent
                        var param: String
                        if (read.loc?.instance == null){
                            param = read.loc?.field?.name + " : ${read.loc?.type} "
                        } else{
                            param = read.loc?.instance.toString().substringAfterLast('.')+"@"+ read.loc?.instance.hashCode().toString(16) +"." + read.loc?.field?.name+ "@" + read.loc?.field?.hashCode()?.toString(16) + " : ${read.loc?.type.toString().substringAfterLast('/')} "
                        }
                        bufferedWriter.newLine()
                        bufferedWriter.write("${read.tid}${read.serial} [label=\"${read.tid}:${read.serial}.R(${param})\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${read.tid}${read.serial};")
                        if(read.rf != null){
                            if(read.rf is WriteEvent){
                                val readFrom = read.rf as WriteEvent
                                bufferedWriter.newLine()
                                bufferedWriter.write("${readFrom.tid}${readFrom.serial} -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            } else if (read.rf is InitializationEvent){
                                bufferedWriter.newLine()
                                bufferedWriter.write("root -> ${read.tid}${read.serial}[color=red, label=\"rf\"];")
                            }
                        }
                        tid = read.tid
                        serial = read.serial
                    } else if (nextChild.value.type == EventType.START){
                        val create = nextChild.value as StartEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${create.tid}${create.serial} [label=\"${create.tid}:${create.serial}.Thread Started\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${create.tid}${create.serial};")
                        tid = create.tid
                        serial = create.serial
                    } else if (nextChild.value.type == EventType.JOIN){
                        val join = nextChild.value as JoinEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${join.tid}${join.serial} [label=\"${join.tid}:${join.serial}.Thread Joined thread-${join.joinTid}\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${join.tid}${join.serial};")
                        tid = join.tid
                        serial = join.serial
                    } else if (nextChild.value.type == EventType.FINISH){
                        val finish = nextChild.value as FinishEvent
                        bufferedWriter.newLine()
                        bufferedWriter.write("${finish.tid}${finish.serial} [label=\"${finish.tid}:${finish.serial}.Thread Finished\"]")
                        bufferedWriter.newLine()
                        bufferedWriter.write("${tid}${serial} -> ${finish.tid}${finish.serial};")
                        tid = finish.tid
                        serial = finish.serial
                    }
                    nextChild = nextChild.child
                }
            } else if (root?.children!![i]!!.value.type == EventType.START){
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
                            param = write.loc?.instance.toString()
                                .substringAfterLast('.') + "@" + write.loc?.instance.hashCode()
                                .toString(16) + "." + write.loc?.field?.name+ "@" + write.loc?.field?.hashCode()?.toString(16) + " : ${
                                write.loc?.type.toString().substringAfterLast('/')
                            } "
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
                            param = read.loc?.instance.toString()
                                .substringAfterLast('.') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + "." + read.loc?.field?.name+ "@" + read.loc?.field?.hashCode()?.toString(16) + " : ${
                                read.loc?.type.toString().substringAfterLast('/')
                            } "
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
                            param = write.loc?.instance.toString()
                                .substringAfterLast('.') + "@" + write.loc?.instance.hashCode()
                                .toString(16) + "." + write.loc?.field?.name + "@" + write.loc?.field?.hashCode()
                                ?.toString(16) + " : ${
                                write.loc?.type.toString().substringAfterLast('/')
                            } "
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
                            param = read.loc?.instance.toString()
                                .substringAfterLast('.') + "@" + read.loc?.instance.hashCode()
                                .toString(16) + "." + read.loc?.field?.name + "@" + read.loc?.field?.hashCode()
                                ?.toString(16) + " : ${
                                read.loc?.type.toString().substringAfterLast('/')
                            } "
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
                    }
                    nextChild = nextChild.child
                }
            }
        }

        // This part prints the CO edges
        if(this.COs.isNotEmpty()){
            for (i in 0 until this.COs.size){
                if (this.COs[i].firstWrite is WriteEvent){
                    val firstTid = (this.COs[i].firstWrite as WriteEvent).tid
                    val firstSerial = (this.COs[i].firstWrite as WriteEvent).serial
                    val secondTid = this.COs[i].secondWrite.tid
                    val secondSerial = this.COs[i].secondWrite.serial
                    bufferedWriter.newLine()
                    bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=blue, label=\"co\"];")
                }else{
                    val secondTid = this.COs[i].secondWrite.tid
                    val secondSerial = this.COs[i].secondWrite.serial
                    bufferedWriter.newLine()
                    bufferedWriter.write("root -> ${secondTid}${secondSerial}[color=blue, label=\"co\"];")
                }

            }
        }

        // This part prints the ST edges
        if(this.STs.isNotEmpty()){
            for (i in this.STs.indices){
                val firstTid = (this.STs.elementAt(i).first as ThreadEvent).tid
                val firstSerial = (this.STs.elementAt(i).first as ThreadEvent).serial
                val secondTid = (this.STs.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.STs.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=green, label=\"st\"];")
            }
        }

        // This part prints the JT edges
        if(this.JTs.isNotEmpty()){
            for (i in this.JTs.indices){
                val firstTid = (this.JTs.elementAt(i).first as ThreadEvent).tid
                val firstSerial = (this.JTs.elementAt(i).first as ThreadEvent).serial
                val secondTid = (this.JTs.elementAt(i).second as ThreadEvent).tid
                val secondSerial = (this.JTs.elementAt(i).second as ThreadEvent).serial
                bufferedWriter.newLine()
                bufferedWriter.write("${firstTid}${firstSerial} -> ${secondTid}${secondSerial}[color=orange, label=\"jt\"];")
            }
        }

        bufferedWriter.newLine()
        bufferedWriter.write("}")
        bufferedWriter.close()

        dot2png("src/main/resources/Visualized_Graphs/","Execution_Graph_${graphID}")
    }

    /*
     When you make a deepCopy from a graph, the reference dependencies between events
        will be preserved within the ExecutionGraph object
     */
    fun deepCopy() : ExecutionGraph{
        val newExecutionGraph = ExecutionGraph(root = null,
            graphEvents = mutableListOf(),
            eventsOrder = mutableListOf(),
            COs = mutableListOf(),
            porf = mutableSetOf(),
            sc = mutableSetOf(),
            deleted = mutableListOf(),
            previous = mutableListOf(),
            JTs = mutableSetOf(),
            STs = mutableSetOf()
        )
        for (i in 0 until this.graphEvents.size){
            newExecutionGraph.graphEvents.add(this.graphEvents[i].deepCopy())
        }

        for (i in 0 until this.eventsOrder.size){
            newExecutionGraph.eventsOrder.add(
                newExecutionGraph.graphEvents.find { it.equals(this.eventsOrder[i]) }!!
            )
        }
        for (i in 0 until this.COs.size){
            if (this.COs[i].firstWrite is WriteEvent){
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

        for (i in this.STs.indices){
            newExecutionGraph.STs.add(
                Pair(newExecutionGraph.graphEvents.find { it.equals(this.STs.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.STs.elementAt(i).second) }!!)
            )
        }

        for (i in this.JTs.indices){
            newExecutionGraph.JTs.add(
                Pair(newExecutionGraph.graphEvents.find { it.equals(this.JTs.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.JTs.elementAt(i).second) }!!)
            )
        }

        for (i in this.porf.indices){
            newExecutionGraph.porf.add(
                Pair(newExecutionGraph.graphEvents.find { it.equals(this.porf.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.porf.elementAt(i).second) }!!)
            )
        }
        for (i in this.sc.indices){
            newExecutionGraph.sc.add(
                Pair(newExecutionGraph.graphEvents.find { it.equals(this.sc.elementAt(i).first) }!!,
                    newExecutionGraph.graphEvents.find { it.equals(this.sc.elementAt(i).second) }!!)
            )
        }
        for (i in 0 until this.deleted.size){
            newExecutionGraph.deleted.add(
                newExecutionGraph.graphEvents.find { it.equals(this.deleted[i]) }!!
            )
        }
        for (i in 0 until this.previous.size){
            newExecutionGraph.previous.add(
                newExecutionGraph.graphEvents.find { it.equals(this.previous[i]) }!!
            )
        }

        newExecutionGraph.root = RootNode(
            newExecutionGraph.graphEvents.find { it.equals(this.root?.value) }!!
        )

        for (i in this.root?.children?.keys!!){
            newExecutionGraph.root?.children!!.put(i,
                EventNode(newExecutionGraph.graphEvents.find { it.equals(this.root?.children!![i]!!.value) }!!,
                    null)
            )
            var node = this.root?.children!![i]!!
            var copyNode = newExecutionGraph.root?.children!![i]!!
            while (node.child != null){
                copyNode.child = EventNode(
                    newExecutionGraph.graphEvents.find { it.equals(node.child!!.value) }!!,
                    null)
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
    fun deepestCopy() : ExecutionGraph{
        val newExecutionGraph = ExecutionGraph(root = (this.root?.deepCopy()) as RootNode,
            graphEvents = mutableListOf(),
            eventsOrder = mutableListOf(),
            COs = mutableListOf(),
            porf = mutableSetOf(),
            sc = mutableSetOf(),
            deleted = mutableListOf(),
            previous = mutableListOf(),
            JTs = mutableSetOf(),
            STs = mutableSetOf()
        )
        for (i in 0 until this.graphEvents.size){
            newExecutionGraph.graphEvents.add(this.graphEvents[i].deepCopy())
        }
        for (i in 0 until this.eventsOrder.size){
            newExecutionGraph.eventsOrder.add(this.eventsOrder[i].deepCopy())
        }
        for (i in 0 until this.COs.size){
            newExecutionGraph.COs.add(this.COs[i].deepCopy())
        }
        for (i in this.STs.indices){
            newExecutionGraph.STs.add(Pair(this.STs.elementAt(i).first.deepCopy(),this.STs.elementAt(i).second.deepCopy()))
        }
        for (i in this.JTs.indices){
            newExecutionGraph.JTs.add(Pair(this.JTs.elementAt(i).first.deepCopy(),this.JTs.elementAt(i).second.deepCopy()))
        }
        for (i in this.porf.indices){
            newExecutionGraph.porf.add(Pair(this.porf.elementAt(i).first.deepCopy(),this.porf.elementAt(i).second.deepCopy()))
        }
        for (i in this.sc.indices){
            newExecutionGraph.sc.add(Pair(this.sc.elementAt(i).first.deepCopy(),this.sc.elementAt(i).second.deepCopy()))
        }
        for (i in 0 until this.deleted.size){
            newExecutionGraph.deleted.add(this.deleted[i].deepCopy())
        }
        for (i in 0 until this.previous.size){
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

    private fun dot2png(dotPath : String, dotName : String) {
        val processBuilder = ProcessBuilder("dot", "-Tpng", "-o", "${dotPath}/${dotName}.png", "${dotPath}/${dotName}.dot")
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        process.waitFor()
    }
}