package programStructure

import java.io.Serializable

/**
 * InitializationEvent event
 * @property type the type of the event
 * @property tid the thread id
 * @property serial the serial number of the event
 */
data class InitializationEvent(
    override val type: EventType = EventType.INITIAL,
    override val tid: Int = 0,
    override val serial: Int = 0
): ThreadEvent(), ReadsFrom, Serializable {
    override fun deepCopy(): Event {
        return InitializationEvent()
    }
}