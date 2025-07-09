package dpor

import programStructure.SymExecutionEvent
import programStructure.ThreadEvent

data class RevisitState(
    var popitems: ArrayList<ThreadEvent>? = null,
    var numOfPop: Int = 0,
    var deleted: ArrayList<HashSet<SymExecutionEvent>>? = null,
)