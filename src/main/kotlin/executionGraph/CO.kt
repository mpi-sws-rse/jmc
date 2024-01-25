package executionGraph

import programStructure.Initialization
import programStructure.ReadsFrom
import programStructure.WriteEvent

data class CO(var firstWrite : ReadsFrom, val secondWrite: WriteEvent){

    fun deepCopy(): CO {
        val newCo = CO(firstWrite = this.firstWrite ,secondWrite = this.secondWrite.deepCopy() as WriteEvent)
        if (this.firstWrite is Initialization){
            newCo.firstWrite = Initialization().deepCopy() as ReadsFrom
        } else {
            newCo.firstWrite = (this.firstWrite as WriteEvent).deepCopy() as ReadsFrom
        }
        return newCo
    }
}
