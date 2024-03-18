package programStructure

import java.io.Serializable

/**
 * StartEvent is a subclass of [ThreadEvent] and represents the start of a thread.
 */
data class StartEvent(

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.START,

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property serial The serial number of the event.
     */
    override val serial: Int = 0,

    /**
     * @property callerThread The thread id of the thread that is starting the new thread.
     */
    val callerThread : Int,
): ThreadEvent() , Serializable {

    /**
     * Returns a deep copy of this object
     */
    override fun deepCopy(): Event {
        return StartEvent(
            type = EventType.START,
            tid = copy().tid,
            serial = copy().serial,
            callerThread = copy().callerThread
        )
    }
}