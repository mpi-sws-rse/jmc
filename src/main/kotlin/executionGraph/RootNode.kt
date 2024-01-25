package executionGraph

import programStructure.Event

data class RootNode(override var value: Event,
                    var children : MutableMap<Int,EventNode>? = mutableMapOf()
) : Node {
    override fun deepCopy(): Node {
        val newRootNode = RootNode(value = this.value.deepCopy(),
            children = mutableMapOf()
        )
        for (i in this.children!!.keys){
            newRootNode.children!![i] = (this.children!![i]!!.deepCopy()) as EventNode
        }
        return newRootNode
    }
}
