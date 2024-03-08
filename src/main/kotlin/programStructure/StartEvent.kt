package programStructure

import java.io.Serializable

/**
 * StartEvent is a subclass of ThreadEvent and represents the start of a thread.
 * @property tid The thread id of the thread that is starting.
 * @property serial The serial number of the event.
 * @property callerThread The thread id of the thread that is starting the new thread.
 */
data class StartEvent(
    override val type: EventType = EventType.START,
    override val tid: Int,
    override val serial: Int = 0,
    val callerThread : Int,
): ThreadEvent() , Serializable {
    override fun deepCopy(): Event {
        return StartEvent(
            type = EventType.START,
            tid = copy().tid,
            serial = copy().serial,
            callerThread = copy().callerThread
        )
    }
}