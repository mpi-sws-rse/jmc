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

    override fun toString(): String {
        return "$type{${tid - 1}, $serial}"
    }

    fun compareTo(otherThread: ThreadEvent): Int {
        if (tid == null && otherThread.tid == null) {
            return 0
        }
        if (tid == null) {
            return -1
        }
        if (otherThread.tid == null) {
            return 1
        }
        val cmp = tid.compareTo(otherThread.tid)
        if (cmp != 0) {
            return cmp
        }
        if (serial == null && otherThread.serial == null) {
            return 0
        }
        if (serial == null) {
            return -1
        }
        if (otherThread.serial == null) {
            return 1
        }
        return serial.compareTo(otherThread.serial)
    }
}