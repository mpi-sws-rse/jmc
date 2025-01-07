package dpor

import programStructure.ThreadEvent

data class RevisitState(
    var popitems: ArrayList<ThreadEvent>? = null,
    var numOfPop: Int = 0,
)