package programStructure

import java.io.Serializable

/**
 * WriteEvent class is a subclass of [ThreadEvent], and it represents a write event in the program.
 */
data class SendEvent(

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.SEND,

    /**
     * @property serial The serial number of the event.
     */
    override var serial: Int = 0,

    /**
     * @property value The value that is written.
     */
    var value: Message? = null,

    var receiverId: Long = 0,

    var tag: Long? = null,

    var fr: ReceiveEvent? = null

) : ThreadEvent(), ReceivesFrom, Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return A deep copy of this object
     */
    override fun deepCopy(): Event {
        return SendEvent(
            tid = copy().tid,
            type = EventType.SEND,
            serial = copy().serial,
            value = copy().value,
            receiverId = copy().receiverId,
            tag = copy().tag,
            fr = copy().fr
        )
    }
}