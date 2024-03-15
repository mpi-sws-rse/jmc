package programStructure

import java.io.Serializable

/**
 * JMCThread is a class that represents an abstract version of a thread in the user's program.
 * This abstraction helps to capture only the necessary information for the model checker.
 */
data class JMCThread(

    /**
     * @property tid The thread id.
     */
    var tid: Int,

    /**
     * @property instructions The list of [Event] in the thread.
     */
    val instructions: MutableList<Event> = mutableListOf()
): Serializable