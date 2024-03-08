package programStructure

import java.io.Serializable

/**
 * JMCThread is a class that represents an abstract version of a thread
 * in the user's program. This abstraction helps to capture only the
 * necessary information for the model checker.
 * @property tid The thread id.
 * @property instructions The list of instructions in the thread.
 */
data class JMCThread(
    var tid: Int,
    val instructions: MutableList<Event> = mutableListOf()
): Serializable