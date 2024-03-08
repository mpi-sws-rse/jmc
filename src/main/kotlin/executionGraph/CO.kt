package executionGraph

import programStructure.InitializationEvent
import programStructure.ReadsFrom
import programStructure.WriteEvent
import java.io.Serializable

/**
 * This class represents the CO (Coherence Order) relation
 * between two write events.
 *
 * @property firstWrite The first event in the CO relation.
 * @property secondWrite The second event in the CO relation.
 */
data class CO(
    var firstWrite: ReadsFrom,
    val secondWrite: WriteEvent
): Serializable {

    fun deepCopy(): CO {
        val newCo = CO(
            firstWrite = this.firstWrite,
            secondWrite = this.secondWrite.deepCopy() as WriteEvent
        )

        if (this.firstWrite is InitializationEvent) {
            newCo.firstWrite = InitializationEvent().deepCopy() as ReadsFrom
        } else {
            newCo.firstWrite = (this.firstWrite as WriteEvent).deepCopy() as ReadsFrom
        }
        return newCo
    }
}