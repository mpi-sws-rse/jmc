package programStructure

import java.io.Serializable

/**
 * An abstract class representing a thread event.
 * @property tid The thread id of the thread that the event belongs to.
 * @property serial The serial number of the event.
 */
abstract class ThreadEvent: Event, Serializable {

    abstract val tid: Int
    abstract val serial: Int
}