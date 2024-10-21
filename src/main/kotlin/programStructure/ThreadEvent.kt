package programStructure

import java.io.Serializable

/**
 * An abstract class subclass of [Event] that represents a thread event in a program.
 */
abstract class ThreadEvent : Event, Serializable {

    /**
     * @property tid The thread id of the thread that the event belongs to.
     */
    abstract val tid: Int

    /**
     * @property serial The serial number of the event.
     */
    abstract val serial: Int

    override fun equals(other: Any?): Boolean {
        if (other !is ThreadEvent) return false
        return this.tid == other.tid && this.serial == other.serial && this.type == other.type
    }
}